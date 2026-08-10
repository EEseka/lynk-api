package com.eeseka.lynk.payment.service

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.payment.domain.exception.PaymentIllegalStateException
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.domain.model.ProviderTransactionStatus
import com.eeseka.lynk.payment.infra.database.repositories.PaymentRepository
import com.eeseka.lynk.payment.infra.paystack.PaystackClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PaymentVerificationService(
    private val paymentRepository: PaymentRepository,
    private val paystackClient: PaystackClient,
    private val paymentConfirmationService: PaymentConfirmationService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Not transactional: it calls Paystack, and a transaction held across that would pin a database
     * connection for the length of the round trip. Every step that writes opens its own.
     */
    fun verifyLatestPayment(userId: UserId, hangoutId: HangoutId): PaymentStatus {
        val alreadyPaid = paymentRepository.existsByHangoutIdAndUserIdAndStatus(
            hangoutId = hangoutId,
            userId = userId,
            status = PaymentStatus.SUCCESS
        )
        if (alreadyPaid) return PaymentStatus.SUCCESS

        val pending = paymentRepository
            .findAllByHangoutIdAndUserIdAndStatus(
                hangoutId = hangoutId,
                userId = userId,
                status = PaymentStatus.PENDING
            )
            .maxByOrNull { it.createdAt }
            ?: throw PaymentIllegalStateException("There is no payment to check for this hangout.")

        val transaction = paystackClient.verifyTransaction(pending.reference)

        return when (transaction.status) {
            ProviderTransactionStatus.SUCCEEDED -> confirmAndReadStatus(pending.reference, transaction.paidAmountKobo)

            ProviderTransactionStatus.FAILED -> {
                failPayment(pending.reference)
                PaymentStatus.FAILED
            }

            // Paystack is still working on it or would not say. Either way there is no answer to give yet,
            // and the reconciliation sweep will settle it if the guest walks away now.
            ProviderTransactionStatus.PENDING, ProviderTransactionStatus.UNKNOWN -> PaymentStatus.PENDING
        }
    }


    private fun confirmAndReadStatus(reference: String, paidAmountKobo: Long?): PaymentStatus {
        paymentConfirmationService.confirm(reference = reference, paidAmountKobo = paidAmountKobo)

        return paymentRepository.findByReference(reference)?.status ?: PaymentStatus.PENDING
    }

    private fun failPayment(reference: String) {
        val payment = paymentRepository.findByReference(reference) ?: return

        logger.info("Payment {} was reported failed when its payer checked on it", reference)

        paymentRepository.save(
            payment.apply {
                status = PaymentStatus.FAILED
                failureReason = "The provider reported the payment as failed"
            }
        )
    }
}