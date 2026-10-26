package com.eeseka.lynk.assistant.domain.model

import com.eeseka.lynk.common.domain.type.DiscoverChatId

data class DiscoverChat(
    val id: DiscoverChatId,
    val messages: List<AssistantMessage> // Oldest first
)
