package com.eeseka.lynk.hangout.api.dto

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.HangoutVibe
import java.time.Instant

data class HangoutSummaryDto(
    val id: HangoutId,
    val hostId: UserId,
    val name: String,
    val vibe: HangoutVibe,
    val status: HangoutStatus,
    val scheduledAt: Instant,
    val maxAttendees: Int?,
    val participantCount: Int,
    val createdAt: Instant
)