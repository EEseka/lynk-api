package com.eeseka.lynk.spot.infra.google_places.mappers

import com.eeseka.lynk.spot.domain.model.Spot
import com.eeseka.lynk.spot.domain.model.PriceLevel
import com.eeseka.lynk.spot.domain.model.SpotCategory
import com.eeseka.lynk.spot.infra.google_places.dto.GooglePlace

fun GooglePlace.toSpot(): Spot {
    return Spot(
        id = id,
        name = displayName?.text ?: "Unknown",
        description = editorialSummary?.text,
        photoUrls = photos?.map { it.name } ?: emptyList(),
        category = mapCategory(primaryType, types),
        tags = types ?: emptyList(),
        priceLevel = mapPrice(priceLevel),
        rating = rating,
        reviewCount = userRatingCount ?: 0,
        isOpenNow = regularOpeningHours?.openNow ?: false,
        shortAddress = shortFormattedAddress ?: formattedAddress,
        latitude = location?.latitude ?: 0.0,
        longitude = location?.longitude ?: 0.0,
        websiteUrl = websiteUri,
        googleMapsUrl = googleMapsUri,
        isSaved = false
    )
}

private fun mapPrice(priceLevel: String?): PriceLevel? {
    return when (priceLevel) {
        "PRICE_LEVEL_INEXPENSIVE" -> PriceLevel.CHEAP
        "PRICE_LEVEL_MODERATE" -> PriceLevel.MODERATE
        "PRICE_LEVEL_EXPENSIVE" -> PriceLevel.EXPENSIVE
        "PRICE_LEVEL_VERY_EXPENSIVE" -> PriceLevel.LUXURY
        else -> null
    }
}

private fun mapCategory(primaryType: String?, types: List<String>?): SpotCategory {
    val allTypes = (listOfNotNull(primaryType) + (types ?: emptyList())).map { it.lowercase() }

    if (allTypes.isEmpty()) return SpotCategory.OTHER

    val matchedCategory = allTypes.firstNotNullOfOrNull { type ->
        when {
            type == "night_club" -> SpotCategory.CLUB
            type == "bar" || type.endsWith("_bar") || type == "lounge" -> SpotCategory.LOUNGE
            type.contains("cafe") || type.contains("coffee") -> SpotCategory.CAFE
            type.contains("restaurant") || type == "food" -> SpotCategory.RESTAURANT
            type in setOf(
                "shopping_mall",
                "tourist_attraction",
                "park",
                "museum",
                "movie_theater",
                "bowling_alley",
                "amusement_center",
                "art_gallery"
            ) -> SpotCategory.ACTIVITY
            else -> null
        }
    }

    return matchedCategory ?: SpotCategory.OTHER
}