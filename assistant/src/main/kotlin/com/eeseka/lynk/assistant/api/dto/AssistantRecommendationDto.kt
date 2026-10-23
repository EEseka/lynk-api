package com.eeseka.lynk.assistant.api.dto

import com.eeseka.lynk.spot.api.dto.SpotDto

data class AssistantRecommendationDto(
    val spot: SpotDto,
    val reason: String
)
