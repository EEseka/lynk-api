package com.eeseka.lynk.assistant.infra.database.mappers

import com.eeseka.lynk.assistant.domain.model.AssistantMessage
import com.eeseka.lynk.assistant.domain.model.AssistantRecommendation
import com.eeseka.lynk.assistant.infra.database.entities.AssistantMessageEntity
import com.eeseka.lynk.assistant.infra.database.entities.AssistantSpotEntity
import com.eeseka.lynk.spot.domain.model.Spot

fun AssistantMessageEntity.toAssistantMessage(recommendations: List<AssistantRecommendation>): AssistantMessage {
    return AssistantMessage(
        id = id!!,
        hangoutId = hangoutId,
        role = role,
        status = status,
        content = content,
        failureReason = failureReason,
        recommendations = recommendations,
        createdAt = createdAt
    )
}

fun AssistantSpotEntity.toAssistantRecommendation(): AssistantRecommendation {
    return AssistantRecommendation(
        spot = Spot(
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
        ),
        reason = reason
    )
}
