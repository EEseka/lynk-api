package com.eeseka.lynk.assistant.domain.model

import com.eeseka.lynk.common.domain.type.AssistantMessageId
import com.eeseka.lynk.common.domain.type.DiscoverChatId

sealed interface AssistantStreamEvent {
    data class Started(
        val messageId: AssistantMessageId,
        val discoverChatId: DiscoverChatId? // Discover chats
    ) : AssistantStreamEvent

    data class Status(val kind: AssistantStatusKind) : AssistantStreamEvent

    data class Text(val text: String) : AssistantStreamEvent

    data class Spots(val recommendations: List<AssistantRecommendation>) : AssistantStreamEvent

    data object Done : AssistantStreamEvent

    data class Error(val reason: AssistantFailureReason) : AssistantStreamEvent
}
