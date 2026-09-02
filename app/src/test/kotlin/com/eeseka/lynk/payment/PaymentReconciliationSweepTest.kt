package com.eeseka.lynk.payment

import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.PaymentState
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.infra.database.entities.HangoutEntity
import com.eeseka.lynk.hangout.infra.database.entities.HangoutUserEntity
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutParticipantRepository
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.domain.model.ProviderTransaction
import com.eeseka.lynk.payment.domain.model.ProviderTransactionStatus
import com.eeseka.lynk.payment.domain.model.RefundStatus
import com.eeseka.lynk.payment.infra.database.entities.PaymentEntity
import com.eeseka.lynk.payment.infra.database.repositories.HangoutPayoutAccountRepository
import com.eeseka.lynk.payment.infra.database.repositories.PaymentRepository
import com.eeseka.lynk.payment.service.PaymentReconciliationService
import com.eeseka.lynk.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mockito.never
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sweeps that clean up after a webhook that never arrived. None of them can be reached over
 * HTTP, so they are called here the way the scheduler calls them.
 *
 * They are the last line for money: a charge Paystack took but never told us about, a refund nobody
 * asked for, a transfer that left without an answer.
 */
class PaymentReconciliationSweepTest : IntegrationTest() {

    private companion object {
        const val AMOUNT_KOBO = 500_000L
        const val TRANSFER_REFERENCE = "lynk_payout_test_reference"
    }

    @Autowired
    private lateinit var reconciliationService: PaymentReconciliationService

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var payoutAccountRepository: HangoutPayoutAccountRepository

    @Autowired
    private lateinit var participantRepository: HangoutParticipantRepository

    @Autowired
    private lateinit var hangoutService: HangoutService

    @Test
    fun `confirms a stale payment the provider says went through`() {
        val payment = arrangeStalePayment()
        given(paystackClient.verifyTransaction(payment.reference))
            .willReturn(ProviderTransaction(ProviderTransactionStatus.SUCCEEDED, AMOUNT_KOBO))

        reconciliationService.reconcileStalePayments()

        val reconciled = paymentRepository.findByReference(payment.reference)
        assertNotNull(reconciled)
        assertEquals(PaymentStatus.SUCCESS, reconciled.status)

        val participant = participantRepository.findByHangoutIdAndHangoutUserUserId(
            hangoutId = payment.hangoutId,
            userId = payment.userId
        )
        assertNotNull(participant)
        assertTrue(participant.hasPaid, "the sweep confirmed the money but never seated the guest")
    }

    @Test
    fun `fails a stale payment the provider says did not go through`() {
        val payment = arrangeStalePayment()
        given(paystackClient.verifyTransaction(payment.reference))
            .willReturn(ProviderTransaction(ProviderTransactionStatus.FAILED, null))

        reconciliationService.reconcileStalePayments()

        val reconciled = paymentRepository.findByReference(payment.reference)
        assertNotNull(reconciled)
        assertEquals(PaymentStatus.FAILED, reconciled.status)
        assertNotNull(reconciled.failureReason)
    }

    @Test
    fun `leaves a stale payment pending while the provider is still deciding`() {
        val payment = arrangeStalePayment()
        given(paystackClient.verifyTransaction(payment.reference))
            .willReturn(ProviderTransaction(ProviderTransactionStatus.PENDING, null))

        reconciliationService.reconcileStalePayments()

        assertEquals(PaymentStatus.PENDING, paymentRepository.findByReference(payment.reference)?.status)
    }

    /**
     * A day of the provider saying "pending" is the provider saying nobody ever finished paying.
     */
    @Test
    fun `abandons a payment that was never completed`() {
        val payment = arrangeStalePayment(age = Duration.ofDays(2))
        given(paystackClient.verifyTransaction(payment.reference))
            .willReturn(ProviderTransaction(ProviderTransactionStatus.PENDING, null))

        reconciliationService.reconcileStalePayments()

        assertEquals(PaymentStatus.ABANDONED, paymentRepository.findByReference(payment.reference)?.status)
    }

    @Test
    fun `does not go asking about a payment that has only just started`() {
        val payment = arrangeStalePayment(age = Duration.ofMinutes(2))

        reconciliationService.reconcileStalePayments()

        then(paystackClient).should(never()).verifyTransaction(anyString())
        assertEquals(PaymentStatus.PENDING, paymentRepository.findByReference(payment.reference)?.status)
    }

    @Test
    fun `sends back money held for a hangout that was cancelled`() {
        val payment = arrangeSettledPayment(hangoutStatus = HangoutStatus.CANCELLED)

        reconciliationService.recoverRefundsNeverRequested()

        assertEquals(RefundStatus.REQUESTED, paymentRepository.findByReference(payment.reference)?.refundStatus)
    }

    @Test
    fun `sends back money held for someone who stopped attending`() {
        val payment = arrangeSettledPayment(rsvpStatus = RsvpStatus.DECLINED)

        reconciliationService.recoverRefundsNeverRequested()

        assertEquals(RefundStatus.REQUESTED, paymentRepository.findByReference(payment.reference)?.refundStatus)
    }

    @Test
    fun `keeps money for a guest who is still coming`() {
        val payment = arrangeSettledPayment()

        reconciliationService.recoverRefundsNeverRequested()

        assertEquals(RefundStatus.NONE, paymentRepository.findByReference(payment.reference)?.refundStatus)
    }

    /**
     * Somebody who paid, was sent their money back, and was invited again owns two successful payments:
     * the old refunded one and the live one. The refunded one is older, so reading the oldest success as
     * the payment that bought the seat sends back the only money we are actually holding.
     */
    @Test
    fun `keeps money for a guest who paid again after being refunded`() {
        val arranged = arrangeHangoutWithGuest()
        fixtures.payment(
            hangoutId = arranged.hangout.id!!,
            userId = arranged.guest.userId,
            amountKobo = AMOUNT_KOBO,
            status = PaymentStatus.SUCCESS,
            refundStatus = RefundStatus.PROCESSED,
            paidAt = Instant.now().minus(Duration.ofHours(6))
        )
        val current = fixtures.payment(
            hangoutId = arranged.hangout.id!!,
            userId = arranged.guest.userId,
            amountKobo = AMOUNT_KOBO,
            status = PaymentStatus.SUCCESS,
            paidAt = Instant.now().minus(Duration.ofHours(1))
        )

        reconciliationService.recoverRefundsNeverRequested()

        assertEquals(
            RefundStatus.NONE,
            paymentRepository.findByReference(current.reference)?.refundStatus,
            "the sweep sent back the payment that bought the seat"
        )
    }

    /**
     * Once the money is on its way to the host's bank account, there is nothing left to send back,
     * so the sweep must not start a refund it cannot honor.
     */
    @Test
    fun `does not chase a refund once the payout is on its way`() {
        val payment = arrangeSettledPayment(
            hangoutStatus = HangoutStatus.CANCELLED,
            paymentState = PaymentState.PAYING_OUT
        )

        reconciliationService.recoverRefundsNeverRequested()

        assertEquals(RefundStatus.NONE, paymentRepository.findByReference(payment.reference)?.refundStatus)
    }

    @Test
    fun `settles a payout the provider says landed`() {
        val hangout = arrangePayoutInFlight()
        given(paystackClient.verifyTransfer(TRANSFER_REFERENCE))
            .willReturn(ProviderTransaction(ProviderTransactionStatus.SUCCEEDED, AMOUNT_KOBO))

        reconciliationService.reconcileStalePayouts()

        assertEquals(PaymentState.PAID_OUT, hangoutService.findPaymentState(hangout.id!!))
        assertNotNull(payoutAccountRepository.findByHangoutId(hangout.id!!)?.paidOutAt)
    }

    @Test
    fun `releases a payout the provider says never landed`() {
        val hangout = arrangePayoutInFlight()
        given(paystackClient.verifyTransfer(TRANSFER_REFERENCE))
            .willReturn(ProviderTransaction(ProviderTransactionStatus.FAILED, null))

        reconciliationService.reconcileStalePayouts()

        assertEquals(PaymentState.PAYOUT_FAILED, hangoutService.findPaymentState(hangout.id!!))
        val payoutAccount = payoutAccountRepository.findByHangoutId(hangout.id!!)
        assertNotNull(payoutAccount)
        assertNull(payoutAccount.transferReference, "a failed transfer's reference must not be reused")
    }

    /**
     * The shape [com.eeseka.lynk.payment.service.PayoutService] leaves behind when Paystack refused
     * the transfer, but the second write never ran: paying out, with nothing to ask Paystack about.
     */
    @Test
    fun `releases a payout that was never actually sent`() {
        val hangout = arrangePayoutInFlight(transferReference = null)

        reconciliationService.reconcileStalePayouts()

        assertEquals(PaymentState.PAYOUT_FAILED, hangoutService.findPaymentState(hangout.id!!))
        then(paystackClient).should(never()).verifyTransfer(anyString())
    }

    private fun arrangeStalePayment(age: Duration = Duration.ofHours(1)): PaymentEntity {
        val arranged = arrangeHangoutWithGuest()
        val payment = fixtures.payment(
            hangoutId = arranged.hangout.id!!,
            userId = arranged.guest.userId,
            amountKobo = AMOUNT_KOBO
        )
        fixtures.agePayment(payment, createdAt = Instant.now().minus(age))

        return payment
    }

    private fun arrangeSettledPayment(
        hangoutStatus: HangoutStatus = HangoutStatus.SCHEDULED,
        rsvpStatus: RsvpStatus = RsvpStatus.ATTENDING,
        paymentState: PaymentState = PaymentState.COLLECTING
    ): PaymentEntity {
        val arranged = arrangeHangoutWithGuest(
            hangoutStatus = hangoutStatus,
            rsvpStatus = rsvpStatus,
            paymentState = paymentState
        )

        return fixtures.payment(
            hangoutId = arranged.hangout.id!!,
            userId = arranged.guest.userId,
            amountKobo = AMOUNT_KOBO,
            status = PaymentStatus.SUCCESS,
            paidAt = Instant.now().minus(Duration.ofHours(2))
        )
    }

    private fun arrangePayoutInFlight(transferReference: String? = TRANSFER_REFERENCE): HangoutEntity {
        val arranged = arrangeHangoutWithGuest(paymentState = PaymentState.PAYING_OUT)
        fixtures.payment(
            hangoutId = arranged.hangout.id!!,
            userId = arranged.guest.userId,
            amountKobo = AMOUNT_KOBO,
            status = PaymentStatus.SUCCESS,
            paidAt = Instant.now()
        )
        fixtures.payoutAccount(
            hangout = arranged.hangout,
            host = arranged.host,
            transferReference = transferReference
        )

        return arranged.hangout
    }

    private fun arrangeHangoutWithGuest(
        hangoutStatus: HangoutStatus = HangoutStatus.SCHEDULED,
        rsvpStatus: RsvpStatus = RsvpStatus.ATTENDING,
        paymentState: PaymentState = PaymentState.COLLECTING
    ): ArrangedHangout {
        val host = fixtures.user(displayName = "Ada")
        val guest = fixtures.user(displayName = "Bola")
        val hangout = fixtures.hangout(
            host = host,
            status = hangoutStatus,
            costPerPersonKobo = AMOUNT_KOBO,
            paymentState = paymentState
        )
        fixtures.participant(hangout = hangout, user = host)
        fixtures.participant(hangout = hangout, user = guest, rsvpStatus = rsvpStatus)

        return ArrangedHangout(hangout = hangout, host = host, guest = guest)
    }

    private data class ArrangedHangout(
        val hangout: HangoutEntity,
        val host: HangoutUserEntity,
        val guest: HangoutUserEntity
    )
}
