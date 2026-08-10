package com.eeseka.lynk.lobby.api.dto.ws.outbound

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId

// Payload for invited / withdrawn / left; e.t.c: who, in which hangout, and their name
data class LobbyParticipantDto(
    val hangoutId: HangoutId,
    val userId: UserId,
    val displayName: String
)