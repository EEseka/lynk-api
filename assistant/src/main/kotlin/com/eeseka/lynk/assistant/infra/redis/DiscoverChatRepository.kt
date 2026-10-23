package com.eeseka.lynk.assistant.infra.redis

import com.eeseka.lynk.assistant.domain.model.DiscoverChat
import com.eeseka.lynk.common.domain.type.DiscoverChatId
import com.eeseka.lynk.common.domain.type.UserId
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration

@Component
class DiscoverChatRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private const val CHAT_PREFIX = "assistant:discover_chat"
        private const val ANSWERING_PREFIX = "assistant:discover_answering"
        private val CHAT_TTL = Duration.ofHours(1)
        private val ANSWERING_TTL = Duration.ofMinutes(2) // Longer than the longest answer, so a server that dies mid-answer frees the chat on its own
    }

    fun find(userId: UserId, chatId: DiscoverChatId): DiscoverChat? {
        val json = redisTemplate.opsForValue().get(chatKey(userId, chatId)) ?: return null
        return objectMapper.readValue(json, DiscoverChat::class.java)
    }

    fun save(userId: UserId, chat: DiscoverChat) {
        redisTemplate.opsForValue().set(
            chatKey(userId, chat.id),
            objectMapper.writeValueAsString(chat),
            CHAT_TTL
        )
    }

    fun tryStartAnswering(userId: UserId): Boolean {
        return redisTemplate.opsForValue().setIfAbsent(answeringKey(userId), "1", ANSWERING_TTL) == true
    }

    fun finishAnswering(userId: UserId) {
        redisTemplate.delete(answeringKey(userId))
    }

    private fun chatKey(userId: UserId, chatId: DiscoverChatId) = "$CHAT_PREFIX:$userId:$chatId"

    private fun answeringKey(userId: UserId) = "$ANSWERING_PREFIX:$userId"
}
