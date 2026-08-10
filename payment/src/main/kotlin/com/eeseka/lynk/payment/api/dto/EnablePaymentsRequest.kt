package com.eeseka.lynk.payment.api.dto

import jakarta.validation.constraints.Future
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.Instant

data class EnablePaymentsRequest(
    @field:Min(value = 1, message = "Total cost must be more than zero")
    val totalCostKobo: Long,
    @field:Future(message = "Payment deadline must be in the future")
    val paymentDeadline: Instant,
    @field:Pattern(regexp = "\\d{10}", message = "An account number must be 10 digits")
    val accountNumber: String,
    @field:NotBlank(message = "Bank cannot be blank")
    val bankCode: String
)