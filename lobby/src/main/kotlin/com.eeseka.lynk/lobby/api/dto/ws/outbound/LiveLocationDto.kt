package com.eeseka.lynk.lobby.api.dto.ws.outbound

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import java.time.Instant

data class LiveLocationDto(
    val hangoutId: HangoutId,
    val userId: UserId,
    val latitude: Double,
    val longitude: Double,
    val sharedAt: Instant
)
