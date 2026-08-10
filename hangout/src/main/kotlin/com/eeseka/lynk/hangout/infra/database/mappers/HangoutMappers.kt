package com.eeseka.lynk.hangout.infra.database.mappers

import com.eeseka.lynk.hangout.domain.model.Hangout
import com.eeseka.lynk.hangout.domain.model.HangoutParticipant
import com.eeseka.lynk.hangout.domain.model.HangoutPayment
import com.eeseka.lynk.hangout.domain.model.HangoutPreview
import com.eeseka.lynk.hangout.domain.model.HangoutSummary
import com.eeseka.lynk.hangout.domain.model.HangoutUser
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.infra.database.entities.HangoutEntity
import com.eeseka.lynk.hangout.infra.database.entities.HangoutParticipantEntity
import com.eeseka.lynk.hangout.infra.database.entities.HangoutPaymentEntity
import com.eeseka.lynk.hangout.infra.database.entities.HangoutUserEntity

fun HangoutUserEntity.toHangoutUser(): HangoutUser {
    return HangoutUser(
        userId = userId,
        email = email,
        username = username,
        displayName = displayName,
        profilePictureUrl = profilePictureUrl
    )
}

fun HangoutUser.toHangoutUserEntity(): HangoutUserEntity {
    return HangoutUserEntity(
        userId = userId,
        email = email,
        username = username,
        displayName = displayName,
        profilePictureUrl = profilePictureUrl
    )
}

fun HangoutParticipantEntity.toHangoutParticipant(): HangoutParticipant {
    return HangoutParticipant(
        user = hangoutUser.toHangoutUser(),
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
        participants = participants.map { it.toHangoutParticipant() },
        payment = payment?.toHangoutPayment(),
        createdAt = createdAt
    )
}

fun HangoutPaymentEntity.toHangoutPayment(): HangoutPayment {
    return HangoutPayment(
        totalCostKobo = totalCostKobo,
        costPerPersonKobo = costPerPersonKobo,
        splitHeadcount = splitHeadcount,
        deadline = deadline,
        state = state
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

fun HangoutEntity.toHangoutPreview(): HangoutPreview {
    return HangoutPreview(
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
        attendees = participants
            .filter { it.rsvpStatus == RsvpStatus.ATTENDING }
            .map { it.hangoutUser.toHangoutUser() },
        createdAt = createdAt
    )
}
