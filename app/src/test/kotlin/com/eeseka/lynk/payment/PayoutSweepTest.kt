package com.eeseka.lynk.payment

import com.eeseka.lynk.hangout.domain.model.PaymentState
import com.eeseka.lynk.hangout.infra.database.entities.HangoutEntity
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.payment.domain.exception.PaystackUnavailableException
import com.eeseka.lynk.payment.domain.exception.PayoutRejectedException
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.domain.model.PayoutStatus
import com.eeseka.lynk.payment.infra.database.repositories.PayoutRepository
import com.eeseka.lynk.payment.service.PayoutService
import com.eeseka.lynk.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.then
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Paying the host is the last thing that happens to a hangout's money, and the only step where it
 * leaves us. The sweep runs every five minutes and cannot be reached over HTTP.
 */
class PayoutSweepTest : IntegrationTest() {

    private companion object {
        const val AMOUNT_KOBO = 500_000L
        const val RECIPIENT_CODE = "RCP_test_recipient"
    }

    @Autowired
    private lateinit var payoutService: PayoutService

    @Autowired
    private lateinit var payoutRepository: PayoutRepository

    @Autowired
    private lateinit var hangoutService: HangoutService

    @Test
    fun `sends the host everything that was collected`() {
        val hangout = arrangeHangoutReadyForPayout(paidAttendees = 2)

        payoutService.payHostsWhoAreDue()

        // The reference is minted inside the sweep, so it is matched rather than named.
        then(paystackClient).should().transfer(
            recipientCode = eq(RECIPIENT_CODE),
            amountKobo = eq(AMOUNT_KOBO * 2),
            reference = anyString(),
            reason = eq("Lynk hangout payout")
        )
        assertEquals(PaymentState.PAYING_OUT, hangoutService.findPaymentState(hangout.id!!))
        val payout = payoutRepository.findAll().single()
        assertEquals(PayoutStatus.IN_FLIGHT, payout.status, "the transfer was sent without recording it to reconcile by")
        assertEquals(AMOUNT_KOBO * 2, payout.amountKobo)
    }

    /**
     * Nothing was collected, so there is nothing to send. The hangout still has to leave the payout
     * queue, or the sweep would pick it up again every five minutes forever.
     */
    @Test
    fun `closes out a hangout that collected nothing`() {
        val hangout = arrangeHangoutReadyForPayout(paidAttendees = 0)

        payoutService.payHostsWhoAreDue()

        then(paystackClient).should(never()).transfer(anyString(), anyLong(), anyString(), anyString())
        assertEquals(PaymentState.PAID_OUT, hangoutService.findPaymentState(hangout.id!!))
    }

    @Test
    fun `releases a payout the provider refused`() {
        val hangout = arrangeHangoutReadyForPayout(paidAttendees = 1)
        willThrow(PayoutRejectedException("Your balance is not enough to fulfil this request"))
            .given(paystackClient).transfer(anyString(), anyLong(), anyString(), anyString())

        payoutService.payHostsWhoAreDue()

        assertEquals(PaymentState.PAYOUT_FAILED, hangoutService.findPaymentState(hangout.id!!))
        val payout = payoutRepository.findAll().single()
        assertEquals(PayoutStatus.FAILED, payout.status)
        assertEquals("Your balance is not enough to fulfil this request", payout.failureReason)
    }

    // Paystack refuses a reference it has already seen, so the retry is a new transfer and the refused one stays on record
    @Test
    fun `keeps a refused payout when the host retries`() {
        val hangout = arrangeHangoutReadyForPayout(paidAttendees = 1)
        willThrow(PayoutRejectedException("Your balance is not enough to fulfil this request"))
            .given(paystackClient).transfer(anyString(), anyLong(), anyString(), anyString())
        payoutService.payHostsWhoAreDue()
        reset(paystackClient)

        payoutService.retryPayout(hostId = hangout.hostId, hangoutId = hangout.id!!)
        payoutService.payHostsWhoAreDue()

        val payouts = payoutRepository.findAll().sortedBy { it.id }
        assertEquals(listOf(PayoutStatus.FAILED, PayoutStatus.IN_FLIGHT), payouts.map { it.status })
        assertNotEquals(payouts[0].reference, payouts[1].reference)
    }

    /**
     * Paystack never answered, so we do not know whether the money moved. Guessing either way is
     * worse than leaving it in flight for the reconciliation sweep to settle against Paystack.
     */
    @Test
    fun `leaves a payout in flight when the provider never answered`() {
        val hangout = arrangeHangoutReadyForPayout(paidAttendees = 1)
        willThrow(PaystackUnavailableException("Read timed out"))
            .given(paystackClient).transfer(anyString(), anyLong(), anyString(), anyString())

        payoutService.payHostsWhoAreDue()

        assertEquals(PaymentState.PAYING_OUT, hangoutService.findPaymentState(hangout.id!!))
        assertEquals(PayoutStatus.IN_FLIGHT, payoutRepository.findAll().single().status)
    }

    @Test
    fun `leaves alone a hangout that is not ready to be paid out`() {
        val hangout = arrangeHangoutReadyForPayout(paidAttendees = 1, paymentState = PaymentState.COLLECTING)

        payoutService.payHostsWhoAreDue()

        then(paystackClient).should(never()).transfer(anyString(), anyLong(), anyString(), anyString())
        assertEquals(PaymentState.COLLECTING, hangoutService.findPaymentState(hangout.id!!))
    }

    /**
     * The stuck-payout report is read by a person and must not change what it reports on.
     */
    @Test
    fun `reports a payout still unpaid without touching it`() {
        val hangout = arrangeHangoutReadyForPayout(
            paidAttendees = 1,
            paymentState = PaymentState.PAYOUT_FAILED
        )
        val payout = fixtures.payout(hangoutId = hangout.id!!, status = PayoutStatus.FAILED, failureReason = "Refused")
        fixtures.agePayout(payout, settledAt = Instant.now().minus(Duration.ofDays(3)))

        payoutService.reportPayoutsStillFailed()

        assertEquals(PaymentState.PAYOUT_FAILED, hangoutService.findPaymentState(hangout.id!!))
        then(paystackClient).should(never()).transfer(anyString(), anyLong(), anyString(), anyString())
    }

    private fun arrangeHangoutReadyForPayout(
        paidAttendees: Int,
        paymentState: PaymentState = PaymentState.READY_FOR_PAYOUT
    ): HangoutEntity {
        val host = fixtures.user(displayName = "Ada")
        val hangout = fixtures.hangout(
            host = host,
            costPerPersonKobo = AMOUNT_KOBO,
            paymentState = paymentState,
            splitHeadcount = paidAttendees + 1
        )
        fixtures.participant(hangout = hangout, user = host)

        repeat(paidAttendees) { index ->
            val attendee = fixtures.user(displayName = "Attendee $index")
            fixtures.participant(hangout = hangout, user = attendee, hasPaid = true)
            fixtures.payment(
                hangoutId = hangout.id!!,
                userId = attendee.userId,
                amountKobo = AMOUNT_KOBO,
                status = PaymentStatus.SUCCESS,
                paidAt = Instant.now()
            )
        }

        fixtures.payoutAccount(hangout = hangout, host = host, recipientCode = RECIPIENT_CODE)

        return hangout
    }
}
