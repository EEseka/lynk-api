package com.eeseka.lynk.hangout.api.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class GenerateHangoutPhotoUploadUrlsRequest(
    @field:Min(value = 1, message = "Add at least 1 photo")
    @field:Max(value = 10, message = "Add at most 10 photos at a time")
    val count: Int
)
