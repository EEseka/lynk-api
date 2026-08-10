package com.eeseka.lynk.hangout.domain.model

import java.time.Instant

data class HangoutPayment(
    val totalCostKobo: Long,
    val costPerPersonKobo: Long,
    val splitHeadcount: Int, // How many participants the bill was split between.
    val deadline: Instant,
    val state: PaymentState
)