package com.eeseka.lynk.user.api.dto

import com.eeseka.lynk.common.domain.ValidationPatterns.SINGLE_LINE
import com.eeseka.lynk.user.domain.ProfileConstants.MAX_DISPLAY_NAME_LENGTH
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.hibernate.validator.constraints.Length

data class UpdateProfileRequest(
    @field:NotBlank(message = "Display name cannot be blank")
    @field:Length(
        min = 1,
        max = MAX_DISPLAY_NAME_LENGTH,
        message = "Display name must be between 1 and $MAX_DISPLAY_NAME_LENGTH characters"
    )
    @field:Pattern(regexp = SINGLE_LINE, message = "Display name must be on one line")
    val displayName: String,
    val profilePhotoUrl: String?
)