package com.eeseka.lynk.hangout.domain.event

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.model.RsvpStatus

data class HangoutRsvpUpdatedEvent(
    val hangoutId: HangoutId,
    val userId: UserId,
    val displayName: String,
    val rsvpStatus: RsvpStatus
)