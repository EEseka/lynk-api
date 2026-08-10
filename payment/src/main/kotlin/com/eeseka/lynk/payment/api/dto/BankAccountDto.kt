package com.eeseka.lynk.payment.api.dto

data class BankAccountDto(
    val accountNumber: String,
    val accountName: String,
    val bankCode: String
)