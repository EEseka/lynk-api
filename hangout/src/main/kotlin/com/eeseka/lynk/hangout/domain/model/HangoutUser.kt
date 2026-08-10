package com.eeseka.lynk.hangout.domain.model

import com.eeseka.lynk.common.domain.type.UserId

data class HangoutUser(
    val userId: UserId,
    val email: String,
    val username: String,
    val displayName: String,
    val profilePictureUrl: String?
)