package com.eeseka.lynk.payment.domain.model

import com.eeseka.lynk.common.domain.type.HangoutId
import java.time.Instant

data class PaymentSettings(
    val hangoutId: HangoutId,
    val totalCostKobo: Long,
    val costPerPersonKobo: Long,
    val paymentDeadline: Instant,
    val bankName: String?,
    val accountNumberLast4: String,
    val accountHolderName: String
)