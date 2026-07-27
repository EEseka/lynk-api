package com.eeseka.lynk.lobby.api.dto.ws.outbound

import com.eeseka.lynk.common.domain.type.HangoutId

// Payload for host actions (updated / completed / canceled): which hangout and who did it
data class LobbyHostActionDto(
    val hangoutId: HangoutId,
    val hostDisplayName: String
)