package com.eeseka.lynk.lobby.api.dto.ws.outbound

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.spot.api.dto.SpotDto

data class CandidateAddedDto(
    val hangoutId: HangoutId,
    val spot: SpotDto
)