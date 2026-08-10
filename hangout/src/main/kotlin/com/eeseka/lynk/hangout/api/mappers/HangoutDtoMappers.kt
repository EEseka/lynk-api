package com.eeseka.lynk.hangout.api.mappers

import com.eeseka.lynk.hangout.api.dto.HangoutDto
import com.eeseka.lynk.hangout.api.dto.HangoutParticipantDto
import com.eeseka.lynk.hangout.api.dto.HangoutPaymentDto
import com.eeseka.lynk.hangout.api.dto.HangoutPreviewDto
import com.eeseka.lynk.hangout.api.dto.HangoutSummaryDto
import com.eeseka.lynk.hangout.api.dto.HangoutUserDto
import com.eeseka.lynk.hangout.domain.model.Hangout
import com.eeseka.lynk.hangout.domain.model.HangoutParticipant
import com.eeseka.lynk.hangout.domain.model.HangoutPayment
import com.eeseka.lynk.hangout.domain.model.HangoutPreview
import com.eeseka.lynk.hangout.domain.model.HangoutSummary
import com.eeseka.lynk.hangout.domain.model.HangoutUser
import com.eeseka.lynk.spot.api.mappers.toSpotDto
import com.eeseka.lynk.spot.domain.model.Spot

fun HangoutUser.toHangoutUserDto(): HangoutUserDto {
    return HangoutUserDto(
        userId = userId,
        username = username,
        displayName = displayName,
        profilePictureUrl = profilePictureUrl
    )
}

fun HangoutParticipant.toHangoutParticipantDto(): HangoutParticipantDto {
    return HangoutParticipantDto(
        user = user.toHangoutUserDto(),
        rsvpStatus = rsvpStatus,
        hasPaid = hasPaid
    )
}

fun HangoutSummary.toHangoutSummaryDto(): HangoutSummaryDto {
    return HangoutSummaryDto(
        id = id,
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

fun HangoutPreview.toHangoutPreviewDto(chosenSpot: Spot?): HangoutPreviewDto {
    return HangoutPreviewDto(
        id = id,
        hostId = hostId,
        name = name,
        description = description,
        vibe = vibe,
        status = status,
        scheduledAt = scheduledAt,
        maxAttendees = maxAttendees,
        participantCount = participantCount,
        chosenSpot = chosenSpot?.toSpotDto(),
        attendees = attendees.map { it.toHangoutUserDto() },
        createdAt = createdAt
    )
}

fun Hangout.toHangoutDto(chosenSpot: Spot?): HangoutDto {
    return HangoutDto(
        id = id,
        hostId = hostId,
        name = name,
        description = description,
        vibe = vibe,
        status = status,
        scheduledAt = scheduledAt,
        maxAttendees = maxAttendees,
        participantCount = participantCount,
        chosenSpot = chosenSpot?.toSpotDto(),
        participants = participants.map { it.toHangoutParticipantDto() },
        payment = payment?.toHangoutPaymentDto(),
        createdAt = createdAt
    )
}

fun HangoutPayment.toHangoutPaymentDto(): HangoutPaymentDto {
    return HangoutPaymentDto(
        totalCostKobo = totalCostKobo,
        costPerPersonKobo = costPerPersonKobo,
        splitHeadcount = splitHeadcount,
        deadline = deadline,
        state = state
    )
}