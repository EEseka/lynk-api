package com.eeseka.lynk.hangout.infra.database.mappers

import com.eeseka.lynk.hangout.domain.model.Hangout
import com.eeseka.lynk.hangout.domain.model.HangoutParticipant
import com.eeseka.lynk.hangout.domain.model.HangoutSummary
import com.eeseka.lynk.hangout.domain.model.HangoutUser
import com.eeseka.lynk.hangout.infra.database.entities.HangoutEntity
import com.eeseka.lynk.hangout.infra.database.entities.HangoutParticipantEntity
import com.eeseka.lynk.hangout.infra.database.entities.HangoutUserEntity

fun HangoutUserEntity.toHangoutUser(): HangoutUser {
    return HangoutUser(
        userId = userId,
        username = username,
        displayName = displayName,
        profilePictureUrl = profilePictureUrl
    )
}

fun HangoutUser.toHangoutUserEntity(): HangoutUserEntity {
    return HangoutUserEntity(
        userId = userId,
        username = username,
        displayName = displayName,
        profilePictureUrl = profilePictureUrl
    )
}

fun HangoutParticipantEntity.toHangoutParticipant(): HangoutParticipant {
    return HangoutParticipant(
        userId = hangoutUser.userId,
        username = hangoutUser.username,
        displayName = hangoutUser.displayName,
        profilePictureUrl = hangoutUser.profilePictureUrl,
        rsvpStatus = rsvpStatus,
        hasPaid = hasPaid
    )
}

fun HangoutEntity.toHangout(): Hangout {
    return Hangout(
        id = id!!,
        hostId = hostId,
        name = name,
        description = description,
        vibe = vibe,
        status = status,
        scheduledAt = scheduledAt,
        maxAttendees = maxAttendees,
        participantCount = participantCount,
        chosenSpotID = chosenSpotId,
        totalCost = totalCost,
        participants = participants.map { it.toHangoutParticipant() },
        createdAt = createdAt
    )
}

fun HangoutEntity.toHangoutSummary(): HangoutSummary {
    return HangoutSummary(
        id = id!!,
        hostId = hostId,
        name = name,
        vibe = vibe,
        status = status,
        scheduledAt = scheduledAt,
        maxAttendees = maxAttendees,
        participantCount = participantCount,
        createdAt = createdAt
    )
}
