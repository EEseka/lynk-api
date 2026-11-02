package com.eeseka.lynk.lobby.api.dto.ws.inbound

import com.eeseka.lynk.common.domain.type.HangoutId

data class LiveLocationShareDto(
    val hangoutId: HangoutId,
    val latitude: Double,
    val longitude: Double
)
