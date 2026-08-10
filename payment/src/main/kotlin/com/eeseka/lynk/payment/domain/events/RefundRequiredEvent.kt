package com.eeseka.lynk.payment.domain.events

data class RefundRequiredEvent(
    val reference: String,
    val reason: String
)
