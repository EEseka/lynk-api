package com.eeseka.lynk.hangout

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.hangout.api.dto.HangoutDto
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.TestAccount
import com.eeseka.lynk.support.authenticatedAs
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Several people changing the same hangout at the same moment.
 *
 * Every other test sends one request and waits for it. These send many together because the bugs they
 * guard against only exist while the requests are in flight.
 */
class HangoutConcurrencyTest : IntegrationTest() {

    private companion object {
        // Two requests rarely overlap closely enough to collide; eight reliably do.
        const val RACING_INVITEES = 8
    }

    @Test
    fun `lets only one of several invitees take the last share of a bill`() {
        val host = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")
        val bola = accounts.signIn(email = "bola@lynk.test", displayName = "Bola", username = "bola")
        val racers = (1..RACING_INVITEES).map {
            accounts.signIn(email = "invitee$it@lynk.test", displayName = "Invitee $it", username = "invitee$it")
        }

        // Invited before payments go on, so every racer still holds an invite once the bill is split two ways.
        val hangoutId = hangouts.scheduled(host).id
        hangouts.invite(host, hangoutId, bola)
        hangouts.accept(bola, hangoutId)
        racers.forEach { hangouts.invite(host, hangoutId, it) }
        hangouts.enablePayments(host, hangoutId, totalCostKobo = 1_000_000L, deadline = inTwoDays())

        // Bola drops out, which leaves exactly one share.
        mockMvc.delete("/api/hangouts/$hangoutId/leave") {
            authenticatedAs(bola)
        }.andExpect { status { isNoContent() } }

        val results = acceptTogether(hangoutId, racers)

        val statuses = results.map { it.response.status }
        assertEquals(1, statuses.count { it == 200 }, "expected exactly one invitee to get in: $statuses")

        results.filter { it.response.status != 200 }.forEach { refused ->
            assertTrue(
                refused.response.contentAsString.contains("HANGOUT_ILLEGAL_STATE"),
                "an invitee was refused for the wrong reason: ${refused.response.contentAsString}"
            )
        }

        val hangout = getHangout(host, hangoutId)
        val attending = hangout.participants.count { it.rsvpStatus == RsvpStatus.ATTENDING }
        assertEquals(2, attending, "more people are attending than the bill was split between")
        assertEquals(2, hangout.participantCount)
    }

    /** Every request waits at the same barrier, so they all start together. */
    private fun acceptTogether(hangoutId: HangoutId, invitees: List<TestAccount>): List<MvcResult> {
        val barrier = CyclicBarrier(invitees.size)
        val executor = Executors.newFixedThreadPool(invitees.size)

        try {
            val futures = invitees.map { invitee ->
                executor.submit<MvcResult> {
                    barrier.await()
                    accept(invitee, hangoutId)
                }
            }
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun accept(account: TestAccount, hangoutId: HangoutId): MvcResult =
        mockMvc.patch("/api/hangouts/$hangoutId/rsvp") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(account)
            content = """{"rsvpStatus":"${RsvpStatus.ATTENDING.name}"}"""
        }.andReturn()

    private fun getHangout(caller: TestAccount, hangoutId: HangoutId): HangoutDto {
        val response = mockMvc.get("/api/hangouts/$hangoutId") {
            authenticatedAs(caller)
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        return objectMapper.readValue(response, HangoutDto::class.java)
    }

    private fun inTwoDays(): Instant = Instant.now().plus(Duration.ofDays(2))
}
