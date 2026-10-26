package com.eeseka.lynk.assistant.domain.model

data class AssistantUsage(
    val inputTokens: Int,
    val cacheCreationInputTokens: Int,
    val cacheReadInputTokens: Int,
    val outputTokens: Int,
    val searchCount: Int
)
