package com.eeseka.lynk.lobby.domain.model

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.spot.domain.model.Spot

data class VotingSnapshot(
    val hangoutId: HangoutId,
    val candidates: List<Spot>,
    val votes: Map<UserId, String>,
    val centerLatitude: Double?,
    val centerLongitude: Double?
)
