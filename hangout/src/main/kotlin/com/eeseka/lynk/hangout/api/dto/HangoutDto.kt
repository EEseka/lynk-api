package com.eeseka.lynk.hangout.api.dto

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.HangoutVibe
import com.eeseka.lynk.spot.api.dto.SpotDto
import java.time.Instant

data class HangoutDto(
    val id: HangoutId,
    val hostId: UserId,
    val name: String,
    val description: String?,
    val vibe: HangoutVibe,
    val status: HangoutStatus,
    val scheduledAt: Instant,
    val maxAttendees: Int?,
    val participantCount: Int,
    val chosenSpot: SpotDto?,
    val totalCost: Double?,
    val participants: List<HangoutParticipantDto>,
    val createdAt: Instant
)