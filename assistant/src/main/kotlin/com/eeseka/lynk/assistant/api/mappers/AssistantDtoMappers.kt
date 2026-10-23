package com.eeseka.lynk.assistant.api.mappers

import com.eeseka.lynk.assistant.api.dto.AssistantMessageDto
import com.eeseka.lynk.assistant.api.dto.AssistantRecommendationDto
import com.eeseka.lynk.assistant.api.dto.AssistantUsageDto
import com.eeseka.lynk.assistant.domain.model.AssistantMessage
import com.eeseka.lynk.assistant.domain.model.AssistantRecommendation
import com.eeseka.lynk.common.domain.model.UserRateLimitUsage
import com.eeseka.lynk.spot.api.mappers.toSpotDto

fun AssistantMessage.toAssistantMessageDto(): AssistantMessageDto {
    return AssistantMessageDto(
        id = id,
        role = role,
        status = status,
        content = content,
        failureReason = failureReason,
        recommendations = recommendations.map { it.toAssistantRecommendationDto() },
        createdAt = createdAt
    )
}

fun AssistantRecommendation.toAssistantRecommendationDto(): AssistantRecommendationDto {
    return AssistantRecommendationDto(
        spot = spot.toSpotDto(),
        reason = reason
    )
}

fun UserRateLimitUsage.toAssistantUsageDto(limit: Int): AssistantUsageDto {
    return AssistantUsageDto(
        used = used,
        limit = limit,
        resetsInSeconds = resetsInSeconds
    )
}
