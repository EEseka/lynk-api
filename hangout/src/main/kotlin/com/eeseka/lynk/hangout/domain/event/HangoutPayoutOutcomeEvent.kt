package com.eeseka.lynk.hangout.domain.event

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId

data class HangoutPayoutOutcomeEvent(
    val hangoutId: HangoutId,
    val hostId: UserId,
    val succeeded: Boolean
)