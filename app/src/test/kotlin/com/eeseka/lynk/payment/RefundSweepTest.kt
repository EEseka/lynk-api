package com.eeseka.lynk.payment

import com.eeseka.lynk.hangout.domain.model.PaymentState
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.domain.model.RefundStatus
import com.eeseka.lynk.payment.infra.database.entities.PaymentEntity
import com.eeseka.lynk.payment.infra.database.repositories.PaymentRepository
import com.eeseka.lynk.payment.service.RefundService
import com.eeseka.lynk.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.then
import org.mockito.Mockito.never
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A refund that Paystack refused is money we still owe someone, so it is retried rather than
 * forgotten. Both sweeps here are unreachable over HTTP and are called the way the scheduler does.
 */
class RefundSweepTest : IntegrationTest() {

    @Autowired
    private lateinit var refundService: RefundService

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Test
    fun `tries a refused refund again`() {
        val payment = arrangeFailedRefund()

        refundService.retryFailedRefunds()

        then(paystackClient).should().refund(payment.reference, payment.netAmountKobo)

        val retried = paymentRepository.findByReference(payment.reference)
        assertNotNull(retried)
        assertEquals(RefundStatus.REQUESTED, retried.refundStatus)
        assertEquals(payment.netAmountKobo, retried.refundedAmountKobo)
    }

    /**
     * Once the money has reached the host's bank account it is genuinely gone — we cannot reach into
     * their account to take it back, so the sweep must stop trying.
     */
    @Test
    fun `stops retrying once the money has been paid out`() {
        val payment = arrangeFailedRefund(paymentState = PaymentState.PAID_OUT)

        refundService.retryFailedRefunds()

        then(paystackClient).should(never()).refund(anyString(), anyLong())
        assertEquals(RefundStatus.FAILED, paymentRepository.findByReference(payment.reference)?.refundStatus)
    }

    /**
     * The stuck-refund report exists to be read by a person. It must never quietly change the money
     * it is reporting on.
     */
    @Test
    fun `reports a refund stuck in flight without touching it`() {
        val payment = arrangeFailedRefund(refundStatus = RefundStatus.REQUESTED)
        fixtures.agePayment(payment, updatedAt = Instant.now().minus(Duration.ofDays(3)))

        refundService.reportRefundsStillInFlight()

        val untouched = paymentRepository.findByReference(payment.reference)
        assertNotNull(untouched)
        assertEquals(RefundStatus.REQUESTED, untouched.refundStatus)
        assertEquals(PaymentStatus.SUCCESS, untouched.status)
        then(paystackClient).should(never()).refund(anyString(), anyLong())
    }

    private fun arrangeFailedRefund(
        paymentState: PaymentState = PaymentState.COLLECTING,
        refundStatus: RefundStatus = RefundStatus.FAILED
    ): PaymentEntity {
        val host = fixtures.user(displayName = "Ada")
        val guest = fixtures.user(displayName = "Bola")
        val hangout = fixtures.hangout(host = host, paymentState = paymentState)
        fixtures.participant(hangout = hangout, user = host)
        fixtures.participant(hangout = hangout, user = guest)

        return fixtures.payment(
            hangoutId = hangout.id!!,
            userId = guest.userId,
            status = PaymentStatus.SUCCESS,
            refundStatus = refundStatus,
            paidAt = Instant.now().minus(Duration.ofHours(2))
        )
    }
}
