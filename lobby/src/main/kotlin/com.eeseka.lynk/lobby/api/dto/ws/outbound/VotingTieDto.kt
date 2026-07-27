package com.eeseka.lynk.lobby.api.dto.ws.outbound

import com.eeseka.lynk.common.domain.type.HangoutId

// Outbound VOTING_TIE: the round ended in a tie. The host's client shows these co-leaders so the host can pick one.
data class VotingTieDto(
    val hangoutId: HangoutId,
    val tiedSpotIds: List<String>
)