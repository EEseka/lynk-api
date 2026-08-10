package com.eeseka.lynk.payment.api.dto

import com.eeseka.lynk.payment.domain.model.DeadlineDecision
import jakarta.validation.constraints.Future
import java.time.Instant

data class DeadlineDecisionRequest(
    val decision: DeadlineDecision,
    @field:Future(message = "New payment deadline must be in the future")
    val newDeadline: Instant? = null
)