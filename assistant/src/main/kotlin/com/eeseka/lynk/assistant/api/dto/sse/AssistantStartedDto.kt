package com.eeseka.lynk.assistant.api.dto.sse

import com.eeseka.lynk.common.domain.type.AssistantMessageId
import com.eeseka.lynk.common.domain.type.DiscoverChatId

data class AssistantStartedDto(
    val messageId: AssistantMessageId,
    val discoverChatId: DiscoverChatId?
)
