package com.eeseka.lynk.user.api.dto

import com.eeseka.lynk.user.domain.ProfileConstants.MAX_DISPLAY_NAME_LENGTH
import com.eeseka.lynk.user.domain.ProfileConstants.MAX_USERNAME_LENGTH
import com.eeseka.lynk.user.domain.ProfileConstants.MIN_USERNAME_LENGTH
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.hibernate.validator.constraints.Length

data class CreateProfileRequest(
    @field:NotBlank(message = "Username cannot be blank")
    @field:Length(
        min = MIN_USERNAME_LENGTH,
        max = MAX_USERNAME_LENGTH,
        message = "Username must be between $MIN_USERNAME_LENGTH and $MAX_USERNAME_LENGTH characters"
    )
    @field:Pattern(
        regexp = "^(?=.*[a-zA-Z])[a-zA-Z0-9]+(?:_[a-zA-Z0-9]+)*\$",
        message = "Username must contain at least one letter, and can only use numbers and single internal underscores"
    )
    val username: String,
    @field:NotBlank(message = "Display name cannot be blank")
    @field:Length(
        min = 1,
        max = MAX_DISPLAY_NAME_LENGTH,
        message = "Display name must be between 1 and $MAX_DISPLAY_NAME_LENGTH characters"
    )
    val displayName: String,
    val profilePhotoUrl: String?
)