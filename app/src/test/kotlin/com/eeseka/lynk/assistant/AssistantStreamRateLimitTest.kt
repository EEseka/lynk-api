package com.eeseka.lynk.assistant

import ai.koog.prompt.streaming.StreamFrame
import com.eeseka.lynk.common.infra.rate_limiting.UserRateLimiter
import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.authenticatedAs
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertNull

@TestPropertySource(properties = ["lynk.rate-limit.user.apply-limit=true"])
class AssistantStreamRateLimitTest : IntegrationTest() {

    private companion object {
        const val ANSWER_WAIT_MS = 10_000L
    }

    @MockitoSpyBean
    private lateinit var userRateLimiter: UserRateLimiter

    @Test
    fun `a finished answer closes cleanly and counts once`() {
        // Tests run without Redis, so the count itself always lets the question through
        doAnswer { (it.arguments.last() as () -> Any?).invoke() }
            .whenever(userRateLimiter).withUserRateLimit<Any?>(any(), any(), any(), any(), any(), any())

        val ada = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")
        val hangoutId = hangouts.scheduled(ada, spotId = null).id
        model.script(flowOf(StreamFrame.TextDelta("Hi."), StreamFrame.TextComplete("Hi."), StreamFrame.End(finishReason = "end_turn")))

        val result = mockMvc.post("/api/assistant/messages") {
            contentType = MediaType.APPLICATION_JSON
            accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
            authenticatedAs(ada)
            content = """{"hangoutId":"$hangoutId","content":"Hi","timeZone":"Africa/Lagos"}"""
        }.andExpect { request { asyncStarted() } }.andReturn()
        result.getAsyncResult(ANSWER_WAIT_MS)

        // The stream already sent its 200, so a failure here shows only as an exception, never as a status
        val dispatch = mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk).andReturn()
        assertNull(dispatch.resolvedException)
        verify(userRateLimiter, times(1)).withUserRateLimit<Any?>(any(), any(), any(), any(), any(), any())
    }
}
