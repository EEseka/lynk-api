package com.eeseka.lynk.payment.domain.model

data class ProviderTransaction(
    val status: ProviderTransactionStatus,
    val paidAmountKobo: Long?
)