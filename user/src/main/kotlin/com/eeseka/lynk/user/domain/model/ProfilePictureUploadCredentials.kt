package com.eeseka.lynk.user.domain.model

import java.time.Instant

data class ProfilePictureUploadCredentials(
    val uploadUrl: String,
    val publicUrl: String,
    val headers: Map<String, String>,
    val expiresAt: Instant
)