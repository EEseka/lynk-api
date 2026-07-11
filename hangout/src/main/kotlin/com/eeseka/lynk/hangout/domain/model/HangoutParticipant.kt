package com.eeseka.lynk.hangout.domain.model

import com.eeseka.lynk.common.domain.type.UserId

data class HangoutParticipant(
    val userId: UserId,
    val username: String,
    val displayName: String,
    val profilePictureUrl: String?,

    // Hangout-specific data
    val rsvpStatus: RsvpStatus,
    val hasPaid: Boolean // For the Paystack integration
)