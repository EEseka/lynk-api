package com.eeseka.lynk.payment.api.dto

import jakarta.validation.constraints.Future
import java.time.Instant

data class ChangeDeadlineRequest(
    @field:Future(message = "Payment deadline must be in the future")
    val newDeadline: Instant
)