package com.eeseka.lynk.lobby.api.dto.ws.outbound

import com.eeseka.lynk.common.domain.type.HangoutId

data class LobbyPayoutDto(
    val hangoutId: HangoutId,
    val succeeded: Boolean
)