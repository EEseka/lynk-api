package com.eeseka.lynk.hangout.domain.event

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId

data class HangoutCreatedEvent(
    val hangoutId: HangoutId,
    val hostId: UserId
)
