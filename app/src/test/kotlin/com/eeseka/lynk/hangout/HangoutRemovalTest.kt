package com.eeseka.lynk.hangout

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.api.dto.HangoutDto
import com.eeseka.lynk.hangout.domain.exception.HangoutIllegalStateException
import com.eeseka.lynk.hangout.domain.model.PaymentState
import com.eeseka.lynk.hangout.service.HangoutParticipantService
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.notification.domain.model.NotificationType
import com.eeseka.lynk.notification.infra.database.repositories.NotificationRepository
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.infra.database.repositories.PaymentRepository
import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.TestAccount
import com.eeseka.lynk.support.authenticatedAs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.BDDMockito.then
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The host taking someone out of a hangout they said yes to: the seat comes back, their money goes back,
 * and they're told.
 */
class HangoutRemovalTest : IntegrationTest() {

    private companion object {
        // Two requests rarely overlap closely enough to collide; eight reliably do.
        const val RACING_REMOVALS = 8
    }

    @Autowired
    private lateinit var hangoutService: HangoutService

    @Autowired
    private lateinit var hangoutParticipantService: HangoutParticipantService

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Test
    fun `lets the host remove somebody who is going and invite them back later`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = groupHangout(host, bola)

        remove(host, hangoutId, bola).andExpect { status { isNoContent() } }

        val after = getHangout(host, hangoutId)
        assertEquals(1, after.participantCount)
        assertTrue(after.participants.none { it.user.userId == bola.userId })
        hangouts.invite(host, hangoutId, bola)
    }

    @Test
    fun `lets nobody but the host remove somebody`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val chidi = signIn("chidi")
        val hangoutId = groupHangout(host, bola)
        hangouts.invite(host, hangoutId, chidi)
        hangouts.accept(chidi, hangoutId)

        remove(bola, hangoutId, chidi).andExpect { status { isForbidden() } }
    }

    @Test
    fun `will not remove the host from their own hangout`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = groupHangout(host, bola)

        remove(host, hangoutId, host).andExpect { status { isConflict() } }
    }

    /** Whoever turned up stays in it, the same as leaving. */
    @Test
    fun `refuses to remove somebody once the hangout is under way`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = groupHangout(host, bola)
        fixtures.moveScheduledAt(hangoutId, Instant.now().minus(Duration.ofMinutes(5)))
        hangoutService.transitionDueHangoutsToOngoing()

        remove(host, hangoutId, bola).andExpect { status { isConflict() } }
    }

    @Test
    fun `tells the person removed`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = groupHangout(host, bola)
        broker.deliverEvents()
        notificationRepository.deleteAll()

        remove(host, hangoutId, bola).andExpect { status { isNoContent() } }
        broker.deliverEvents()

        val notification = notificationRepository.findAll().single()
        assertEquals(bola.userId, notification.userId)
        assertEquals(NotificationType.PARTICIPANT_REMOVED, notification.type)
    }

    @Test
    fun `sends back the share of somebody removed after they paid`() {
        val hangout = arrangePaidAttendee(PaymentState.COLLECTING)

        hangoutParticipantService.removeParticipant(
            hostId = hangout.hostId,
            hangoutId = hangout.hangoutId,
            targetUserId = hangout.attendeeId
        )
        broker.deliverEvents()

        val payment = paymentRepository.findAll().single()
        then(paystackClient).should().refund(payment.reference, payment.netAmountKobo)
        assertEquals(PaymentStatus.REFUNDING, payment.status)
    }

    /** The money is already in the host's bank, so removing them would keep it from them for good. */
    @Test
    fun `will not remove somebody whose share has been paid out`() {
        val hangout = arrangePaidAttendee(PaymentState.PAID_OUT)

        assertThrows<HangoutIllegalStateException> {
            hangoutParticipantService.removeParticipant(
                hostId = hangout.hostId,
                hangoutId = hangout.hangoutId,
                targetUserId = hangout.attendeeId
            )
        }
    }

    @Test
    fun `removes somebody once when several removals of them arrive together`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = groupHangout(host, bola)

        val barrier = CyclicBarrier(RACING_REMOVALS)
        val executor = Executors.newFixedThreadPool(RACING_REMOVALS)
        val results = try {
            (1..RACING_REMOVALS).map {
                executor.submit<MvcResult> {
                    barrier.await()
                    remove(host, hangoutId, bola).andReturn()
                }
            }.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        val statuses = results.map { it.response.status }
        assertEquals(1, statuses.count { it == 204 }, "expected exactly one removal to go through: $statuses")
        assertTrue(statuses.all { it == 204 || it == 404 }, "a removal failed for the wrong reason: $statuses")
        assertEquals(1, getHangout(host, hangoutId).participantCount)
    }

    private fun remove(host: TestAccount, hangoutId: HangoutId, target: TestAccount): ResultActionsDsl =
        mockMvc.delete("/api/hangouts/$hangoutId/participants/${target.userId}") {
            authenticatedAs(host)
        }

    private fun getHangout(caller: TestAccount, hangoutId: HangoutId): HangoutDto {
        val response = mockMvc.get("/api/hangouts/$hangoutId") {
            authenticatedAs(caller)
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        return objectMapper.readValue(response, HangoutDto::class.java)
    }

    private fun groupHangout(host: TestAccount, attendee: TestAccount): HangoutId {
        val hangoutId = hangouts.scheduled(host).id
        hangouts.invite(host, hangoutId, attendee)
        hangouts.accept(attendee, hangoutId)
        return hangoutId
    }

    private fun arrangePaidAttendee(paymentState: PaymentState): ArrangedHangout {
        val host = fixtures.user(displayName = "Ada")
        val attendee = fixtures.user(displayName = "Bola")
        val hangout = fixtures.hangout(host = host, paymentState = paymentState, participantCount = 2)
        fixtures.participant(hangout = hangout, user = host)
        fixtures.participant(hangout = hangout, user = attendee, hasPaid = true)
        fixtures.payment(
            hangoutId = hangout.id!!,
            userId = attendee.userId,
            status = PaymentStatus.SUCCESS,
            paidAt = Instant.now()
        )

        return ArrangedHangout(hangoutId = hangout.id!!, hostId = host.userId, attendeeId = attendee.userId)
    }

    private data class ArrangedHangout(
        val hangoutId: HangoutId,
        val hostId: UserId,
        val attendeeId: UserId
    )

    private fun signIn(username: String): TestAccount = accounts.signIn(
        email = "$username@lynk.test",
        displayName = username.replaceFirstChar { it.uppercase() },
        username = username
    )
}
