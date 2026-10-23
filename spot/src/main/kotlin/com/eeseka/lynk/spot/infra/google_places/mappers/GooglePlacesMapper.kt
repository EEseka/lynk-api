package com.eeseka.lynk.spot.infra.google_places.mappers

import com.eeseka.lynk.spot.domain.model.BusinessStatus
import com.eeseka.lynk.spot.domain.model.Spot
import com.eeseka.lynk.spot.domain.model.PriceLevel
import com.eeseka.lynk.spot.domain.model.SpotAiSummary
import com.eeseka.lynk.spot.domain.model.SpotAmenities
import com.eeseka.lynk.spot.domain.model.SpotOpeningHours
import com.eeseka.lynk.spot.domain.model.SpotOpeningPeriod
import com.eeseka.lynk.spot.domain.model.SpotParking
import com.eeseka.lynk.spot.domain.model.SpotPayment
import com.eeseka.lynk.spot.domain.model.SpotPriceRange
import com.eeseka.lynk.spot.infra.google_places.GooglePlaceTypes
import com.eeseka.lynk.spot.infra.google_places.dto.GoogleGenerativeSummary
import com.eeseka.lynk.spot.infra.google_places.dto.GoogleOpeningHours
import com.eeseka.lynk.spot.infra.google_places.dto.GoogleOpeningPeriod
import com.eeseka.lynk.spot.infra.google_places.dto.GoogleParkingOptions
import com.eeseka.lynk.spot.infra.google_places.dto.GooglePaymentOptions
import com.eeseka.lynk.spot.infra.google_places.dto.GooglePlace
import com.eeseka.lynk.spot.infra.google_places.dto.GooglePriceRange
import com.eeseka.lynk.spot.infra.google_places.dto.GoogleReviewSummary
import java.time.DayOfWeek

fun GooglePlace.toSpot(): Spot? {
    val location = location ?: return null
    val name = displayName?.text?.takeIf { it.isNotBlank() } ?: return null

    return Spot(
        id = id,
        name = name,
        typeLabel = primaryTypeDisplayName?.text,
        description = editorialSummary?.text,
        generativeSummary = generativeSummary?.toSpotAiSummary(),
        reviewSummary = reviewSummary?.toSpotAiSummary(),
        photoUrls = photos?.map { it.name } ?: emptyList(),
        category = GooglePlaceTypes.categoryOf(primaryType, types),
        priceLevel = mapPrice(priceLevel),
        priceRange = priceRange?.toSpotPriceRange(),
        rating = rating,
        reviewCount = userRatingCount ?: 0,
        businessStatus = mapBusinessStatus(businessStatus),
        openingHours = currentOpeningHours?.toSpotOpeningHours(utcOffsetMinutes, timeZone?.id),
        amenities = toSpotAmenities(),
        parking = parkingOptions?.toSpotParking(),
        payment = paymentOptions?.toSpotPayment(),
        shortAddress = shortFormattedAddress ?: formattedAddress,
        latitude = location.latitude,
        longitude = location.longitude,
        phoneNumber = internationalPhoneNumber,
        websiteUrl = websiteUri,
        googleMapsUrl = googleMapsUri,
        directionsUrl = googleMapsLinks?.directionsUri,
        isSaved = false
    )
}

private fun GoogleGenerativeSummary.toSpotAiSummary(): SpotAiSummary? {
    val text = overview?.text ?: return null
    val disclosure = disclosureText?.text ?: return null
    return SpotAiSummary(text = text, disclosure = disclosure)
}

private fun GoogleReviewSummary.toSpotAiSummary(): SpotAiSummary? {
    val summaryText = text?.text ?: return null
    val disclosure = disclosureText?.text ?: return null
    return SpotAiSummary(text = summaryText, disclosure = disclosure)
}

private fun GooglePriceRange.toSpotPriceRange(): SpotPriceRange? {
    val currencyCode = startPrice?.currencyCode ?: endPrice?.currencyCode ?: return null
    return SpotPriceRange(
        currencyCode = currencyCode,
        startAmount = startPrice?.units?.toLongOrNull()?.takeIf { it > 1 },
        endAmount = endPrice?.units?.toLongOrNull()
    )
}

private fun mapBusinessStatus(businessStatus: String?): BusinessStatus? {
    return when (businessStatus) {
        "OPERATIONAL" -> BusinessStatus.OPERATIONAL
        "CLOSED_TEMPORARILY" -> BusinessStatus.CLOSED_TEMPORARILY
        "CLOSED_PERMANENTLY" -> BusinessStatus.CLOSED_PERMANENTLY
        "FUTURE_OPENING" -> BusinessStatus.FUTURE_OPENING
        else -> null
    }
}

private fun GoogleOpeningHours.toSpotOpeningHours(utcOffsetMinutes: Int?, timeZoneId: String?) = SpotOpeningHours(
    weekdayDescriptions = weekdayDescriptions ?: emptyList(),
    utcOffsetMinutes = utcOffsetMinutes,
    timeZoneId = timeZoneId,
    periods = periods?.mapNotNull { it.toSpotOpeningPeriod() } ?: emptyList()
)

private fun GoogleOpeningPeriod.toSpotOpeningPeriod(): SpotOpeningPeriod? {
    val openDay = open?.day ?: return null
    return SpotOpeningPeriod(
        openDay = googleDayOfWeek(openDay),
        openMinuteOfDay = (open.hour ?: 0) * 60 + (open.minute ?: 0),
        closeDay = close?.day?.let { googleDayOfWeek(it) },
        closeMinuteOfDay = close?.let { (it.hour ?: 0) * 60 + (it.minute ?: 0) }
    )
}

// Google counts from Sunday as 0, java.time from Monday as 1
private fun googleDayOfWeek(day: Int): DayOfWeek = DayOfWeek.SUNDAY.plus(day.toLong())

private fun GooglePlace.toSpotAmenities(): SpotAmenities? {
    val isAllUnknown = listOf(goodForGroups, reservable, liveMusic, outdoorSeating, servesCocktails, goodForWatchingSports)
        .all { it == null }
    if (isAllUnknown) return null

    return SpotAmenities(
        isGoodForGroups = goodForGroups,
        isReservable = reservable,
        hasLiveMusic = liveMusic,
        hasOutdoorSeating = outdoorSeating,
        servesCocktails = servesCocktails,
        isGoodForWatchingSports = goodForWatchingSports
    )
}

private fun GoogleParkingOptions.toSpotParking() = SpotParking(
    hasFreeLot = freeParkingLot,
    hasPaidLot = paidParkingLot,
    hasFreeStreet = freeStreetParking,
    hasPaidStreet = paidStreetParking,
    hasValet = valetParking,
    hasFreeGarage = freeGarageParking,
    hasPaidGarage = paidGarageParking
)

private fun GooglePaymentOptions.toSpotPayment() = SpotPayment(
    acceptsCreditCards = acceptsCreditCards,
    acceptsDebitCards = acceptsDebitCards,
    acceptsCashOnly = acceptsCashOnly,
    acceptsNfc = acceptsNfc
)

private fun mapPrice(priceLevel: String?): PriceLevel? {
    return when (priceLevel) {
        "PRICE_LEVEL_INEXPENSIVE" -> PriceLevel.CHEAP
        "PRICE_LEVEL_MODERATE" -> PriceLevel.MODERATE
        "PRICE_LEVEL_EXPENSIVE" -> PriceLevel.EXPENSIVE
        "PRICE_LEVEL_VERY_EXPENSIVE" -> PriceLevel.LUXURY
        else -> null
    }
}