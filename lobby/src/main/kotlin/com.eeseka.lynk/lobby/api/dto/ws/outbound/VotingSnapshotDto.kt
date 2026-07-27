package com.eeseka.lynk.lobby.api.dto.ws.outbound

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.spot.api.dto.SpotDto

data class VotingSnapshotDto(
    val hangoutId: HangoutId,
    val candidates: List<SpotDto>,
    val votes: Map<UserId, String>,
    val latitude: Double?,
    val longitude: Double?
)