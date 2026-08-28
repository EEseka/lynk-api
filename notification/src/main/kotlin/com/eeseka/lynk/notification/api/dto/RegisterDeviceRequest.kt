package com.eeseka.lynk.notification.api.dto

import com.eeseka.lynk.notification.domain.model.Platform
import jakarta.validation.constraints.NotBlank

data class RegisterDeviceRequest(
    @field:NotBlank
    val token: String,
    val platform: Platform
)