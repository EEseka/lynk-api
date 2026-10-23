package com.eeseka.lynk.hangout.api.dto

import com.eeseka.lynk.hangout.domain.HangoutConstants.MAX_PHOTOS_PER_UPLOAD
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class GenerateHangoutPhotoUploadUrlsRequest(
    @field:Min(value = 1, message = "Add at least 1 photo")
    @field:Max(value = MAX_PHOTOS_PER_UPLOAD.toLong(), message = "Add at most $MAX_PHOTOS_PER_UPLOAD photos at a time")
    val count: Int
)
