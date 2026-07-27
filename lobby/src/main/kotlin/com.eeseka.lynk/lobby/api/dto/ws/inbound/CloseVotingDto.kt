package com.eeseka.lynk.lobby.api.dto.ws.inbound

import com.eeseka.lynk.common.domain.type.HangoutId

// chosenSpotId is null normally (the highest-voted candidate wins); it's set only to break a tie among co-leaders.
data class CloseVotingDto(
    val hangoutId: HangoutId,
    val chosenSpotId: String? = null
)