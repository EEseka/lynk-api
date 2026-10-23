package com.eeseka.lynk.assistant.service.agent.tools.mappers

import com.eeseka.lynk.assistant.domain.DistanceCalculator
import com.eeseka.lynk.assistant.domain.OpeningHoursCheck
import com.eeseka.lynk.assistant.service.agent.AssistantTurn
import com.eeseka.lynk.assistant.service.agent.tools.dto.outbound.SpotAmenitiesDto
import com.eeseka.lynk.assistant.service.agent.tools.dto.outbound.SpotCandidateDto
import com.eeseka.lynk.assistant.service.agent.tools.dto.outbound.SpotDetailsDto
import com.eeseka.lynk.assistant.service.agent.tools.dto.outbound.SpotParkingDto
import com.eeseka.lynk.assistant.service.agent.tools.dto.outbound.SpotPaymentDto
import com.eeseka.lynk.assistant.service.agent.tools.dto.outbound.SpotPriceRangeDto
import com.eeseka.lynk.spot.domain.model.Spot
import com.eeseka.lynk.spot.domain.model.SpotAmenities
import com.eeseka.lynk.spot.domain.model.SpotParking
import com.eeseka.lynk.spot.domain.model.SpotPayment
import com.eeseka.lynk.spot.domain.model.SpotPriceRange
import java.time.Instant
import kotlin.math.roundToInt

fun Spot.toSpotDetailsDto(turn: AssistantTurn): SpotDetailsDto {
    return SpotDetailsDto(
        spot = toSpotCandidateDto(turn),
        description = description,
        weeklyHours = openingHours?.weekdayDescriptions ?: emptyList(),
        amenities = amenities?.toSpotAmenitiesDto(),
        parking = parking?.toSpotParkingDto(),
        payment = payment?.toSpotPaymentDto()
    )
}

fun Spot.toSpotCandidateDto(turn: AssistantTurn): SpotCandidateDto {
    val distanceKm = if (turn.searchLatitude != null && turn.searchLongitude != null) {
        val meters = DistanceCalculator.calculateDistanceInMeters(turn.searchLatitude, turn.searchLongitude, latitude, longitude)
        (meters / 100.0).roundToInt() / 10.0
    } else null

    return SpotCandidateDto(
        spotId = id,
        name = name,
        type = typeLabel,
        category = category,
        priceLevel = priceLevel,
        priceRange = priceRange?.toSpotPriceRangeDto(),
        rating = rating,
        reviewCount = reviewCount,
        address = shortAddress,
        distanceKm = distanceKm,
        isOpenAtHangoutTime = turn.hangoutTime?.let { time ->
            openingHours?.let { OpeningHoursCheck.isOpenAt(it, time) }
        },
        isOpenNow = if (turn.hangoutTime == null) {
            openingHours?.let { OpeningHoursCheck.isOpenAt(it, Instant.now()) }
        } else null,
        isOnBallot = id in turn.ballotSpotIds
    )
}

fun SpotPriceRange.toSpotPriceRangeDto(): SpotPriceRangeDto {
    return SpotPriceRangeDto(
        currencyCode = currencyCode,
        startAmount = startAmount,
        endAmount = endAmount
    )
}

fun SpotAmenities.toSpotAmenitiesDto(): SpotAmenitiesDto {
    return SpotAmenitiesDto(
        isGoodForGroups = isGoodForGroups,
        isReservable = isReservable,
        hasLiveMusic = hasLiveMusic,
        hasOutdoorSeating = hasOutdoorSeating,
        servesCocktails = servesCocktails,
        isGoodForWatchingSports = isGoodForWatchingSports
    )
}

fun SpotParking.toSpotParkingDto(): SpotParkingDto {
    return SpotParkingDto(
        hasFreeLot = hasFreeLot,
        hasPaidLot = hasPaidLot,
        hasFreeStreet = hasFreeStreet,
        hasPaidStreet = hasPaidStreet,
        hasValet = hasValet,
        hasFreeGarage = hasFreeGarage,
        hasPaidGarage = hasPaidGarage
    )
}

fun SpotPayment.toSpotPaymentDto(): SpotPaymentDto {
    return SpotPaymentDto(
        acceptsCreditCards = acceptsCreditCards,
        acceptsDebitCards = acceptsDebitCards,
        acceptsCashOnly = acceptsCashOnly,
        acceptsNfc = acceptsNfc
    )
}
