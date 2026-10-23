package com.eeseka.lynk.assistant.domain.model

import com.eeseka.lynk.spot.domain.model.Spot

data class AssistantRecommendation(
    val spot: Spot,
    val reason: String
)
