package com.eeseka.lynk.hangout.domain.model

import java.time.Instant

data class HangoutPhotoDownloadUrls(
    val fullUrl: String,
    val thumbnailUrl: String,
    val expiresAt: Instant
)
