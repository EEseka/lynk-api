package com.eeseka.lynk.hangout.domain.model

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import java.time.Instant

data class Hangout(
    val id: HangoutId,
    val hostId: UserId,
    val name: String,
    val description: String?,
    val vibe: HangoutVibe,
    val status: HangoutStatus,
    val scheduledAt: Instant,
    val maxAttendees: Int?,
    val participantCount: Int,
    val chosenSpotID: String?, // Google Place ID null if still voting
    val totalCost: Double?,
    val participants: List<HangoutParticipant>,
    val createdAt: Instant
)