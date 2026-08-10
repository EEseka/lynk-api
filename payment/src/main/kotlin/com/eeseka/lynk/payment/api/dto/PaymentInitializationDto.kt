package com.eeseka.lynk.payment.api.dto

data class PaymentInitializationDto(
    val authorizationUrl: String,
    val reference: String,
    val amountKobo: Long,
    val netAmountKobo: Long
)