package com.eeseka.lynk.payment.api.dto

data class PayoutAccountDto(
    val bankName: String?,
    val accountNumberLast4: String,
    val accountHolderName: String
)
