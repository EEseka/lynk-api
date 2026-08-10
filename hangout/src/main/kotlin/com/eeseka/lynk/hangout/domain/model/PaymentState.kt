package com.eeseka.lynk.hangout.domain.model

enum class PaymentState {
    COLLECTING,
    AWAITING_HOST_DECISION,
    READY_FOR_PAYOUT,
    PAYING_OUT,
    PAID_OUT,
    PAYOUT_FAILED
}