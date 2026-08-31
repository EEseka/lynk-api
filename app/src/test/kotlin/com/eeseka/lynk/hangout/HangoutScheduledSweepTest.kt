package com.eeseka.lynk.hangout

import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.PaymentState
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.infra.database.entities.HangoutEntity
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutRepository
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The four sweeps that move a hangout along when nobody is looking: the payment deadline passing,
 * a host who never answered it, the start time arriving, and the nightly tidy-up.
 *
 * The first two decide what happens to money, so they are the ones that matter most here. None can
 * be reached over HTTP; the scheduler is off under the test profile, and they are called directly.
 */
class HangoutScheduledSweepTest : IntegrationTest() {

    @Autowired
    private lateinit var hangoutService: HangoutService

    @Autowired
    private lateinit var hangoutRepository: HangoutRepository

    @Test
    fun `readies a hangout for payout when everybody paid by the deadline`() {
        val hangout = arrangeHangout(paymentDeadline = anHourAgo(), guestHasPaid = true)

        hangoutService.resolvePaymentDeadlines()

        assertEquals(PaymentState.READY_FOR_PAYOUT, hangoutService.findPaymentState(hangout.id!!))
    }

    @Test
    fun `asks the host what to do when somebody did not pay`() {
        val hangout = arrangeHangout(paymentDeadline = anHourAgo(), guestHasPaid = false)

        hangoutService.resolvePaymentDeadlines()

        assertEquals(PaymentState.AWAITING_HOST_DECISION, hangoutService.findPaymentState(hangout.id!!))
    }

    @Test
    fun `ignores people who are not attending when counting who has paid`() {
        val hangout = arrangeHangout(
            paymentDeadline = anHourAgo(),
            guestHasPaid = false,
            guestRsvpStatus = RsvpStatus.DECLINED
        )

        hangoutService.resolvePaymentDeadlines()

        assertEquals(PaymentState.READY_FOR_PAYOUT, hangoutService.findPaymentState(hangout.id!!))
    }

    @Test
    fun `leaves a deadline that has not arrived alone`() {
        val hangout = arrangeHangout(paymentDeadline = inAnHour(), guestHasPaid = false)

        hangoutService.resolvePaymentDeadlines()

        assertEquals(PaymentState.COLLECTING, hangoutService.findPaymentState(hangout.id!!))
    }

    @Test
    fun `leaves a cancelled hangout out of the deadline sweep`() {
        val hangout = arrangeHangout(
            paymentDeadline = anHourAgo(),
            guestHasPaid = false,
            status = HangoutStatus.CANCELLED
        )

        hangoutService.resolvePaymentDeadlines()

        assertEquals(PaymentState.COLLECTING, hangoutService.findPaymentState(hangout.id!!))
    }

    /**
     * The host was asked and never answered, and the hangout is now in the past. It goes ahead with
     * whoever paid rather than sitting on their money indefinitely.
     */
    @Test
    fun `proceeds with a hangout the host never decided on`() {
        val hangout = arrangeHangout(
            paymentState = PaymentState.AWAITING_HOST_DECISION,
            scheduledAt = anHourAgo(),
            guestHasPaid = false
        )

        hangoutService.proceedWithHangoutsTheHostNeverDecided()

        assertEquals(PaymentState.READY_FOR_PAYOUT, hangoutService.findPaymentState(hangout.id!!))
    }

    @Test
    fun `still waits on a host whose hangout has not happened yet`() {
        val hangout = arrangeHangout(
            paymentState = PaymentState.AWAITING_HOST_DECISION,
            scheduledAt = inAnHour(),
            guestHasPaid = false
        )

        hangoutService.proceedWithHangoutsTheHostNeverDecided()

        assertEquals(PaymentState.AWAITING_HOST_DECISION, hangoutService.findPaymentState(hangout.id!!))
    }

    @Test
    fun `does not proceed with a cancelled hangout`() {
        val hangout = arrangeHangout(
            paymentState = PaymentState.AWAITING_HOST_DECISION,
            scheduledAt = anHourAgo(),
            status = HangoutStatus.CANCELLED,
            guestHasPaid = false
        )

        hangoutService.proceedWithHangoutsTheHostNeverDecided()

        assertEquals(PaymentState.AWAITING_HOST_DECISION, hangoutService.findPaymentState(hangout.id!!))
    }

    @Test
    fun `starts a hangout whose time has come`() {
        val hangout = arrangeHangout(scheduledAt = anHourAgo(), guestHasPaid = true)

        hangoutService.transitionDueHangoutsToOngoing()

        assertEquals(HangoutStatus.ONGOING, statusOf(hangout))
    }

    @Test
    fun `leaves a hangout that has not started yet`() {
        val hangout = arrangeHangout(scheduledAt = inAnHour(), guestHasPaid = true)

        hangoutService.transitionDueHangoutsToOngoing()

        assertEquals(HangoutStatus.SCHEDULED, statusOf(hangout))
    }

    /**
     * A hangout still choosing its spot has not been scheduled, so its start time means nothing yet.
     */
    @Test
    fun `does not start a hangout that is still voting`() {
        val hangout = arrangeHangout(
            scheduledAt = anHourAgo(),
            status = HangoutStatus.VOTING,
            guestHasPaid = true
        )

        hangoutService.transitionDueHangoutsToOngoing()

        assertEquals(HangoutStatus.VOTING, statusOf(hangout))
    }

    @Test
    fun `deletes a long past hangout nobody joined and nobody paid for`() {
        val hangout = arrangeSoloHangout(scheduledAt = Instant.now().minus(Duration.ofDays(40)))

        hangoutService.cleanupSoloUnpaidHangouts()

        assertNull(hangoutRepository.findByIdOrNull(hangout.id!!))
    }

    @Test
    fun `keeps a solo hangout that had money on it`() {
        val hangout = arrangeSoloHangout(
            scheduledAt = Instant.now().minus(Duration.ofDays(40)),
            costPerPersonKobo = 500_000L
        )

        hangoutService.cleanupSoloUnpaidHangouts()

        assertNotNull(hangoutRepository.findByIdOrNull(hangout.id!!))
    }

    @Test
    fun `keeps a solo hangout that is only recently past`() {
        val hangout = arrangeSoloHangout(scheduledAt = Instant.now().minus(Duration.ofDays(2)))

        hangoutService.cleanupSoloUnpaidHangouts()

        assertNotNull(hangoutRepository.findByIdOrNull(hangout.id!!))
    }

    @Test
    fun `keeps a long past hangout somebody else joined`() {
        val host = fixtures.user(displayName = "Ada")
        val guest = fixtures.user(displayName = "Bola")
        val hangout = fixtures.hangout(
            host = host,
            costPerPersonKobo = null,
            scheduledAt = Instant.now().minus(Duration.ofDays(40)),
            participantCount = 2
        )
        fixtures.participant(hangout = hangout, user = host)
        fixtures.participant(hangout = hangout, user = guest)

        hangoutService.cleanupSoloUnpaidHangouts()

        assertNotNull(hangoutRepository.findByIdOrNull(hangout.id!!))
    }

    private fun arrangeHangout(
        status: HangoutStatus = HangoutStatus.SCHEDULED,
        paymentState: PaymentState = PaymentState.COLLECTING,
        paymentDeadline: Instant = inAnHour(),
        scheduledAt: Instant = Instant.now().plus(Duration.ofDays(3)),
        guestHasPaid: Boolean,
        guestRsvpStatus: RsvpStatus = RsvpStatus.ATTENDING
    ): HangoutEntity {
        val host = fixtures.user(displayName = "Ada")
        val guest = fixtures.user(displayName = "Bola")
        val hangout = fixtures.hangout(
            host = host,
            status = status,
            paymentState = paymentState,
            scheduledAt = scheduledAt,
            paymentDeadline = paymentDeadline,
            participantCount = 2
        )
        fixtures.participant(hangout = hangout, user = host, hasPaid = true)
        fixtures.participant(
            hangout = hangout,
            user = guest,
            rsvpStatus = guestRsvpStatus,
            hasPaid = guestHasPaid
        )

        return hangout
    }

    /** A hangout with only its host on it, and payments never turned on. */
    private fun arrangeSoloHangout(
        scheduledAt: Instant,
        costPerPersonKobo: Long? = null
    ): HangoutEntity {
        val host = fixtures.user(displayName = "Ada")
        val hangout = fixtures.hangout(
            host = host,
            costPerPersonKobo = costPerPersonKobo,
            scheduledAt = scheduledAt,
            participantCount = 1
        )
        fixtures.participant(hangout = hangout, user = host)

        return hangout
    }

    private fun statusOf(hangout: HangoutEntity) = hangoutRepository.findByIdOrNull(hangout.id!!)?.status

    private fun anHourAgo() = Instant.now().minus(Duration.ofHours(1))

    private fun inAnHour() = Instant.now().plus(Duration.ofHours(1))
}