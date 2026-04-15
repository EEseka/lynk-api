package com.eeseka.lynk.user.api.mappers

import com.eeseka.lynk.user.api.dto.ProfilePictureUploadResponse
import com.eeseka.lynk.user.domain.model.ProfilePictureUploadCredentials

fun ProfilePictureUploadCredentials.toResponse(): ProfilePictureUploadResponse {
    return ProfilePictureUploadResponse(
        uploadUrl = uploadUrl,
        publicUrl = publicUrl,
        headers = headers,
        expiresAt = expiresAt
    )
}