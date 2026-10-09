package com.eeseka.lynk.spot.api.mappers

import com.eeseka.lynk.spot.api.dto.PaginatedSpotsDto
import com.eeseka.lynk.spot.api.dto.SpotAiSummaryDto
import com.eeseka.lynk.spot.api.dto.SpotAmenitiesDto
import com.eeseka.lynk.spot.api.dto.SpotDto
import com.eeseka.lynk.spot.api.dto.SpotOpeningHoursDto
import com.eeseka.lynk.spot.api.dto.SpotParkingDto
import com.eeseka.lynk.spot.api.dto.SpotPaymentDto
import com.eeseka.lynk.spot.api.dto.SpotPriceRangeDto
import com.eeseka.lynk.spot.domain.model.PaginatedSpots
import com.eeseka.lynk.spot.domain.model.Spot
import com.eeseka.lynk.spot.domain.model.SpotAiSummary
import com.eeseka.lynk.spot.domain.model.SpotAmenities
import com.eeseka.lynk.spot.domain.model.SpotOpeningHours
import com.eeseka.lynk.spot.domain.model.SpotParking
import com.eeseka.lynk.spot.domain.model.SpotPayment
import com.eeseka.lynk.spot.domain.model.SpotPriceRange

fun Spot.toSpotDto(): SpotDto {
    return SpotDto(
        id = id,
        name = name,
        typeLabel = typeLabel,
        description = description,
        generativeSummary = generativeSummary?.toSpotAiSummaryDto(),
        reviewSummary = reviewSummary?.toSpotAiSummaryDto(),
        photoUrls = photoUrls,
        category = category,
        priceLevel = priceLevel,
        priceRange = priceRange?.toSpotPriceRangeDto(),
        rating = rating,
        reviewCount = reviewCount,
        businessStatus = businessStatus,
        openingHours = openingHours?.toSpotOpeningHoursDto(),
        amenities = amenities?.toSpotAmenitiesDto(),
        parking = parking?.toSpotParkingDto(),
        payment = payment?.toSpotPaymentDto(),
        shortAddress = shortAddress,
        latitude = latitude,
        longitude = longitude,
        phoneNumber = phoneNumber,
        websiteUrl = websiteUrl,
        googleMapsUrl = googleMapsUrl,
        directionsUrl = directionsUrl,
        isSaved = isSaved,
        savedAt = savedAt
    )
}

fun PaginatedSpots.toPaginatedSpotsDto(): PaginatedSpotsDto {
    return PaginatedSpotsDto(
        spots = spots.map { it.toSpotDto() },
        nextPageToken = nextPageToken
    )
}

private fun SpotAiSummary.toSpotAiSummaryDto() = SpotAiSummaryDto(
    text = text,
    disclosure = disclosure
)

private fun SpotPriceRange.toSpotPriceRangeDto() = SpotPriceRangeDto(
    currencyCode = currencyCode,
    startAmount = startAmount,
    endAmount = endAmount
)

private fun SpotOpeningHours.toSpotOpeningHoursDto() = SpotOpeningHoursDto(
    isOpenNow = isOpenNow,
    weekdayDescriptions = weekdayDescriptions,
    nextOpenTime = nextOpenTime,
    nextCloseTime = nextCloseTime,
    utcOffsetMinutes = utcOffsetMinutes
)

private fun SpotAmenities.toSpotAmenitiesDto() = SpotAmenitiesDto(
    isGoodForGroups = isGoodForGroups,
    isReservable = isReservable,
    hasLiveMusic = hasLiveMusic,
    hasOutdoorSeating = hasOutdoorSeating,
    servesCocktails = servesCocktails,
    isGoodForWatchingSports = isGoodForWatchingSports
)

private fun SpotParking.toSpotParkingDto() = SpotParkingDto(
    hasFreeLot = hasFreeLot,
    hasPaidLot = hasPaidLot,
    hasFreeStreet = hasFreeStreet,
    hasPaidStreet = hasPaidStreet,
    hasValet = hasValet,
    hasFreeGarage = hasFreeGarage,
    hasPaidGarage = hasPaidGarage
)

private fun SpotPayment.toSpotPaymentDto() = SpotPaymentDto(
    acceptsCreditCards = acceptsCreditCards,
    acceptsDebitCards = acceptsDebitCards,
    acceptsCashOnly = acceptsCashOnly,
    acceptsNfc = acceptsNfc
)
