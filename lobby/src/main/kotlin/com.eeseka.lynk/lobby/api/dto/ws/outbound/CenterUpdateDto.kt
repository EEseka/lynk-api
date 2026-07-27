package com.eeseka.lynk.lobby.api.dto.ws.outbound

import com.eeseka.lynk.common.domain.type.HangoutId

data class CenterUpdateDto(
    val hangoutId: HangoutId,
    val latitude: Double,
    val longitude: Double
)