package com.eeseka.lynk.payment.api.dto

import com.eeseka.lynk.payment.domain.PaymentConstants.ACCOUNT_NUMBER_PATTERN
import com.eeseka.lynk.payment.domain.PaymentConstants.NUBAN_LENGTH
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class ChangePayoutAccountRequest(
    @field:Pattern(regexp = ACCOUNT_NUMBER_PATTERN, message = "An account number must be $NUBAN_LENGTH digits")
    val accountNumber: String,
    @field:NotBlank(message = "Bank cannot be blank")
    val bankCode: String
)
