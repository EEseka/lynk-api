package com.eeseka.lynk.hangout.domain.model

import java.time.Instant

data class HangoutParticipant(
    val user: HangoutUser,
    val rsvpStatus: RsvpStatus,
    val hasPaid: Boolean,
    val arrivedAt: Instant?
)