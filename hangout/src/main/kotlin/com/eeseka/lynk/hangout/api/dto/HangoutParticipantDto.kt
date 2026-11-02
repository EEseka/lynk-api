package com.eeseka.lynk.hangout.api.dto

import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import java.time.Instant

data class HangoutParticipantDto(
    val user: HangoutUserDto,
    val rsvpStatus: RsvpStatus,
    val hasPaid: Boolean,
    val arrivedAt: Instant?
)
