package com.eeseka.lynk.assistant.domain.model

data class DiscoverChatMessage(
    val role: AssistantMessageRole,
    val content: String,
    val spots: List<DiscoverChatSpot>
)
