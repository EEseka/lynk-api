package com.eeseka.lynk.lobby.api.dto.ws.outbound

import com.eeseka.lynk.common.domain.type.HangoutId

// Payload for a change nobody made: which hangout to re-fetch, and no name to put on it
data class LobbyHangoutDto(
    val hangoutId: HangoutId
)
