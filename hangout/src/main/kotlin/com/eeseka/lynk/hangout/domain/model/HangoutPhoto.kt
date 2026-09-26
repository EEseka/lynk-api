package com.eeseka.lynk.hangout.domain.model

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.HangoutPhotoId
import java.time.Instant

data class HangoutPhoto(
    val id: HangoutPhotoId,
    val hangoutId: HangoutId,
    val uploader: HangoutUser,
    val status: HangoutPhotoStatus,
    val caption: String?,
    val createdAt: Instant
)
