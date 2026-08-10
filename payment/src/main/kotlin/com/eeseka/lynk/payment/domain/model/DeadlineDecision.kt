package com.eeseka.lynk.payment.domain.model

enum class DeadlineDecision {
    EXTEND,
    REMOVE_NON_PAYERS,
    PROCEED_ANYWAY,
    CANCEL
}