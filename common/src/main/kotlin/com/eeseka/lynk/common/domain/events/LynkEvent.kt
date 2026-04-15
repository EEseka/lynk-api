package com.eeseka.lynk.common.domain.events

import java.time.Instant

interface LynkEvent {
    val eventId: String
    val eventKey: String
    val occurredAt: Instant
    val exchange: String
}