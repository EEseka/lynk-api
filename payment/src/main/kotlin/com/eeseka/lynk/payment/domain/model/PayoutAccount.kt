package com.eeseka.lynk.payment.domain.model

data class PayoutAccount(
    val bankName: String?,
    val accountNumberLast4: String,
    val accountHolderName: String
)
