package com.eeseka.lynk.hangout.api.dto

import com.eeseka.lynk.hangout.domain.model.PaymentState
import java.time.Instant

data class HangoutPaymentDto(
    val totalCostKobo: Long,
    val costPerPersonKobo: Long,
    val splitHeadcount: Int,
    val deadline: Instant,
    val state: PaymentState
)