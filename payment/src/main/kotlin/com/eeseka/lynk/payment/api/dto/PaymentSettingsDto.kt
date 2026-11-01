package com.eeseka.lynk.payment.api.dto

import com.eeseka.lynk.common.domain.type.HangoutId
import java.time.Instant

data class PaymentSettingsDto(
    val hangoutId: HangoutId,
    val totalCostKobo: Long,
    val costPerPersonKobo: Long,
    val paymentDeadline: Instant,
    val payoutAccount: PayoutAccountDto
)