package com.eeseka.lynk.assistant.infra.redis.dto

import com.eeseka.lynk.common.domain.type.DiscoverChatId

data class DiscoverChatDto(
    val id: DiscoverChatId,
    val messages: List<DiscoverChatMessageDto>
)
