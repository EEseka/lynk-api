package com.eeseka.lynk.assistant.infra.redis.dto

import com.eeseka.lynk.assistant.domain.model.AssistantFailureReason
import com.eeseka.lynk.assistant.domain.model.AssistantMessageRole
import com.eeseka.lynk.assistant.domain.model.AssistantMessageStatus
import com.eeseka.lynk.common.domain.type.AssistantMessageId
import java.time.Instant

data class DiscoverChatMessageDto(
    val id: AssistantMessageId,
    val role: AssistantMessageRole,
    val status: AssistantMessageStatus?, // Only answers have one; a question is never pending
    val content: String,
    val failureReason: AssistantFailureReason?,
    val spots: List<DiscoverChatSpotDto>,
    val createdAt: Instant
)
