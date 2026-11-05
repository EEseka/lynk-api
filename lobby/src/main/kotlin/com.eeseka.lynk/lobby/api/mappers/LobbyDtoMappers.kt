package com.eeseka.lynk.lobby.api.mappers

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.lobby.api.dto.ws.outbound.LiveLocationDto
import com.eeseka.lynk.lobby.api.dto.ws.outbound.VotingCandidateDto
import com.eeseka.lynk.lobby.api.dto.ws.outbound.VotingSnapshotDto
import com.eeseka.lynk.lobby.domain.model.LiveLocation
import com.eeseka.lynk.lobby.domain.model.VotingCandidate
import com.eeseka.lynk.lobby.domain.model.VotingSnapshot
import com.eeseka.lynk.spot.api.mappers.toSpotDto

fun VotingCandidate.toVotingCandidateDto(): VotingCandidateDto {
    return VotingCandidateDto(
        spot = spot.toSpotDto(),
        suggestedById = suggestedById,
        suggestedByDisplayName = suggestedByDisplayName,
        note = note
    )
}

fun VotingSnapshot.toVotingSnapshotDto(): VotingSnapshotDto {
    return VotingSnapshotDto(
        hangoutId = hangoutId,
        candidates = candidates.map { it.toVotingCandidateDto() },
        votes = votes,
        latitude = centerLatitude,
        longitude = centerLongitude
    )
}

fun LiveLocation.toLiveLocationDto(hangoutId: HangoutId): LiveLocationDto {
    return LiveLocationDto(
        hangoutId = hangoutId,
        userId = userId,
        latitude = latitude,
        longitude = longitude,
        sharedAt = sharedAt
    )
}
