package com.eeseka.lynk.hangout.domain.event

import com.eeseka.lynk.common.domain.type.HangoutId

data class HangoutPaymentDeadlineResolvedEvent(
    val hangoutId: HangoutId
)