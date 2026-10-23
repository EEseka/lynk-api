package com.eeseka.lynk.lobby.api.mappers

import com.eeseka.lynk.lobby.api.dto.ws.outbound.VotingSnapshotDto
import com.eeseka.lynk.lobby.domain.model.VotingSnapshot
import com.eeseka.lynk.spot.api.mappers.toSpotDto

fun VotingSnapshot.toVotingSnapshotDto(): VotingSnapshotDto {
    return VotingSnapshotDto(
        hangoutId = hangoutId,
        candidates = candidates.map { it.toSpotDto() },
        votes = votes,
        latitude = centerLatitude,
        longitude = centerLongitude
    )
}
