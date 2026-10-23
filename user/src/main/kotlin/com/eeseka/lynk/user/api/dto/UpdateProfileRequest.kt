package com.eeseka.lynk.user.api.dto

import com.eeseka.lynk.user.domain.ProfileConstants.MAX_DISPLAY_NAME_LENGTH
import jakarta.validation.constraints.NotBlank
import org.hibernate.validator.constraints.Length

data class UpdateProfileRequest(
    @field:NotBlank(message = "Display name cannot be blank")
    @field:Length(
        min = 1,
        max = MAX_DISPLAY_NAME_LENGTH,
        message = "Display name must be between 1 and $MAX_DISPLAY_NAME_LENGTH characters"
    )
    val displayName: String,
    val profilePhotoUrl: String?
)