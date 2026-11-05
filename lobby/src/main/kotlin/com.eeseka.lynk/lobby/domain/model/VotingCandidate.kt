package com.eeseka.lynk.lobby.domain.model

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.spot.domain.model.Spot

data class VotingCandidate(
    val spot: Spot,
    val suggestedById: UserId,
    val suggestedByDisplayName: String,
    val note: String?
)
