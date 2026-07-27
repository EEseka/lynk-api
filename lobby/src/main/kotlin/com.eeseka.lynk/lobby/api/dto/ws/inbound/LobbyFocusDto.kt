package com.eeseka.lynk.lobby.api.dto.ws.inbound

import com.eeseka.lynk.common.domain.type.HangoutId

// Inbound payload for ENTER_LOBBY / LEAVE_LOBBY: which lobby the socket is now (or no longer) viewing. Drives the green-dot presence.
data class LobbyFocusDto(
    val hangoutId: HangoutId
)