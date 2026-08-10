package com.eeseka.lynk.payment.service

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.model.PaymentState
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.payment.domain.exception.PaystackUnavailableException
import com.eeseka.lynk.payment.domain.exception.RefundRejectedException
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.domain.model.RefundStatus
import com.eeseka.lynk.payment.infra.database.entities.PaymentEntity
import com.eeseka.lynk.payment.infra.database.repositories.PaymentRepository
import com.eeseka.lynk.payment.infra.paystack.PaystackClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class RefundService(
    private val paymentRepository: PaymentRepository,
    private val paystackClient: PaystackClient,
    private val hangoutService: HangoutService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val STUCK_REFUND_AFTER: Duration = Duration.ofHours(6L)
    }

    @Transactional
    fun applyRefundOutcome(transactionReference: String, succeeded: Boolean) {
        val payment = paymentRepository.findByReference(transactionReference)
        if (payment == null) {
            // The money moved at Paystack, and we cannot say whose it was, so nothing here will ever record it.
            logger.error("Refund outcome for a reference we do not know: {}", transactionReference)
            return
        }

        paymentRepository.save(
            payment.apply {
                refundStatus = if (succeeded) RefundStatus.PROCESSED else RefundStatus.FAILED
                if (succeeded) refundedAt = Instant.now()
            }
        )
    }

    fun refundSettledPaymentsForHangout(hangoutId: HangoutId) {
        if (isBeyondRefund(hangoutId)) {
            logger.warn("Hangout {} is past refunding, its money is already on its way to the host", hangoutId)
            return
        }

        val refundable = paymentRepository.findAllByHangoutIdAndStatusAndRefundStatus(
            hangoutId = hangoutId,
            status = PaymentStatus.SUCCESS,
            refundStatus = RefundStatus.NONE
        )

        refundable.forEach { refund(it) }

        if (refundable.isNotEmpty()) {
            logger.info("Requested {} refunds for cancelled hangout {}", refundable.size, hangoutId)
        }
    }

    fun refundSettledPaymentForParticipant(hangoutId: HangoutId, userId: UserId) {
        if (isBeyondRefund(hangoutId)) {
            logger.warn("Hangout {} is past refunding, {} cannot be sent their money back", hangoutId, userId)
            return
        }

        val payment = paymentRepository.findByHangoutIdAndUserIdAndStatusAndRefundStatus(
            hangoutId = hangoutId,
            userId = userId,
            status = PaymentStatus.SUCCESS,
            refundStatus = RefundStatus.NONE
        ) ?: return

        refund(payment)
    }

    fun refundPaymentByReference(reference: String) {
        val payment = paymentRepository.findByReference(reference)
        if (payment == null) {
            logger.warn("Asked to send back a payment for a reference we do not know: {}", reference)
            return
        }

        if (isBeyondRefund(payment.hangoutId)) {
            logger.error(
                "Payment {} should never have been collected but hangout {} is past refunding, so it is stuck",
                payment.reference, payment.hangoutId
            )
            return
        }

        refund(payment)
    }

    @Scheduled(fixedDelay = 15 * 60 * 1000)
    fun retryFailedRefunds() {
        val stuckRefunds = paymentRepository.findAllByRefundStatus(RefundStatus.FAILED)

        stuckRefunds.forEach { payment ->
            try {
                retryRefund(payment)
            } catch (e: RuntimeException) {
                logger.error("Could not retry the refund for {}: {}", payment.reference, e.message)
            }
        }

        if (stuckRefunds.isNotEmpty()) {
            logger.info("Retried {} refunds that had failed", stuckRefunds.size)
        }
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000)
    fun reportRefundsStillInFlight() {
        val stuck = paymentRepository.findAllByRefundStatusAndUpdatedAtBefore(
            refundStatus = RefundStatus.REQUESTED,
            before = Instant.now().minus(STUCK_REFUND_AFTER)
        )

        stuck.forEach { payment ->
            logger.error(
                "Refund for payment {} on hangout {} was requested at {} and has never settled - check it at Paystack",
                payment.reference, payment.hangoutId, payment.updatedAt
            )
        }
    }

    private fun retryRefund(payment: PaymentEntity) {
        if (isBeyondRefund(payment.hangoutId)) {
            logger.error(
                "Refund for {} is still stuck but hangout {} is past refunding, so it can no longer be sent back",
                payment.reference, payment.hangoutId
            )
            return
        }

        refund(payment, expectedStatus = RefundStatus.FAILED)
    }

    private fun refund(payment: PaymentEntity, expectedStatus: RefundStatus = RefundStatus.NONE) {
        val started = paymentRepository.transitionRefundStatus(
            paymentId = payment.id!!,
            newStatus = RefundStatus.REQUESTED,
            expectedStatus = expectedStatus
        ) == 1

        if (!started) {
            logger.info("A refund for {} is already in flight, leaving it alone", payment.reference)
            return
        }

        try {
            paystackClient.refund(
                transactionReference = payment.reference,
                netAmountKobo = payment.netAmountKobo
            )
        } catch (e: RefundRejectedException) {
            logger.error("Refund for {} was refused: {}", payment.reference, e.message)
            markRefundFailed(payment)
        } catch (e: PaystackUnavailableException) {
            logger.error(
                "Refund for {} went unanswered, leaving it in flight to be settled by hand: {}",
                payment.reference, e.message
            )
        }
    }

    private fun markRefundFailed(payment: PaymentEntity) {
        paymentRepository.findByReference(payment.reference)?.let { freshPayment ->
            paymentRepository.save(
                freshPayment.apply {
                    refundStatus = RefundStatus.FAILED
                    refundedAmountKobo = null
                }
            )
        }
    }

    /**
     * The refund window runs from payment until payout and no further. Once a transfer has landed in
     * the host's bank account, the money is genuinely gone - we cannot reach into their account.
     */
    private fun isBeyondRefund(hangoutId: HangoutId): Boolean =
        hangoutService.findPaymentState(hangoutId) in setOf(PaymentState.PAYING_OUT, PaymentState.PAID_OUT)
}