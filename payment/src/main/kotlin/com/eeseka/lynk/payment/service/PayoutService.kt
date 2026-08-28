package com.eeseka.lynk.payment.service

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.payment.domain.exception.PaymentIllegalStateException
import com.eeseka.lynk.payment.domain.exception.PayoutRejectedException
import com.eeseka.lynk.payment.domain.exception.PaystackUnavailableException
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.domain.model.RefundStatus
import com.eeseka.lynk.payment.infra.database.repositories.HangoutPayoutAccountRepository
import com.eeseka.lynk.payment.infra.database.repositories.PaymentRepository
import com.eeseka.lynk.payment.infra.paystack.PaystackClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.*

@Service
class PayoutService(
    private val paymentRepository: PaymentRepository,
    private val hangoutPayoutAccountRepository: HangoutPayoutAccountRepository,
    private val paystackClient: PaystackClient,
    private val hangoutService: HangoutService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val STUCK_PAYOUT_AFTER: Duration = Duration.ofDays(1L)
    }

    @Transactional
    fun applyTransferOutcome(transferReference: String, succeeded: Boolean, reason: String?) {
        val payoutAccount = hangoutPayoutAccountRepository.findByTransferReference(transferReference)
        if (payoutAccount == null) {
            logger.warn("Transfer outcome for a reference we do not know: {}", transferReference)
            return
        }

        hangoutPayoutAccountRepository.save(
            payoutAccount.apply {
                if (succeeded) {
                    paidOutAt = Instant.now()
                    payoutFailureReason = null
                    recipientCode = ""
                    bankName = null
                    accountNumberLast4 = ""
                    accountHolderName = ""
                } else {
                    payoutFailureReason = reason?.take(255) ?: "The transfer did not go through"
                    this.transferReference = null
                }
            }
        )

        hangoutService.recordPayoutOutcome(
            hangoutId = payoutAccount.hangoutId,
            succeeded = succeeded,
            reference = transferReference,
            amountKobo = collectedKoboFor(payoutAccount.hangoutId)
        )
    }

    fun retryPayout(hostId: UserId, hangoutId: HangoutId) {
        hangoutService.retryPayout(hostId = hostId, hangoutId = hangoutId)
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000)
    fun reportPayoutsStillFailed() {
        val cutoff = Instant.now().minus(STUCK_PAYOUT_AFTER)

        hangoutService.findHangoutIdsWithPayoutFailed().forEach { hangoutId ->
            val payoutAccount = hangoutPayoutAccountRepository.findByHangoutId(hangoutId) ?: run {
                logger.error("Hangout {} has a failed payout but no payout account at all", hangoutId)
                return@forEach
            }
            if (payoutAccount.updatedAt.isAfter(cutoff)) return@forEach

            logger.error(
                "Payout for hangout {} was refused at {} and is still unpaid - reason: {} - host: {}",
                hangoutId,
                payoutAccount.updatedAt,
                payoutAccount.payoutFailureReason ?: "not recorded",
                payoutAccount.hostId
            )
        }
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    fun payHostsWhoAreDue() {
        val hangoutIds = hangoutService.findHangoutIdsReadyForPayout()

        hangoutIds.forEach { hangoutId ->
            // One failure must not stop the rest: these are unrelated hangouts and unrelated hosts.
            try {
                payHost(hangoutId)
            } catch (e: RuntimeException) {
                logger.error("Payout sweep failed for hangout {}: {}", hangoutId, e.message)
            }
        }
    }

    private fun payHost(hangoutId: HangoutId) {
        val payoutAccount = hangoutPayoutAccountRepository.findByHangoutId(hangoutId)
            ?: throw PaymentIllegalStateException("This hangout has no payout account.")

        val collectedKobo = collectedKoboFor(hangoutId)

        if (collectedKobo <= 0) {
            logger.info("Nothing collected for hangout {}, marking it paid out", hangoutId)
            hangoutService.recordPayoutOutcome(
                hangoutId = hangoutId,
                succeeded = true,
                reference = null,
                amountKobo = 0
            )
            return
        }

        val transferReference = payoutAccount.transferReference ?: "lynk_payout_${UUID.randomUUID()}"

        hangoutPayoutAccountRepository.save(
            payoutAccount.apply { this.transferReference = transferReference }
        )
        hangoutService.markPayoutInFlight(hangoutId)

        try {
            paystackClient.transfer(
                recipientCode = payoutAccount.recipientCode,
                amountKobo = collectedKobo,
                reference = transferReference,
                reason = "Lynk hangout payout"
            )
        } catch (e: PayoutRejectedException) {
            // These two writes are not one transaction and do not need to be. If the second never
            // runs, the hangout stays PAYING_OUT with no transfer reference, which is the exact shape
            // reconcilePayout looks for and releases.
            hangoutPayoutAccountRepository.save(
                payoutAccount.apply {
                    payoutFailureReason = e.message?.take(255)
                    // Paystack will not accept the same reference twice, so a retry that reused this one
                    // would be refused for that reason alone and could never get through.
                    this.transferReference = null
                }
            )
            hangoutService.recordPayoutOutcome(
                hangoutId = hangoutId,
                succeeded = false,
                reference = transferReference,
                amountKobo = collectedKobo
            )

            logger.error("Payout for hangout {} was refused: {}", hangoutId, e.message)
            return
        } catch (e: PaystackUnavailableException) {
            logger.error(
                "Payout for hangout {} went unanswered, leaving it in flight to be reconciled: {}",
                hangoutId, e.message
            )
            return
        }

        logger.info("Payout of {} kobo accepted for hangout {}", collectedKobo, hangoutId)
    }

    private fun collectedKoboFor(hangoutId: HangoutId): Long =
        paymentRepository.sumNetAmountByHangoutIdAndStatusAndRefundStatus(
            hangoutId = hangoutId,
            status = PaymentStatus.SUCCESS,
            refundStatus = RefundStatus.NONE
        )
}