package com.eeseka.lynk.hangout.domain.model

import com.eeseka.lynk.common.domain.type.HangoutPhotoId
import java.time.Instant

data class HangoutPhotoUploadCredentials(
    val photoId: HangoutPhotoId,
    val fullUploadUrl: String,
    val thumbnailUploadUrl: String,
    val headers: Map<String, String>,
    val expiresAt: Instant
)
