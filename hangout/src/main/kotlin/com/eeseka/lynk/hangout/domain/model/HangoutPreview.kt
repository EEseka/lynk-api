package com.eeseka.lynk.hangout.domain.model

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import java.time.Instant

// The glimpse a PENDING invitee sees before accepting.
//  Mirrors Hangout, minus totalCost, and attendees are plain HangoutUsers (no RSVP/payment) — ATTENDING only.
data class HangoutPreview(
    val id: HangoutId,
    val hostId: UserId,
    val name: String,
    val description: String?,
    val vibe: HangoutVibe,
    val status: HangoutStatus,
    val scheduledAt: Instant,
    val maxAttendees: Int?,
    val participantCount: Int,
    val chosenSpotID: String?, // Google Place ID, null if still voting
    val attendees: List<HangoutUser>, // ATTENDING only — social proof
    val createdAt: Instant
)
