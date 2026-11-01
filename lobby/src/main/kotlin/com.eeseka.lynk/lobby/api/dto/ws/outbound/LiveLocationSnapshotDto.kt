package com.eeseka.lynk.lobby.api.dto.ws.outbound

import com.eeseka.lynk.common.domain.type.HangoutId

// Outbound LIVE_LOCATION_SNAPSHOT: everyone sharing right now, replacing whatever the client held.
data class LiveLocationSnapshotDto(
    val hangoutId: HangoutId,
    val locations: List<LiveLocationDto>
)
