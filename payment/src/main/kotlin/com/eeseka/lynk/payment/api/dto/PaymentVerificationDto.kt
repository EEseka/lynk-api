package com.eeseka.lynk.payment.api.dto

import com.eeseka.lynk.payment.domain.model.PaymentStatus

data class PaymentVerificationDto(
    val status: PaymentStatus
)