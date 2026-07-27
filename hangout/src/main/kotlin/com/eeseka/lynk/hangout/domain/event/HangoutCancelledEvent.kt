package com.eeseka.lynk.hangout.domain.event

import com.eeseka.lynk.common.domain.type.HangoutId

data class HangoutCancelledEvent(
    val hangoutId: HangoutId,
    val hostDisplayName: String
)
