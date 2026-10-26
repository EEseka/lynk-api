package com.eeseka.lynk.payment.domain.model

enum class PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    ABANDONED,
    REFUNDING, // A refund is on its way back to the payer.
    REFUND_FAILED, // The last refund was refused, and the retry sweep will send it again.
    REFUNDED
}
