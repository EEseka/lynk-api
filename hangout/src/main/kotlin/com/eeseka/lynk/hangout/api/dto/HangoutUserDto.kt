package com.eeseka.lynk.hangout.api.dto

import com.eeseka.lynk.common.domain.type.UserId

data class HangoutUserDto(
    val userId: UserId,
    val username: String,
    val displayName: String,
    val profilePictureUrl: String?
)
