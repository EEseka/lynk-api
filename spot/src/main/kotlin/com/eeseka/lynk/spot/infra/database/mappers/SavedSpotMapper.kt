package com.eeseka.lynk.spot.infra.database.mappers

import com.eeseka.lynk.spot.domain.model.Spot
import com.eeseka.lynk.spot.infra.database.entities.SavedSpotEntity

fun SavedSpotEntity.toSpot(): Spot {
    return Spot(
        id = googlePlaceId,
        name = name,
        description = null, // Not needed for the small UI card
        photoUrls = coverPhotoUrl?.let { listOf(it) } ?: emptyList(),
        category = category,
        tags = emptyList(), // Too much clutter for a list screen
        priceLevel = priceLevel,
        rating = null, // Volatile
        reviewCount = null, // Volatile
        isOpenNow = false, // Volatile
        shortAddress = shortAddress,
        latitude = latitude,
        longitude = longitude,
        websiteUrl = null,
        googleMapsUrl = null,
        isSaved = true,
        savedAt = createdAt
    )
}