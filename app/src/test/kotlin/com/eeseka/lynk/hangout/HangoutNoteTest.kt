package com.eeseka.lynk.hangout

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.HangoutNoteId
import com.eeseka.lynk.hangout.api.dto.HangoutNoteCountDto
import com.eeseka.lynk.hangout.api.dto.HangoutNoteDto
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutNoteRepository
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.notification.domain.model.NotificationType
import com.eeseka.lynk.notification.service.PushNotificationService
import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.TestAccount
import com.eeseka.lynk.support.authenticatedAs
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import tools.jackson.core.type.TypeReference
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Notes open with I'm Here, an hour before the start, and close when the hangout ends. Everyone going
 * can read them; only their author can change them, and the author or the host can delete them.
 */
class HangoutNoteTest : IntegrationTest() {

    @Autowired
    private lateinit var hangoutService: HangoutService

    @Autowired
    private lateinit var hangoutNoteRepository: HangoutNoteRepository

    @MockitoSpyBean
    private lateinit var pushNotificationService: PushNotificationService

    @Test
    fun `a note within the hour reaches everyone else going`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val cara = signIn("cara")
        val hangoutId = groupHangout(host, listOf(bola, cara), startsIn = Duration.ofMinutes(30))

        addNote(bola, hangoutId, "Running late", audience = "EVERYONE")
            .andExpect { status { isCreated() } }
        broker.deliverEvents()

        verify(pushNotificationService).sendToUsers(
            eq(setOf(host.userId, cara.userId)),
            any(),
            eq("Bola: Running late"),
            eq(hangoutId),
            eq(NotificationType.NOTE_ADDED)
        )
        assertEquals(listOf("Running late"), getNotes(cara, hangoutId).map { it.content })
    }

    @Test
    fun `a note for the host only tells the host`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val cara = signIn("cara")
        val hangoutId = groupHangout(host, listOf(bola, cara), startsIn = Duration.ofMinutes(30))

        addNote(bola, hangoutId, "Parking is full", audience = "HOST")
            .andExpect { status { isCreated() } }
        broker.deliverEvents()

        verify(pushNotificationService).sendToUsers(
            eq(setOf(host.userId)),
            any(),
            eq("Bola: Parking is full"),
            eq(hangoutId),
            eq(NotificationType.NOTE_ADDED)
        )
        assertEquals(1, getNotes(cara, hangoutId).size)
    }

    @Test
    fun `the host's notes always go to everyone`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = groupHangout(host, listOf(bola), startsIn = Duration.ofMinutes(30))

        addNote(host, hangoutId, "Gate code is 4471", audience = "HOST")
            .andExpect { status { isCreated() } }
        broker.deliverEvents()

        verify(pushNotificationService).sendToUsers(
            eq(setOf(bola.userId)),
            any(),
            eq("Ada: Gate code is 4471"),
            eq(hangoutId),
            eq(NotificationType.NOTE_ADDED)
        )
    }

    @Test
    fun `cannot leave a note before the hour opens`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = groupHangout(host, listOf(bola), startsIn = Duration.ofHours(3))

        addNote(bola, hangoutId, "Early", audience = "EVERYONE")
            .andExpect { status { isConflict() } }
        broker.deliverEvents()

        verify(pushNotificationService, never()).sendToUsers(any(), any(), any(), any(), eq(NotificationType.NOTE_ADDED))
        assertEquals(0, hangoutNoteRepository.count())
    }

    @Test
    fun `cannot leave a note on a hangout only its host is going to`() {
        val host = signIn("ada")
        val hangoutId = groupHangout(host, emptyList(), startsIn = Duration.ofMinutes(30))

        addNote(host, hangoutId, "Anyone?", audience = "EVERYONE")
            .andExpect { status { isConflict() } }
    }

    @Test
    fun `refuses a blank note`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = groupHangout(host, listOf(bola), startsIn = Duration.ofMinutes(30))

        addNote(bola, hangoutId, "   ", audience = "EVERYONE")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `only people going can read the notes`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val invitee = signIn("dayo")
        val stranger = signIn("eze")
        val hangoutId = groupHangout(host, listOf(bola), startsIn = Duration.ofMinutes(30))
        hangouts.invite(host, hangoutId, invitee)

        mockMvc.get("/api/hangouts/$hangoutId/notes") { authenticatedAs(invitee) }
            .andExpect { status { isForbidden() } }
        mockMvc.get("/api/hangouts/$hangoutId/notes") { authenticatedAs(stranger) }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `only the author can edit a note and editing tells nobody`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = groupHangout(host, listOf(bola), startsIn = Duration.ofMinutes(30))
        val noteId = postedNote(bola, hangoutId, "Running late")

        editNote(host, hangoutId, noteId, "Not yours").andExpect { status { isForbidden() } }
        editNote(bola, hangoutId, noteId, "Running 10 minutes late").andExpect { status { isNoContent() } }
        broker.deliverEvents()

        val note = getNotes(host, hangoutId).single()
        assertEquals("Running 10 minutes late", note.content)
        assertNotNull(note.updatedAt)
        verify(pushNotificationService, never()).sendToUsers(any(), any(), eq("Bola: Running 10 minutes late"), any(), any())
    }

    @Test
    fun `after the hangout ends notes stay readable and only deleting still works`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = groupHangout(host, listOf(bola), startsIn = Duration.ofMinutes(30))
        val noteId = postedNote(bola, hangoutId, "We're at the back")
        complete(host, hangoutId)

        addNote(bola, hangoutId, "Too late", audience = "EVERYONE").andExpect { status { isConflict() } }
        editNote(bola, hangoutId, noteId, "Changed").andExpect { status { isConflict() } }
        assertEquals(listOf("We're at the back"), getNotes(host, hangoutId).map { it.content })

        deleteNote(bola, hangoutId, noteId).andExpect { status { isNoContent() } }
        assertEquals(0, getNotes(host, hangoutId).size)
    }

    @Test
    fun `the host can delete anyone's note but other guests cannot`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val chidi = signIn("chidi")
        val hangoutId = groupHangout(host, listOf(bola, chidi), startsIn = Duration.ofMinutes(30))
        val noteId = postedNote(bola, hangoutId, "Bring cash")

        deleteNote(chidi, hangoutId, noteId).andExpect { status { isForbidden() } }
        deleteNote(host, hangoutId, noteId).andExpect { status { isNoContent() } }
        deleteNote(bola, hangoutId, noteId).andExpect { status { isNotFound() } }
        assertEquals(0, getNotes(host, hangoutId).size)
    }

    @Test
    fun `pages the notes newest first and counts them all`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = groupHangout(host, listOf(bola), startsIn = Duration.ofMinutes(30))
        postedNote(bola, hangoutId, "First")
        postedNote(host, hangoutId, "Second")
        postedNote(bola, hangoutId, "Third")

        val firstPage = getNotes(host, hangoutId, pageSize = 2)
        val secondPage = getNotes(host, hangoutId, pageSize = 2, before = firstPage.last().createdAt)

        assertEquals(listOf("Third", "Second"), firstPage.map { it.content })
        assertEquals(listOf("First"), secondPage.map { it.content })
        assertEquals(3L, getNoteCount(bola, hangoutId))
    }

    @Test
    fun `only people going can count the notes`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val invitee = signIn("dayo")
        val hangoutId = groupHangout(host, listOf(bola), startsIn = Duration.ofMinutes(30))
        hangouts.invite(host, hangoutId, invitee)

        mockMvc.get("/api/hangouts/$hangoutId/notes/count") { authenticatedAs(invitee) }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `takes a person's notes with them when they delete their account`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = groupHangout(host, listOf(bola), startsIn = Duration.ofMinutes(30))
        val bolasNote = postedNote(bola, hangoutId, "Running late")
        val hostsNote = postedNote(host, hangoutId, "No worries")
        complete(host, hangoutId)

        mockMvc.delete("/api/auth/account") { authenticatedAs(bola) }
            .andExpect { status { isOk() } }
        broker.deliverEvents()

        assertEquals(false, hangoutNoteRepository.existsById(bolasNote))
        assertEquals(true, hangoutNoteRepository.existsById(hostsNote))
    }

    private fun groupHangout(host: TestAccount, attendees: List<TestAccount>, startsIn: Duration): HangoutId {
        val hangoutId = hangouts.scheduled(host).id
        attendees.forEach { attendee ->
            hangouts.invite(host, hangoutId, attendee)
            hangouts.accept(attendee, hangoutId)
        }
        fixtures.moveScheduledAt(hangoutId, Instant.now().plus(startsIn))
        return hangoutId
    }

    private fun complete(host: TestAccount, hangoutId: HangoutId) {
        fixtures.moveScheduledAt(hangoutId, Instant.now().minus(Duration.ofHours(1)))
        hangoutService.transitionDueHangoutsToOngoing()
        mockMvc.patch("/api/hangouts/$hangoutId/complete") { authenticatedAs(host) }
            .andExpect { status { is2xxSuccessful() } }
    }

    private fun postedNote(account: TestAccount, hangoutId: HangoutId, content: String): HangoutNoteId {
        val response = addNote(account, hangoutId, content, audience = "EVERYONE")
            .andExpect { status { isCreated() } }
            .andReturn().response.contentAsString
        return objectMapper.readValue(response, HangoutNoteDto::class.java).id
    }

    private fun addNote(account: TestAccount, hangoutId: HangoutId, content: String, audience: String): ResultActionsDsl =
        mockMvc.post("/api/hangouts/$hangoutId/notes") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(account)
            this.content = objectMapper.writeValueAsString(mapOf("content" to content, "audience" to audience))
        }

    private fun editNote(account: TestAccount, hangoutId: HangoutId, noteId: HangoutNoteId, content: String): ResultActionsDsl =
        mockMvc.patch("/api/hangouts/$hangoutId/notes/$noteId") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(account)
            this.content = objectMapper.writeValueAsString(mapOf("content" to content))
        }

    private fun deleteNote(account: TestAccount, hangoutId: HangoutId, noteId: HangoutNoteId): ResultActionsDsl =
        mockMvc.delete("/api/hangouts/$hangoutId/notes/$noteId") { authenticatedAs(account) }

    private fun getNotes(
        account: TestAccount,
        hangoutId: HangoutId,
        pageSize: Int? = null,
        before: Instant? = null
    ): List<HangoutNoteDto> {
        val response = mockMvc.get("/api/hangouts/$hangoutId/notes") {
            authenticatedAs(account)
            pageSize?.let { param("pageSize", it.toString()) }
            before?.let { param("before", it.toString()) }
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        return objectMapper.readValue(response, object : TypeReference<List<HangoutNoteDto>>() {})
    }

    private fun getNoteCount(account: TestAccount, hangoutId: HangoutId): Long {
        val response = mockMvc.get("/api/hangouts/$hangoutId/notes/count") {
            authenticatedAs(account)
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        return objectMapper.readValue(response, HangoutNoteCountDto::class.java).count
    }

    private fun signIn(name: String): TestAccount =
        accounts.signIn(email = "$name@lynk.test", displayName = name.replaceFirstChar { it.uppercase() }, username = name)
}
