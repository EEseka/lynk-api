package com.eeseka.lynk.payment.service

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.payment.domain.exception.PaymentIllegalStateException
import com.eeseka.lynk.payment.domain.exception.PayoutRejectedException
import com.eeseka.lynk.payment.domain.exception.PaystackUnavailableException
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.domain.model.PayoutStatus
import com.eeseka.lynk.payment.infra.database.entities.HangoutPayoutAccountEntity
import com.eeseka.lynk.payment.infra.database.entities.PayoutEntity
import com.eeseka.lynk.payment.infra.database.repositories.HangoutPayoutAccountRepository
import com.eeseka.lynk.payment.infra.database.repositories.PaymentRepository
import com.eeseka.lynk.payment.infra.database.repositories.PayoutRepository
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
    private val payoutRepository: PayoutRepository,
    private val paystackClient: PaystackClient,
    private val hangoutService: HangoutService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val STUCK_PAYOUT_AFTER: Duration = Duration.ofDays(1L)
    }

    @Transactional
    fun applyTransferOutcome(transferReference: String, succeeded: Boolean, reason: String?) {
        val payout = payoutRepository.findByReference(transferReference)
        if (payout == null) {
            logger.warn("Transfer outcome for a reference we do not know: {}", transferReference)
            return
        }
        if (payout.status != PayoutStatus.IN_FLIGHT) {
            logger.info("Transfer {} was already recorded as {}, nothing to do", transferReference, payout.status)
            return
        }

        payoutRepository.save(
            payout.apply {
                status = if (succeeded) PayoutStatus.SUCCEEDED else PayoutStatus.FAILED
                if (!succeeded) failureReason = reason?.take(255) ?: "The transfer did not go through"
                settledAt = Instant.now()
            }
        )

        if (succeeded) {
            hangoutPayoutAccountRepository.findByHangoutId(payout.hangoutId)?.let { payoutAccount ->
                hangoutPayoutAccountRepository.save(payoutAccount.apply { eraseBankDetails() })
            }
        }

        hangoutService.recordPayoutOutcome(
            hangoutId = payout.hangoutId,
            succeeded = succeeded,
            reference = transferReference,
            amountKobo = payout.amountKobo
        )
    }

    fun retryPayout(hostId: UserId, hangoutId: HangoutId) {
        hangoutService.retryPayout(hostId = hostId, hangoutId = hangoutId)
    }

    // A canceled hangout is never paid out, so the host's bank details have nothing left to do.
    @Transactional
    fun erasePayoutAccount(hangoutId: HangoutId) {
        val payoutAccount = hangoutPayoutAccountRepository.findByHangoutId(hangoutId) ?: return

        hangoutPayoutAccountRepository.save(
            payoutAccount.apply { eraseBankDetails() }
        )
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000)
    fun reportPayoutsStillFailed() {
        val cutoff = Instant.now().minus(STUCK_PAYOUT_AFTER)

        hangoutService.findHangoutIdsWithPayoutFailed().forEach { hangoutId ->
            val failedPayout = payoutRepository.findFirstByHangoutIdAndStatusOrderByCreatedAtDesc(
                hangoutId = hangoutId,
                status = PayoutStatus.FAILED
            ) ?: run {
                logger.error("Hangout {} has a failed payout but no failed transfer recorded", hangoutId)
                return@forEach
            }
            val failedAt = failedPayout.settledAt ?: failedPayout.createdAt
            if (failedAt.isAfter(cutoff)) return@forEach

            logger.error(
                "Payout for hangout {} was refused at {} and is still unpaid - reason: {} - host: {}",
                hangoutId,
                failedAt,
                failedPayout.failureReason ?: "not recorded",
                hangoutPayoutAccountRepository.findByHangoutId(hangoutId)?.hostId ?: "unknown"
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
            hangoutPayoutAccountRepository.save(
                payoutAccount.apply { eraseBankDetails() }
            )
            return
        }

        // A sweep that stopped after recording the transfer but before marking the hangout left it in flight,
        // and Paystack takes the same reference only once, so it is sent again as it was
        val payout = payoutRepository.findByHangoutIdAndStatus(hangoutId, PayoutStatus.IN_FLIGHT)
            ?: payoutRepository.save(
                PayoutEntity(
                    hangoutId = hangoutId,
                    reference = "lynk_payout_${UUID.randomUUID()}",
                    amountKobo = collectedKobo
                )
            )
        hangoutService.markPayoutInFlight(hangoutId)

        try {
            paystackClient.transfer(
                recipientCode = payoutAccount.recipientCode,
                amountKobo = payout.amountKobo,
                reference = payout.reference,
                reason = "Lynk hangout payout"
            )
        } catch (e: PayoutRejectedException) {
            // These two writes are not one transaction and do not need to be. If the second never runs,
            // the hangout stays PAYING_OUT with no transfer in flight, which is the exact shape
            // reconcilePayout looks for and releases.
            payoutRepository.save(
                payout.apply {
                    status = PayoutStatus.FAILED
                    failureReason = e.message?.take(255)
                    settledAt = Instant.now()
                }
            )
            hangoutService.recordPayoutOutcome(
                hangoutId = hangoutId,
                succeeded = false,
                reference = payout.reference,
                amountKobo = payout.amountKobo
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

        logger.info("Payout of {} kobo accepted for hangout {}", payout.amountKobo, hangoutId)
    }

    private fun collectedKoboFor(hangoutId: HangoutId): Long =
        paymentRepository.sumNetAmountByHangoutIdAndStatus(
            hangoutId = hangoutId,
            status = PaymentStatus.SUCCESS
        )

    // Keeps the row, but nothing that identifies the host's bank account.
    private fun HangoutPayoutAccountEntity.eraseBankDetails() {
        recipientCode = ""
        bankName = null
        accountNumberLast4 = ""
        accountHolderName = ""
    }
}
