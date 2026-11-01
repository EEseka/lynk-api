package com.eeseka.lynk.payment.api.dto

data class BankAccountDto(
    val accountNumber: String,
    val accountHolderName: String,
    val bankCode: String
)