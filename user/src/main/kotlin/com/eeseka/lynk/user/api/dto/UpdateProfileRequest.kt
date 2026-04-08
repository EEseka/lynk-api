package com.eeseka.lynk.user.api.dto

import jakarta.validation.constraints.NotBlank
import org.hibernate.validator.constraints.Length

data class UpdateProfileRequest(
    @field:NotBlank(message = "Username cannot be blank")
    @field:Length(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
    val username: String,
    @field:NotBlank(message = "Display name cannot be blank")
    @field:Length(min = 1, max = 50, message = "Display name must be between 1 and 50 characters")
    val displayName: String,
    val profilePhotoUrl: String?
)