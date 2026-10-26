package com.eeseka.lynk.assistant.infra.redis.mappers

import com.eeseka.lynk.assistant.domain.model.AssistantMessage
import com.eeseka.lynk.assistant.domain.model.AssistantRecommendation
import com.eeseka.lynk.assistant.domain.model.DiscoverChat
import com.eeseka.lynk.assistant.infra.mappers.spotSnapshot
import com.eeseka.lynk.assistant.infra.redis.dto.DiscoverChatDto
import com.eeseka.lynk.assistant.infra.redis.dto.DiscoverChatMessageDto
import com.eeseka.lynk.assistant.infra.redis.dto.DiscoverChatSpotDto

fun DiscoverChatDto.toDiscoverChat(): DiscoverChat {
    return DiscoverChat(
        id = id,
        messages = messages.map { it.toAssistantMessage() }
    )
}

fun DiscoverChat.toDiscoverChatDto(): DiscoverChatDto {
    return DiscoverChatDto(
        id = id,
        messages = messages.map { it.toDiscoverChatMessageDto() }
    )
}

fun DiscoverChatMessageDto.toAssistantMessage(): AssistantMessage {
    return AssistantMessage(
        id = id,
        role = role,
        status = status,
        content = content,
        failureReason = failureReason,
        recommendations = spots.map { it.toAssistantRecommendation() },
        createdAt = createdAt
    )
}

fun AssistantMessage.toDiscoverChatMessageDto(): DiscoverChatMessageDto {
    return DiscoverChatMessageDto(
        id = id,
        role = role,
        status = status,
        content = content,
        failureReason = failureReason,
        spots = recommendations.map { it.toDiscoverChatSpotDto() },
        createdAt = createdAt
    )
}

fun DiscoverChatSpotDto.toAssistantRecommendation(): AssistantRecommendation {
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

fun AssistantRecommendation.toDiscoverChatSpotDto(): DiscoverChatSpotDto {
    return DiscoverChatSpotDto(
        reason = reason,
        googlePlaceId = spot.id,
        name = spot.name,
        coverPhotoUrl = spot.photoUrls.firstOrNull(),
        category = spot.category,
        priceLevel = spot.priceLevel,
        shortAddress = spot.shortAddress,
        latitude = spot.latitude,
        longitude = spot.longitude
    )
}
