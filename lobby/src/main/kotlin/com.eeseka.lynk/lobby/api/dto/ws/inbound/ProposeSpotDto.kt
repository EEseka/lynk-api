package com.eeseka.lynk.lobby.api.dto.ws.inbound

import com.eeseka.lynk.common.domain.type.HangoutId

data class ProposeSpotDto(
    val hangoutId: HangoutId,
    val spotId: String
)