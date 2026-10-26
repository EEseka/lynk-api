package com.eeseka.lynk.payment.service

import com.eeseka.lynk.common.domain.events.hangout.HangoutEvent
import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.common.infra.message_queue.EventPublisher
import com.eeseka.lynk.hangout.domain.model.PaymentState
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.payment.domain.exception.PaystackUnavailableException
import com.eeseka.lynk.payment.domain.exception.RefundRejectedException
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.domain.model.RefundStatus
import com.eeseka.lynk.payment.infra.database.entities.PaymentEntity
import com.eeseka.lynk.payment.infra.database.repositories.PaymentRepository
import com.eeseka.lynk.payment.infra.database.repositories.RefundRepository
import com.eeseka.lynk.payment.infra.paystack.PaystackClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class RefundService(
    private val paymentRepository: PaymentRepository,
    private val refundRepository: RefundRepository,
    private val paystackClient: PaystackClient,
    private val hangoutService: HangoutService,
    private val eventPublisher: EventPublisher
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val STUCK_REFUND_AFTER: Duration = Duration.ofHours(6L)
    }

    fun applyRefundOutcome(transactionReference: String, succeeded: Boolean) {
        val payment = paymentRepository.findByReference(transactionReference)
        if (payment == null) {
            // The money moved at Paystack, and we cannot say whose it was, so nothing here will ever record it.
            logger.error("Refund outcome for a reference we do not know: {}", transactionReference)
            return
        }

        val settledAt = Instant.now()
        // Zero rows mean no refund of this payment is in flight, so the provider is repeating itself
        val settled = refundRepository.settleInFlight(
            paymentId = payment.id!!,
            newStatus = if (succeeded) RefundStatus.PROCESSED else RefundStatus.FAILED,
            failureReason = if (succeeded) null else "The provider reported the refund as failed",
            settledAt = settledAt,
            inFlightStatus = RefundStatus.REQUESTED
        ) == 1
        if (!settled) {
            logger.info("No refund of {} is in flight, so this outcome was already recorded", payment.reference)
            return
        }

        paymentRepository.transitionStatus(
            paymentId = payment.id!!,
            newStatus = if (succeeded) PaymentStatus.REFUNDED else PaymentStatus.REFUND_FAILED,
            expectedStatus = PaymentStatus.REFUNDING,
            updatedAt = settledAt
        )

        if (!succeeded) return

        val hangoutName = hangoutService.findHangoutName(payment.hangoutId)
        if (hangoutName == null) {
            logger.warn(
                "Refunded {} but hangout {} is gone, so there is nothing to name in a notification",
                payment.reference, payment.hangoutId
            )
            return
        }

        eventPublisher.publish(
            HangoutEvent.RefundIssued(
                hangoutId = payment.hangoutId,
                hangoutName = hangoutName,
                participantId = payment.userId,
                amountKobo = payment.netAmountKobo,
                reference = payment.reference
            )
        )
    }

    fun refundSettledPaymentsForHangout(hangoutId: HangoutId) {
        if (isBeyondRefund(hangoutId)) {
            logger.warn("Hangout {} is past refunding, its money is already on its way to the host", hangoutId)
            return
        }

        val refundable = paymentRepository.findAllByHangoutIdAndStatus(
            hangoutId = hangoutId,
            status = PaymentStatus.SUCCESS
        )

        refundable.forEach { refund(it, expectedStatus = PaymentStatus.SUCCESS) }

        if (refundable.isNotEmpty()) {
            logger.info("Requested {} refunds for cancelled hangout {}", refundable.size, hangoutId)
        }
    }

    fun refundSettledPaymentForParticipant(hangoutId: HangoutId, userId: UserId) {
        if (isBeyondRefund(hangoutId)) {
            logger.warn("Hangout {} is past refunding, {} cannot be sent their money back", hangoutId, userId)
            return
        }

        val payment = paymentRepository.findByHangoutIdAndUserIdAndStatus(
            hangoutId = hangoutId,
            userId = userId,
            status = PaymentStatus.SUCCESS
        ) ?: return

        refund(payment, expectedStatus = PaymentStatus.SUCCESS)
    }

    /**
     * [alreadyClaimed] is for a payment whose refund was claimed by the transaction that recorded it:
     * [refund] would find it already REFUNDING, read that as somebody else's refund on its way, and send
     * nothing at all.
     */
    fun refundPaymentByReference(reference: String, alreadyClaimed: Boolean = false) {
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

        if (alreadyClaimed) {
            sendRefund(payment)
            return
        }

        // Money we hold, or a short payment we refused to keep. Anything else is already going back or gone
        if (payment.status != PaymentStatus.SUCCESS && payment.status != PaymentStatus.FAILED) {
            logger.info("Payment {} is {}, so there is nothing more to send back", payment.reference, payment.status)
            return
        }

        refund(payment, expectedStatus = payment.status)
    }

    @Scheduled(fixedDelay = 15 * 60 * 1000)
    fun retryFailedRefunds() {
        val stuckRefunds = paymentRepository.findAllByStatus(PaymentStatus.REFUND_FAILED)

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
        val stuck = paymentRepository.findAllByStatusAndUpdatedAtBefore(
            status = PaymentStatus.REFUNDING,
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

        refund(payment, expectedStatus = PaymentStatus.REFUND_FAILED)
    }

    private fun refund(payment: PaymentEntity, expectedStatus: PaymentStatus) {
        // One row changed means this call is the one that claimed the refund; zero means somebody else claimed it first and is already sending the money back.
        val claimed = paymentRepository.transitionStatus(
            paymentId = payment.id!!,
            newStatus = PaymentStatus.REFUNDING,
            expectedStatus = expectedStatus,
            updatedAt = Instant.now()
        ) == 1

        if (!claimed) {
            logger.info("A refund for {} is already in flight, leaving it alone", payment.reference)
            return
        }

        sendRefund(payment)
    }

    private fun sendRefund(payment: PaymentEntity) {
        // Recorded before the provider is called, so every attempt is kept whatever it comes to
        refundRepository.insert(
            paymentId = payment.id!!,
            amountKobo = payment.netAmountKobo,
            status = RefundStatus.REQUESTED,
            createdAt = Instant.now()
        )

        try {
            paystackClient.refund(
                transactionReference = payment.reference,
                netAmountKobo = payment.netAmountKobo
            )
        } catch (e: RefundRejectedException) {
            logger.error("Refund for {} was refused: {}", payment.reference, e.message)
            markRefundFailed(payment, reason = e.message?.take(255) ?: "The provider refused the refund")
        } catch (e: PaystackUnavailableException) {
            logger.error(
                "Refund for {} went unanswered, leaving it in flight to be settled by hand: {}",
                payment.reference, e.message
            )
        }
    }

    private fun markRefundFailed(payment: PaymentEntity, reason: String) {
        val failedAt = Instant.now()
        refundRepository.settleInFlight(
            paymentId = payment.id!!,
            newStatus = RefundStatus.FAILED,
            failureReason = reason,
            settledAt = failedAt,
            inFlightStatus = RefundStatus.REQUESTED
        )
        paymentRepository.transitionStatus(
            paymentId = payment.id!!,
            newStatus = PaymentStatus.REFUND_FAILED,
            expectedStatus = PaymentStatus.REFUNDING,
            updatedAt = failedAt
        )
    }

    /**
     * The refund window runs from payment until payout and no further. Once a transfer has landed in
     * the host's bank account, the money is genuinely gone - we cannot reach into their account.
     */
    private fun isBeyondRefund(hangoutId: HangoutId): Boolean =
        hangoutService.findPaymentState(hangoutId) in setOf(PaymentState.PAYING_OUT, PaymentState.PAID_OUT)
}
