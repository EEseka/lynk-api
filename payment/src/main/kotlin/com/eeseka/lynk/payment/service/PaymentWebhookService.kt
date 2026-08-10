package com.eeseka.lynk.payment.service

import com.eeseka.lynk.payment.domain.events.PaymentProviderEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PaymentWebhookService(
    private val paymentConfirmationService: PaymentConfirmationService,
    private val refundService: RefundService,
    private val payoutService: PayoutService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Only routes. Each handler below opens its own transaction, and this deliberately does not wrap
     * them in an outer one: some of them go on to call Paystack, and a transaction spanning an HTTP
     * round trip holds a database connection for as long as Paystack takes to answer.
     */
    fun handle(event: PaymentProviderEvent) {
        when (event) {
            is PaymentProviderEvent.ChargeSucceeded -> paymentConfirmationService.confirm(
                reference = event.reference,
                paidAmountKobo = event.paidAmountKobo
            )

            is PaymentProviderEvent.RefundSettled -> refundService.applyRefundOutcome(
                transactionReference = event.transactionReference,
                succeeded = event.succeeded
            )

            is PaymentProviderEvent.TransferSettled -> payoutService.applyTransferOutcome(
                transferReference = event.transferReference,
                succeeded = event.succeeded,
                reason = event.reason
            )

            is PaymentProviderEvent.Ignored -> logger.info("Ignoring {}", event.reason)
        }
    }
}