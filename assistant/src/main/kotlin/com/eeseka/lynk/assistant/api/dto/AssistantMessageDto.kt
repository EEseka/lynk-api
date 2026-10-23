package com.eeseka.lynk.assistant.api.dto

import com.eeseka.lynk.assistant.domain.model.AssistantFailureReason
import com.eeseka.lynk.assistant.domain.model.AssistantMessageRole
import com.eeseka.lynk.assistant.domain.model.AssistantMessageStatus
import com.eeseka.lynk.common.domain.type.AssistantMessageId
import java.time.Instant

data class AssistantMessageDto(
    val id: AssistantMessageId,
    val role: AssistantMessageRole,
    val status: AssistantMessageStatus?,
    val content: String,
    val failureReason: AssistantFailureReason?,
    val recommendations: List<AssistantRecommendationDto>,
    val createdAt: Instant
)
