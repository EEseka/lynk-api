package com.eeseka.lynk.hangout.api.dto

import com.eeseka.lynk.common.domain.type.UserId

data class HangoutParticipantDto(
    val userId: UserId,
    val username: String,
    val displayName: String,
    val profilePictureUrl: String?,
    val rsvpStatus: String,
    val hasPaid: Boolean
)