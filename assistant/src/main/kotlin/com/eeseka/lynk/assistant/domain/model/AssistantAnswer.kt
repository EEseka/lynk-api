package com.eeseka.lynk.assistant.domain.model

data class AssistantAnswer(
    val content: String,
    val inputTokens: Int,
    val outputTokens: Int
)
