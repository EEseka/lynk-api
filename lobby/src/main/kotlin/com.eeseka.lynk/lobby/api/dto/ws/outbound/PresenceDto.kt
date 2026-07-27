package com.eeseka.lynk.lobby.api.dto.ws.outbound

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId

// Outbound PRESENCE_UPDATE: the full set of users currently viewing this lobby.
// We send the whole set on every change, so the client just renders the dots.
data class PresenceDto(
    val hangoutId: HangoutId,
    val presentUserIds: Set<UserId>
)