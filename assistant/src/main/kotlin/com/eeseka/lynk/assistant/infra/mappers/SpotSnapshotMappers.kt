package com.eeseka.lynk.assistant.infra.mappers

import com.eeseka.lynk.spot.domain.model.PriceLevel
import com.eeseka.lynk.spot.domain.model.Spot
import com.eeseka.lynk.spot.domain.model.SpotCategory

fun spotSnapshot(
    googlePlaceId: String,
    name: String,
    coverPhotoUrl: String?,
    category: SpotCategory,
    priceLevel: PriceLevel?,
    shortAddress: String?,
    latitude: Double,
    longitude: Double
): Spot {
    return Spot(
        id = googlePlaceId,
        name = name,
        typeLabel = null,
        description = null, // Not needed for the small UI card
        generativeSummary = null,
        reviewSummary = null,
        photoUrls = coverPhotoUrl?.let { listOf(it) } ?: emptyList(),
        category = category,
        priceLevel = priceLevel,
        priceRange = null,
        rating = null, // Volatile
        reviewCount = null, // Volatile
        businessStatus = null, // Volatile
        openingHours = null, // Volatile
        amenities = null,
        parking = null,
        payment = null,
        shortAddress = shortAddress,
        latitude = latitude,
        longitude = longitude,
        phoneNumber = null,
        websiteUrl = null,
        googleMapsUrl = null,
        directionsUrl = null,
        isSaved = false // Cards never show it
    )
}
