package com.eeseka.lynk.lobby.domain.model

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId

data class VotingSnapshot(
    val hangoutId: HangoutId,
    val candidates: List<VotingCandidate>,
    val votes: Map<UserId, String>,
    val centerLatitude: Double?,
    val centerLongitude: Double?
)
