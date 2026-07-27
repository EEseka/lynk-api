package com.eeseka.lynk.lobby.api.dto.ws.outbound

import com.eeseka.lynk.common.domain.type.HangoutId

data class CandidateRemovedDto(
    val hangoutId: HangoutId,
    val spotId: String
)