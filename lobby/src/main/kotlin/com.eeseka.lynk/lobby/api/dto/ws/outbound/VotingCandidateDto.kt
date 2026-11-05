package com.eeseka.lynk.lobby.api.dto.ws.outbound

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.spot.api.dto.SpotDto

data class VotingCandidateDto(
    val spot: SpotDto,
    val suggestedById: UserId,
    val suggestedByDisplayName: String,
    val note: String?
)
