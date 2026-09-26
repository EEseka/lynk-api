package com.eeseka.lynk.hangout.api.dto

import org.hibernate.validator.constraints.Length

data class ConfirmHangoutPhotoUploadRequest(
    @field:Length(max = 200, message = "Caption cannot exceed 200 characters")
    val caption: String?
)
