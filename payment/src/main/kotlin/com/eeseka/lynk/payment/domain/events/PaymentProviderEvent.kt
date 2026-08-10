package com.eeseka.lynk.payment.domain.events

sealed interface PaymentProviderEvent {
    data class ChargeSucceeded(
        val reference: String,
        val paidAmountKobo: Long?
    ) : PaymentProviderEvent

    data class RefundSettled(
        val transactionReference: String,
        val succeeded: Boolean
    ) : PaymentProviderEvent

    data class TransferSettled(
        val transferReference: String,
        val succeeded: Boolean,
        val reason: String?
    ) : PaymentProviderEvent

    data class Ignored(
        val reason: String
    ) : PaymentProviderEvent
}