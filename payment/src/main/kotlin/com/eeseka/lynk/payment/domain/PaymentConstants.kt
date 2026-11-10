package com.eeseka.lynk.payment.domain

object PaymentConstants {
    const val NUBAN_LENGTH = 10
    const val ACCOUNT_NUMBER_PATTERN = "\\d{$NUBAN_LENGTH}"
    const val MAX_TOTAL_COST_KOBO = 99_999_999_999L // 999,999,999.99 Naira, the most the app's amount field takes
}