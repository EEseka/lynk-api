package com.eeseka.lynk.payment.domain.model

data class BankAccount(
    val accountNumber: String,
    val accountHolderName: String,
    val bankCode: String
)