package com.eeseka.lynk.lobby.api.dto.ws.outbound

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId

// Outbound VOTE_TALLY: the whole current vote map (userId -> spotId they picked).
// Sent on every change; the client derives per-spot counts and its own vote from it.
data class VoteTallyDto(
    val hangoutId: HangoutId,
    val votes: Map<UserId, String>
)