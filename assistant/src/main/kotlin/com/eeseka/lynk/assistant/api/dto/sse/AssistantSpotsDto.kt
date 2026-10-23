package com.eeseka.lynk.assistant.api.dto.sse

import com.eeseka.lynk.assistant.api.dto.AssistantRecommendationDto

data class AssistantSpotsDto(
    val recommendations: List<AssistantRecommendationDto>
)
