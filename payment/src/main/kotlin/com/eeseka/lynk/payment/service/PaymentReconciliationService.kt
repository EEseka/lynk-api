package com.eeseka.lynk.payment.service

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.PaymentState
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.service.HangoutParticipantService
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.payment.domain.exception.PaymentIllegalStateException
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.domain.model.ProviderTransactionStatus
import com.eeseka.lynk.payment.domain.model.RefundStatus
import com.eeseka.lynk.payment.infra.database.entities.PaymentEntity
import com.eeseka.lynk.payment.infra.database.repositories.HangoutPayoutAccountRepository
import com.eeseka.lynk.payment.infra.database.repositories.PaymentRepository
import com.eeseka.lynk.payment.infra.paystack.PaystackClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class PaymentReconciliationService(
    private val paymentRepository: PaymentRepository,
    private val hangoutPayoutAccountRepository: HangoutPayoutAccountRepository,
    private val paystackClient: PaystackClient,
    private val paymentConfirmationService: PaymentConfirmationService,
    private val payoutService: PayoutService,
    private val refundService: RefundService,
    private val hangoutService: HangoutService,
    private val hangoutParticipantService: HangoutParticipantService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val STALE_AFTER: Duration = Duration.ofMinutes(30L)
        private val GIVE_UP_AFTER: Duration = Duration.ofDays(1L)
        private val REFUND_RECOVERY_WINDOW: Duration = Duration.ofDays(1L)
    }

    @Scheduled(fixedDelay = 15 * 60 * 1000)
    fun reconcileStalePayments() {
        val stalePayments = paymentRepository.findAllByStatusAndCreatedAtBefore(
            status = PaymentStatus.PENDING,
            before = Instant.now().minus(STALE_AFTER)
        )

        stalePayments.forEach { payment ->
            try {
                reconcile(payment)
            } catch (e: RuntimeException) {
                logger.error("Could not reconcile payment {}: {}", payment.reference, e.message)
            }
        }

        if (stalePayments.isNotEmpty()) {
            logger.info("Re-checked {} pending payments against Paystack", stalePayments.size)
        }
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000)
    fun recoverRefundsNeverRequested() {
        val settled = paymentRepository.findAllByStatusAndRefundStatusAndPaidAtAfter(
            status = PaymentStatus.SUCCESS,
            refundStatus = RefundStatus.NONE,
            paidAt = Instant.now().minus(REFUND_RECOVERY_WINDOW)
        )

        settled.forEach { payment ->
            try {
                recoverRefund(payment)
            } catch (e: RuntimeException) {
                logger.error("Could not check whether {} is owed back: {}", payment.reference, e.message)
            }
        }

        if (settled.isNotEmpty()) {
            logger.info("Re-checked {} recent payments for refunds that were never requested", settled.size)
        }
    }

    @Scheduled(fixedDelay = 15 * 60 * 1000)
    fun reconcileStalePayouts() {
        val hangoutIds = hangoutService.findHangoutIdsPayingOut()

        hangoutIds.forEach { hangoutId ->
            try {
                reconcilePayout(hangoutId)
            } catch (e: RuntimeException) {
                logger.error("Could not reconcile the payout for hangout {}: {}", hangoutId, e.message)
            }
        }

        if (hangoutIds.isNotEmpty()) {
            logger.info("Re-checked {} payouts still in flight against Paystack", hangoutIds.size)
        }
    }

    private fun reconcile(payment: PaymentEntity) {
        val transaction = paystackClient.verifyTransaction(payment.reference)
        val isTooOldToWaitFor = payment.createdAt.isBefore(Instant.now().minus(GIVE_UP_AFTER))

        when {
            transaction.status == ProviderTransactionStatus.SUCCEEDED -> {
                paymentConfirmationService.confirm(
                    reference = payment.reference,
                    paidAmountKobo = transaction.paidAmountKobo
                )
            }

            transaction.status == ProviderTransactionStatus.FAILED -> {
                paymentRepository.save(
                    payment.apply {
                        status = PaymentStatus.FAILED
                        failureReason = "The provider reported the payment as failed"
                    }
                )
            }

            isTooOldToWaitFor -> {
                paymentRepository.save(
                    payment.apply { status = PaymentStatus.ABANDONED }
                )
                logger.info("Payment {} was never completed, marking it abandoned", payment.reference)
            }

            else -> Unit
        }
    }

    private fun recoverRefund(payment: PaymentEntity) {
        val paymentState = hangoutService.findPaymentState(payment.hangoutId)
        if (paymentState == PaymentState.PAYING_OUT || paymentState == PaymentState.PAID_OUT) return

        if (hangoutService.findHangoutStatus(payment.hangoutId) == HangoutStatus.CANCELLED) {
            logger.warn("Payment {} is held for a cancelled hangout, sending it back", payment.reference)
            refundService.refundPaymentByReference(payment.reference)
            return
        }

        val isStillAttending = hangoutParticipantService.findRsvpStatus(
            hangoutId = payment.hangoutId,
            userId = payment.userId
        ) == RsvpStatus.ATTENDING
        if (!isStillAttending) {
            logger.warn("Payment {} is held for someone no longer attending, sending it back", payment.reference)
            refundService.refundPaymentByReference(payment.reference)
        }
    }

    private fun reconcilePayout(hangoutId: HangoutId) {
        val payoutAccount = hangoutPayoutAccountRepository.findByHangoutId(hangoutId)
            ?: throw PaymentIllegalStateException("This hangout has no payout account.")

        val transferReference = payoutAccount.transferReference
        if (transferReference == null) {
            logger.error("Hangout {} is paying out but has no transfer reference, releasing it", hangoutId)
            hangoutService.recordPayoutOutcome(
                hangoutId = hangoutId,
                succeeded = false,
                reference = null,
                amountKobo = paymentRepository.sumNetAmountByHangoutIdAndStatusAndRefundStatus(
                    hangoutId = hangoutId,
                    status = PaymentStatus.SUCCESS,
                    refundStatus = RefundStatus.NONE
                )
            )
            return
        }

        val transfer = paystackClient.verifyTransfer(transferReference)

        when (transfer.status) {
            ProviderTransactionStatus.SUCCEEDED -> payoutService.applyTransferOutcome(
                transferReference = transferReference,
                succeeded = true,
                reason = null
            )

            ProviderTransactionStatus.FAILED -> payoutService.applyTransferOutcome(
                transferReference = transferReference,
                succeeded = false,
                reason = "The provider reported the transfer as failed"
            )

            // Still moving, or Paystack would not say. Either way it is not ours to resolve yet.
            ProviderTransactionStatus.PENDING, ProviderTransactionStatus.UNKNOWN -> Unit
        }
    }
}