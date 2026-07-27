package com.eeseka.lynk.lobby.api.dto.ws.inbound

import com.eeseka.lynk.common.domain.type.HangoutId

data class CastVoteDto(
    val hangoutId: HangoutId,
    val spotId: String
)