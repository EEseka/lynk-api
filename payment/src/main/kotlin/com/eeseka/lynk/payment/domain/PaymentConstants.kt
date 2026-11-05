package com.eeseka.lynk.payment.domain

object PaymentConstants {
    const val NUBAN_LENGTH = 10
    const val ACCOUNT_NUMBER_PATTERN = "\\d{$NUBAN_LENGTH}"
}