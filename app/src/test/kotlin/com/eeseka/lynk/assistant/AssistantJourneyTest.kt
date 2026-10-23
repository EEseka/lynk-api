package com.eeseka.lynk.assistant

import ai.koog.prompt.streaming.StreamFrame
import com.eeseka.lynk.assistant.api.controllers.AssistantController
import com.eeseka.lynk.assistant.domain.model.AssistantFailureReason
import com.eeseka.lynk.assistant.domain.model.AssistantMessageRole
import com.eeseka.lynk.assistant.domain.model.AssistantMessageStatus
import com.eeseka.lynk.assistant.domain.model.DiscoverChat
import com.eeseka.lynk.assistant.domain.model.DiscoverChatSpot
import com.eeseka.lynk.assistant.infra.database.repositories.AssistantMessageRepository
import com.eeseka.lynk.assistant.infra.database.repositories.AssistantSpotRepository
import com.eeseka.lynk.assistant.infra.messaging.AssistantUserEventListener
import com.eeseka.lynk.assistant.infra.redis.DiscoverChatRepository
import com.eeseka.lynk.assistant.service.AssistantMessageService
import com.eeseka.lynk.assistant.service.AssistantService
import com.eeseka.lynk.common.domain.events.user.UserEvent
import com.eeseka.lynk.common.domain.type.DiscoverChatId
import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.infra.rate_limiting.UserRateLimiter
import com.eeseka.lynk.spot.domain.model.PriceLevel
import com.eeseka.lynk.spot.domain.model.Spot
import com.eeseka.lynk.spot.domain.model.SpotCategory
import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.TestAccount
import com.eeseka.lynk.support.authenticatedAs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The model is scripted: each test says what it streams back, call by call, the way Anthropic would.
 * What is checked is ours: who may ask, one answer per chat, what gets saved, and that a message
 * that bought nothing is given back.
 */
class AssistantJourneyTest : IntegrationTest() {

    private companion object {
        const val SPOT_ID = "ChIJterrakulture"
        const val LATITUDE = 6.4281
        const val LONGITUDE = 3.4219
        const val ANSWER_WAIT_MS = 10_000L
    }

    @Autowired
    private lateinit var assistantMessageRepository: AssistantMessageRepository

    @Autowired
    private lateinit var assistantSpotRepository: AssistantSpotRepository

    @Autowired
    private lateinit var assistantMessageService: AssistantMessageService

    @Autowired
    private lateinit var assistantService: AssistantService

    @Autowired
    private lateinit var assistantUserEventListener: AssistantUserEventListener

    // Tests run without Redis, so Discover's chats are held here instead
    @MockitoBean
    private lateinit var discoverChatRepository: DiscoverChatRepository

    // Watched rather than replaced, so a refund can be seen without a live Redis behind it
    @MockitoSpyBean
    private lateinit var userRateLimiter: UserRateLimiter

    private lateinit var ada: TestAccount

    @BeforeEach
    fun signInAndStubGoogle() {
        ada = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")

        given(googlePlacesClient.searchSpots(anyOrNull(), anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
            .willReturn(listOf(spot(SPOT_ID, "Terra Kulture")) to null)
        given(googlePlacesClient.getSpotById(eq(SPOT_ID))).willReturn(spot(SPOT_ID, "Terra Kulture"))
    }

    @Test
    fun `the refund route is the one the rate limiter counts`() {
        val interceptorRoute = "${AssistantController::class.simpleName}.${AssistantController::sendMessage.name}"

        assertEquals(AssistantService.SEND_MESSAGE_ROUTE, interceptorRoute)
    }

    @Test
    fun `answers in a hangout, shows the card it chose and keeps both messages`() {
        val hangoutId = votingHangout(ada)
        scriptModel(
            toolCall("search_spots", """{"query":"quiet restaurant"}"""),
            toolCall("recommend_spot", """{"spotId":"$SPOT_ID","reason":"Calm, and good for groups."}"""),
            text("Terra Kulture fits.")
        )

        val stream = awaitAnswer(askInHangout(ada, hangoutId, "Somewhere quiet?"))

        assertTrue("event:TEXT" in stream)
        assertTrue("event:SPOTS" in stream && "Calm, and good for groups." in stream)
        assertTrue("event:DONE" in stream)

        val messages = assistantMessageRepository.findAll()
        val answer = messages.first { it.role == AssistantMessageRole.ASSISTANT }
        assertEquals(setOf(AssistantMessageRole.USER, AssistantMessageRole.ASSISTANT), messages.map { it.role }.toSet())
        assertEquals("Somewhere quiet?", messages.first { it.role == AssistantMessageRole.USER }.content)
        assertEquals(AssistantMessageStatus.ANSWERED, answer.status)
        assertEquals("Terra Kulture fits.", answer.content)
        assertEquals(1, answer.searchCount)
        assertEquals(listOf(SPOT_ID), assistantSpotRepository.findAll().map { it.googlePlaceId })
    }

    @Test
    fun `keeps the words written beside the cards and drops the ones written before a search`() {
        val hangoutId = votingHangout(ada)
        scriptModel(
            textThenToolCall("Let me look around for you.", "search_spots", """{"query":"quiet restaurant"}"""),
            textThenToolCall(
                "Terra Kulture is calm and good for groups.",
                "recommend_spot",
                """{"spotId":"$SPOT_ID","reason":"Calm, and good for groups."}"""
            )
        )

        awaitAnswer(askInHangout(ada, hangoutId, "Somewhere quiet?"))

        val answer = assistantMessageRepository.findAll().first { it.role == AssistantMessageRole.ASSISTANT }
        assertEquals("Terra Kulture is calm and good for groups.", answer.content)
        assertEquals(2, model.prompts.size)
    }

    @Test
    fun `lets the model write again when a card could not be added`() {
        val hangoutId = votingHangout(ada)
        scriptModel(
            textThenToolCall("Try Nowhere Bar.", "recommend_spot", """{"spotId":"ChIJmadeup","reason":"Trust me."}"""),
            text("I could not find a place yet.")
        )

        awaitAnswer(askInHangout(ada, hangoutId, "Anywhere?"))

        val answer = assistantMessageRepository.findAll().first { it.role == AssistantMessageRole.ASSISTANT }
        assertEquals("I could not find a place yet.", answer.content)
    }

    @Test
    fun `reminds the model of earlier cards by name`() {
        val hangoutId = votingHangout(ada)
        scriptModel(
            toolCall("search_spots", """{"query":"quiet restaurant"}"""),
            toolCall("recommend_spot", """{"spotId":"$SPOT_ID","reason":"Calm, and good for groups."}"""),
            text("Terra Kulture fits.")
        )
        awaitAnswer(askInHangout(ada, hangoutId, "Somewhere quiet?"))

        scriptModel(text("It is open then."))
        awaitAnswer(askInHangout(ada, hangoutId, "Is the first one open?"))

        val history = model.prompts.last().messages.joinToString("\n") { it.textContent() }
        assertTrue("Terra Kulture [spotId $SPOT_ID]" in history)
    }

    @Test
    fun `never shows a card for a place no search returned`() {
        val hangoutId = votingHangout(ada)
        scriptModel(
            toolCall("recommend_spot", """{"spotId":"ChIJmadeup","reason":"Trust me."}"""),
            text("Here you go.")
        )

        val stream = awaitAnswer(askInHangout(ada, hangoutId, "Anywhere?"))

        assertFalse("event:SPOTS" in stream)
        assertTrue(assistantSpotRepository.findAll().isEmpty())
    }

    @Test
    fun `sends a reason too long for the card back instead of cutting it`() {
        val hangoutId = votingHangout(ada)
        scriptModel(
            toolCall("search_spots", """{"query":"quiet restaurant"}"""),
            toolCall("recommend_spot", """{"spotId":"$SPOT_ID","reason":"${"Calm. ".repeat(60)}"}"""),
            toolCall("recommend_spot", """{"spotId":"$SPOT_ID","reason":"Calm, and good for groups."}"""),
            text("Terra Kulture fits.")
        )

        awaitAnswer(askInHangout(ada, hangoutId, "Somewhere quiet?"))

        assertEquals(listOf("Calm, and good for groups."), assistantSpotRepository.findAll().map { it.reason })
    }

    @Test
    fun `turns away a second question while the first is still being answered`() {
        val hangoutId = votingHangout(ada)
        val firstAnswerMayFinish = CompletableDeferred<Unit>()
        model.script(
            flow {
                firstAnswerMayFinish.await()
                text("Done.").forEach { emit(it) }
            }
        )

        val first = askInHangout(ada, hangoutId, "First?").andReturn()
        postQuestion(ada, """{"hangoutId":"$hangoutId","content":"Second?"}""")
            .andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("ANSWER_IN_PROGRESS") }
            }

        firstAnswerMayFinish.complete(Unit)
        first.getAsyncResult(ANSWER_WAIT_MS)

        // The turned-away question cost nothing and left nothing behind
        verify(userRateLimiter).refund(ada.userId, AssistantService.SEND_MESSAGE_ROUTE)
        assertEquals(2, assistantMessageRepository.count())
    }

    @Test
    fun `gives the message back when the answer fails`() {
        val hangoutId = votingHangout(ada)
        model.script(flow { throw IllegalStateException("Anthropic is down") })

        val stream = awaitAnswer(askInHangout(ada, hangoutId, "Anywhere?"))

        assertTrue("event:ERROR" in stream && AssistantFailureReason.ERROR.name in stream)
        val answer = assistantMessageRepository.findAll().first { it.role == AssistantMessageRole.ASSISTANT }
        assertEquals(AssistantMessageStatus.FAILED, answer.status)
        verify(userRateLimiter).refund(ada.userId, AssistantService.SEND_MESSAGE_ROUTE)
    }

    @Test
    fun `counts a refusal as a failed answer`() {
        val hangoutId = votingHangout(ada)
        scriptModel(listOf(StreamFrame.End(finishReason = "refusal")))

        val stream = awaitAnswer(askInHangout(ada, hangoutId, "Anywhere?"))

        assertTrue(AssistantFailureReason.REFUSED.name in stream)
        val answer = assistantMessageRepository.findAll().first { it.role == AssistantMessageRole.ASSISTANT }
        assertEquals(AssistantFailureReason.REFUSED, answer.failureReason)
    }

    @Test
    fun `only someone going can ask, and only while voting is open`() {
        val bola = accounts.signIn(email = "bola@lynk.test", displayName = "Bola", username = "bola")
        val votingHangoutId = votingHangout(ada)
        val scheduledHangoutId = hangouts.scheduled(ada).id

        postQuestion(bola, """{"hangoutId":"$votingHangoutId","content":"Anywhere?"}""")
            .andExpect { status { isForbidden() }; jsonPath("$.code") { value("NOT_ATTENDING") } }
        postQuestion(ada, """{"hangoutId":"$scheduledHangoutId","content":"Anywhere?"}""")
            .andExpect { status { isConflict() }; jsonPath("$.code") { value("VOTING_CLOSED") } }

        verify(userRateLimiter).refund(bola.userId, AssistantService.SEND_MESSAGE_ROUTE)
        verify(userRateLimiter).refund(ada.userId, AssistantService.SEND_MESSAGE_ROUTE)
        assertEquals(0, assistantMessageRepository.count())
    }

    @Test
    fun `shows the chat newest first, cards from the snapshot kept with them`() {
        val hangoutId = votingHangout(ada)
        scriptModel(
            toolCall("search_spots", """{"query":"restaurant"}"""),
            toolCall("recommend_spot", """{"spotId":"$SPOT_ID","reason":"Good jollof."}"""),
            text("Try this.")
        )
        awaitAnswer(askInHangout(ada, hangoutId, "Food?"))

        mockMvc.get("/api/assistant/hangouts/$hangoutId/messages") {
            authenticatedAs(ada)
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].role") { value("ASSISTANT") }
            jsonPath("$[0].recommendations[0].spot.name") { value("Terra Kulture") }
            jsonPath("$[0].recommendations[0].reason") { value("Good jollof.") }
            jsonPath("$[1].role") { value("USER") }
        }

        // An old chat opens without a details call per card
        verify(googlePlacesClient, never()).getSpotById(any())
    }

    @Test
    fun `counts the messages in a hangout chat`() {
        val hangoutId = votingHangout(ada)

        mockMvc.get("/api/assistant/hangouts/$hangoutId/messages/count") { authenticatedAs(ada) }
            .andExpect { jsonPath("$.count") { value(0) } }

        scriptModel(text("Try this."))
        awaitAnswer(askInHangout(ada, hangoutId, "Food?"))

        mockMvc.get("/api/assistant/hangouts/$hangoutId/messages/count") { authenticatedAs(ada) }
            .andExpect {
                status { isOk() }
                jsonPath("$.count") { value(2) }
            }
    }

    @Test
    fun `clears a chat, but not while an answer is being written`() {
        val hangoutId = votingHangout(ada)
        assistantMessageService.startAnswer(hangoutId, ada.userId, "Still going?")

        mockMvc.delete("/api/assistant/hangouts/$hangoutId/messages") { authenticatedAs(ada) }
            .andExpect { status { isConflict() } }

        assistantMessageService.failAnswersPendingSince(Instant.now().plusSeconds(1))
        mockMvc.delete("/api/assistant/hangouts/$hangoutId/messages") { authenticatedAs(ada) }
            .andExpect { status { isNoContent() } }

        assertEquals(0, assistantMessageRepository.count())
    }

    @Test
    fun `fails and refunds an answer a restart left unfinished`() {
        val hangoutId = votingHangout(ada)
        val messageId = assistantMessageService.startAnswer(hangoutId, ada.userId, "Anyone there?")
        fixtures.ageAssistantMessage(messageId, createdAt = Instant.now().minus(5, ChronoUnit.MINUTES))

        assistantService.failStuckAnswers()

        assertEquals(AssistantMessageStatus.FAILED, assistantMessageRepository.findById(messageId).get().status)
        verify(userRateLimiter).refund(ada.userId, AssistantService.SEND_MESSAGE_ROUTE)
    }

    @Test
    fun `deleting an account deletes its chats`() {
        val hangoutId = votingHangout(ada)
        assistantMessageService.startAnswer(hangoutId, ada.userId, "Anywhere?")

        assistantUserEventListener.handleUserEvent(
            UserEvent.Deleted(userId = ada.userId, email = "ada@lynk.test", displayName = "Ada")
        )

        assertEquals(0, assistantMessageRepository.count())
    }

    @Test
    fun `a Discover question needs the area being looked at`() {
        postQuestion(ada, """{"content":"Anywhere?"}""")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a Discover chat that expired asks for a new one`() {
        val expiredChatId = UUID.randomUUID()
        given(discoverChatRepository.tryStartAnswering(ada.userId)).willReturn(true)
        given(discoverChatRepository.find(ada.userId, expiredChatId)).willReturn(null)

        postQuestion(ada, discoverQuestion("Anywhere?", chatId = expiredChatId))
            .andExpect { status { isNotFound() }; jsonPath("$.code") { value("CHAT_EXPIRED") } }

        // The chat is read under the lock, so a refused question still lets the next one in
        verify(discoverChatRepository).finishAnswering(ada.userId)
    }

    @Test
    fun `keeps a Discover chat in Redis and never in the database`() {
        given(discoverChatRepository.tryStartAnswering(ada.userId)).willReturn(true)
        scriptModel(
            toolCall("search_spots", """{"query":"cafe"}"""),
            toolCall("recommend_spot", """{"spotId":"$SPOT_ID","reason":"Quiet in the morning."}"""),
            text("This one.")
        )

        val stream = awaitAnswer(postQuestion(ada, discoverQuestion("A cafe?")))

        assertTrue("discoverChatId" in stream && "event:DONE" in stream)
        val saved = argumentCaptor<DiscoverChat>()
        verify(discoverChatRepository).save(eq(ada.userId), saved.capture())
        assertEquals(listOf("A cafe?", "This one."), saved.firstValue.messages.map { it.content })
        assertEquals(listOf(DiscoverChatSpot(id = SPOT_ID, name = "Terra Kulture")), saved.firstValue.messages.last().spots)
        verify(discoverChatRepository).finishAnswering(ada.userId)
        assertEquals(0, assistantMessageRepository.count())
    }

    @Test
    fun `turns away a second Discover question while the first is being answered`() {
        given(discoverChatRepository.tryStartAnswering(ada.userId)).willReturn(false)

        postQuestion(ada, discoverQuestion("A cafe?"))
            .andExpect { status { isConflict() }; jsonPath("$.code") { value("ANSWER_IN_PROGRESS") } }

        verify(discoverChatRepository, never()).save(any(), any())
    }

    @Test
    fun `reads a Discover chat only once it holds the answering lock`() {
        given(discoverChatRepository.tryStartAnswering(ada.userId)).willReturn(false)

        postQuestion(ada, discoverQuestion("And for dinner?", chatId = UUID.randomUUID()))
            .andExpect { status { isConflict() }; jsonPath("$.code") { value("ANSWER_IN_PROGRESS") } }

        // Read before the lock, a chat could be saved over by the answer still being written
        verify(discoverChatRepository, never()).find(any(), any())
    }

    private fun votingHangout(host: TestAccount): HangoutId = hangouts.scheduled(host, spotId = null).id

    private fun askInHangout(account: TestAccount, hangoutId: HangoutId, question: String): ResultActionsDsl =
        postQuestion(account, """{"hangoutId":"$hangoutId","content":"$question","timeZone":"Africa/Lagos"}""")

    private fun discoverQuestion(question: String, chatId: DiscoverChatId? = null): String {
        val chat = chatId?.let { ""","discoverChatId":"$it"""" } ?: ""
        return """{"content":"$question","latitude":$LATITUDE,"longitude":$LONGITUDE$chat}"""
    }

    // Asks for the stream and for JSON, so an error before the answer starts still arrives as a normal body
    private fun postQuestion(account: TestAccount, body: String): ResultActionsDsl =
        mockMvc.post("/api/assistant/messages") {
            contentType = MediaType.APPLICATION_JSON
            accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
            authenticatedAs(account)
            content = body
        }

    private fun awaitAnswer(request: ResultActionsDsl): String {
        val result: MvcResult = request.andExpect { request { asyncStarted() } }.andReturn()
        result.getAsyncResult(ANSWER_WAIT_MS)
        return result.response.contentAsString
    }

    // One list of frames per call to the model, in the order the calls happen
    private fun scriptModel(vararg calls: List<StreamFrame>) {
        model.script(*calls.map { flowOf(*it.toTypedArray()) }.toTypedArray())
    }

    private fun toolCall(name: String, args: String): List<StreamFrame> = listOf(
        StreamFrame.ToolCallComplete(id = "call_${UUID.randomUUID()}", name = name, content = args),
        StreamFrame.End(finishReason = "tool_use")
    )

    private fun textThenToolCall(words: String, name: String, args: String): List<StreamFrame> = listOf(
        StreamFrame.TextDelta(words),
        StreamFrame.TextComplete(words),
        StreamFrame.ToolCallComplete(id = "call_${UUID.randomUUID()}", name = name, content = args),
        StreamFrame.End(finishReason = "tool_use")
    )

    private fun text(words: String): List<StreamFrame> = listOf(
        StreamFrame.TextDelta(words),
        StreamFrame.TextComplete(words),
        StreamFrame.End(finishReason = "end_turn")
    )

    private fun spot(id: String, name: String) = Spot(
        id = id,
        name = name,
        typeLabel = null,
        description = "Somewhere to eat",
        generativeSummary = null,
        reviewSummary = null,
        photoUrls = listOf("https://places.test/$id.jpg"),
        category = SpotCategory.RESTAURANT,
        priceLevel = PriceLevel.MODERATE,
        priceRange = null,
        rating = 4.5,
        reviewCount = 120,
        businessStatus = null,
        openingHours = null,
        amenities = null,
        parking = null,
        payment = null,
        shortAddress = "Victoria Island, Lagos",
        latitude = LATITUDE,
        longitude = LONGITUDE,
        phoneNumber = null,
        websiteUrl = null,
        googleMapsUrl = null,
        directionsUrl = null,
        isSaved = false
    )
}
