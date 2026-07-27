package com.eeseka.lynk.lobby.api.dto.ws.outbound

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.model.RsvpStatus

// Payload for an RSVP change: adds the new status so the client can show
data class LobbyRsvpDto(
    val hangoutId: HangoutId,
    val userId: UserId,
    val displayName: String,
    val rsvpStatus: RsvpStatus
)