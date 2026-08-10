package com.eeseka.lynk.payment.domain.model

data class PaymentInitialization(
    val authorizationUrl: String,
    val reference: String,
    val amountKobo: Long,
    val netAmountKobo: Long
)