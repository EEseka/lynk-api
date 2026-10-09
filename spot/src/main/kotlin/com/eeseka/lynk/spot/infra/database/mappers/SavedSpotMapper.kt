package com.eeseka.lynk.spot.infra.database.mappers

import com.eeseka.lynk.spot.domain.model.Spot
import com.eeseka.lynk.spot.infra.database.entities.SavedSpotEntity

fun SavedSpotEntity.toSpot(): Spot {
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
        isSaved = true,
        savedAt = createdAt
    )
}
