package com.eeseka.lynk.user.api.dto

import jakarta.validation.constraints.NotBlank

data class GoogleLoginRequest(
    @field:NotBlank(message = "Token is required")
    val token: String
)