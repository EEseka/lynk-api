package com.eeseka.lynk.lobby.api.dto.ws.outbound

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId

// The uploaders already have these photos on screen, so their own apps skip the prompt
data class LobbyPhotosAddedDto(
    val hangoutId: HangoutId,
    val uploaderIds: Set<UserId>
)
