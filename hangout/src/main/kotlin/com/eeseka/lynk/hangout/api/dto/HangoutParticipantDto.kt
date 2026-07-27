package com.eeseka.lynk.hangout.api.dto

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.model.RsvpStatus

data class HangoutParticipantDto(
    val userId: UserId,
    val username: String,
    val displayName: String,
    val profilePictureUrl: String?,
    val rsvpStatus: RsvpStatus,
    val hasPaid: Boolean
)