package com.eeseka.lynk.user.api.dto

import jakarta.validation.constraints.NotBlank

data class GenerateProfilePictureUploadUrlRequest(
    @field:NotBlank(message = "Mime type is required")
    val mimeType: String
)