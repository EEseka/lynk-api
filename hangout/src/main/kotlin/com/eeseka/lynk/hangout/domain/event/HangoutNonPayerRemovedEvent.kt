package com.eeseka.lynk.hangout.domain.event

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId

data class HangoutNonPayerRemovedEvent(
    val hangoutId: HangoutId,
    val userId: UserId,
    val displayName: String
)