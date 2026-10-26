package com.eeseka.lynk.assistant.domain.model

import com.eeseka.lynk.common.domain.type.AssistantMessageId
import java.time.Instant

data class AssistantMessage(
    val id: AssistantMessageId,
    val role: AssistantMessageRole,
    val status: AssistantMessageStatus?,
    val content: String,
    val failureReason: AssistantFailureReason?,
    val recommendations: List<AssistantRecommendation>,
    val createdAt: Instant
)
