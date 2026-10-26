package com.eeseka.lynk.assistant.infra.database.mappers

import com.eeseka.lynk.assistant.domain.model.AssistantMessage
import com.eeseka.lynk.assistant.domain.model.AssistantRecommendation
import com.eeseka.lynk.assistant.infra.database.entities.AssistantMessageEntity
import com.eeseka.lynk.assistant.infra.database.entities.AssistantSpotEntity
import com.eeseka.lynk.assistant.infra.mappers.spotSnapshot

fun AssistantMessageEntity.toAssistantMessage(recommendations: List<AssistantRecommendation>): AssistantMessage {
    return AssistantMessage(
        id = id!!,
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
        spot = spotSnapshot(
            googlePlaceId = googlePlaceId,
            name = name,
            coverPhotoUrl = coverPhotoUrl,
            category = category,
            priceLevel = priceLevel,
            shortAddress = shortAddress,
            latitude = latitude,
            longitude = longitude
        ),
        reason = reason
    )
}
