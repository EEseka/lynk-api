package com.eeseka.lynk.hangout.domain.model

data class HangoutParticipant(
    val user: HangoutUser,
    val rsvpStatus: RsvpStatus,
    val hasPaid: Boolean
)