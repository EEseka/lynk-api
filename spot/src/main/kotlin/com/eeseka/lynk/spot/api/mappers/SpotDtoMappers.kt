package com.eeseka.lynk.spot.api.mappers

import com.eeseka.lynk.spot.api.dto.PaginatedSpotsDto
import com.eeseka.lynk.spot.api.dto.SpotDto
import com.eeseka.lynk.spot.domain.model.PaginatedSpots
import com.eeseka.lynk.spot.domain.model.Spot

fun Spot.toSpotDto(): SpotDto {
    return SpotDto(
        id = id,
        name = name,
        description = description,
        photoUrls = photoUrls,
        category = category,
        tags = tags,
        priceLevel = priceLevel,
        rating = rating,
        reviewCount = reviewCount,
        isOpenNow = isOpenNow,
        shortAddress = shortAddress,
        latitude = latitude,
        longitude = longitude,
        isSaved = isSaved,
        websiteUrl = websiteUrl,
        googleMapsUrl = googleMapsUrl,
        savedAt = savedAt
    )
}

fun PaginatedSpots.toPaginatedSpotsDto(): PaginatedSpotsDto {
    return PaginatedSpotsDto(
        spots = spots.map { it.toSpotDto() },
        nextPageToken = nextPageToken
    )
}