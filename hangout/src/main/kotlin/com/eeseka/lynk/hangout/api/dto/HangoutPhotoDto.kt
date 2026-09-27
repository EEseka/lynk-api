package com.eeseka.lynk.hangout.api.dto

import com.eeseka.lynk.common.domain.type.HangoutPhotoId
import java.time.Instant

data class HangoutPhotoDto(
    val id: HangoutPhotoId,
    val uploader: HangoutUserDto,
    val caption: String?,
    val fullUrl: String,
    val thumbnailUrl: String,
    val urlsExpireAt: Instant,
    val createdAt: Instant
)
