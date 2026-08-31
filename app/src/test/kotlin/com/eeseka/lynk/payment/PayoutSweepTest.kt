package com.eeseka.lynk.payment

import com.eeseka.lynk.hangout.domain.model.PaymentState
import com.eeseka.lynk.hangout.infra.database.entities.HangoutEntity
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.payment.domain.exception.PaystackUnavailableException
import com.eeseka.lynk.payment.domain.exception.PayoutRejectedException
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.infra.database.repositories.HangoutPayoutAccountRepository
import com.eeseka.lynk.payment.service.PayoutService
import com.eeseka.lynk.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.then
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.never
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
    private lateinit var payoutAccountRepository: HangoutPayoutAccountRepository

    @Autowired
    private lateinit var hangoutService: HangoutService

    @Test
    fun `sends the host everything that was collected`() {
        val hangout = arrangeHangoutReadyForPayout(paidGuests = 2)

        payoutService.payHostsWhoAreDue()

        // The reference is minted inside the sweep, so it is matched rather than named.
        then(paystackClient).should().transfer(
            recipientCode = eq(RECIPIENT_CODE),
            amountKobo = eq(AMOUNT_KOBO * 2),
            reference = anyString(),
            reason = eq("Lynk hangout payout")
        )
        assertEquals(PaymentState.PAYING_OUT, hangoutService.findPaymentState(hangout.id!!))
        assertNotNull(
            payoutAccountRepository.findByHangoutId(hangout.id!!)?.transferReference,
            "the transfer was sent without recording a reference to reconcile it by"
        )
    }

    /**
     * Nothing was collected, so there is nothing to send. The hangout still has to leave the payout
     * queue, or the sweep would pick it up again every five minutes forever.
     */
    @Test
    fun `closes out a hangout that collected nothing`() {
        val hangout = arrangeHangoutReadyForPayout(paidGuests = 0)

        payoutService.payHostsWhoAreDue()

        then(paystackClient).should(never()).transfer(anyString(), anyLong(), anyString(), anyString())
        assertEquals(PaymentState.PAID_OUT, hangoutService.findPaymentState(hangout.id!!))
    }

    @Test
    fun `releases a payout the provider refused`() {
        val hangout = arrangeHangoutReadyForPayout(paidGuests = 1)
        willThrow(PayoutRejectedException("Your balance is not enough to fulfil this request"))
            .given(paystackClient).transfer(anyString(), anyLong(), anyString(), anyString())

        payoutService.payHostsWhoAreDue()

        assertEquals(PaymentState.PAYOUT_FAILED, hangoutService.findPaymentState(hangout.id!!))

        val payoutAccount = payoutAccountRepository.findByHangoutId(hangout.id!!)
        assertNotNull(payoutAccount)
        assertNotNull(payoutAccount.payoutFailureReason)
        assertNull(
            payoutAccount.transferReference,
            "Paystack refuses a reference it has already seen, so a refused one must not be kept"
        )
    }

    /**
     * Paystack never answered, so we do not know whether the money moved. Guessing either way is
     * worse than leaving it in flight for the reconciliation sweep to settle against Paystack.
     */
    @Test
    fun `leaves a payout in flight when the provider never answered`() {
        val hangout = arrangeHangoutReadyForPayout(paidGuests = 1)
        willThrow(PaystackUnavailableException("Read timed out"))
            .given(paystackClient).transfer(anyString(), anyLong(), anyString(), anyString())

        payoutService.payHostsWhoAreDue()

        assertEquals(PaymentState.PAYING_OUT, hangoutService.findPaymentState(hangout.id!!))
        assertNotNull(payoutAccountRepository.findByHangoutId(hangout.id!!)?.transferReference)
    }

    @Test
    fun `leaves alone a hangout that is not ready to be paid out`() {
        val hangout = arrangeHangoutReadyForPayout(paidGuests = 1, paymentState = PaymentState.COLLECTING)

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
            paidGuests = 1,
            paymentState = PaymentState.PAYOUT_FAILED
        )
        val payoutAccount = payoutAccountRepository.findByHangoutId(hangout.id!!)!!
        fixtures.agePayoutAccount(payoutAccount, updatedAt = Instant.now().minus(Duration.ofDays(3)))

        payoutService.reportPayoutsStillFailed()

        assertEquals(PaymentState.PAYOUT_FAILED, hangoutService.findPaymentState(hangout.id!!))
        then(paystackClient).should(never()).transfer(anyString(), anyLong(), anyString(), anyString())
    }

    private fun arrangeHangoutReadyForPayout(
        paidGuests: Int,
        paymentState: PaymentState = PaymentState.READY_FOR_PAYOUT
    ): HangoutEntity {
        val host = fixtures.user(displayName = "Ada")
        val hangout = fixtures.hangout(
            host = host,
            costPerPersonKobo = AMOUNT_KOBO,
            paymentState = paymentState,
            splitHeadcount = paidGuests + 1
        )
        fixtures.participant(hangout = hangout, user = host)

        repeat(paidGuests) { index ->
            val guest = fixtures.user(displayName = "Guest $index")
            fixtures.participant(hangout = hangout, user = guest, hasPaid = true)
            fixtures.payment(
                hangoutId = hangout.id!!,
                userId = guest.userId,
                amountKobo = AMOUNT_KOBO,
                status = PaymentStatus.SUCCESS,
                paidAt = Instant.now()
            )
        }

        fixtures.payoutAccount(hangout = hangout, host = host, recipientCode = RECIPIENT_CODE)

        return hangout
    }
}
