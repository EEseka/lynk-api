package com.eeseka.lynk.assistant.infra.messaging

import com.eeseka.lynk.assistant.service.AssistantMessageService
import com.eeseka.lynk.common.domain.events.user.UserEvent
import com.eeseka.lynk.common.infra.message_queue.MessageQueues
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class AssistantUserEventListener(
    private val assistantMessageService: AssistantMessageService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = [MessageQueues.ASSISTANT_USER_EVENTS])
    fun handleUserEvent(event: UserEvent) {
        when (event) {
            is UserEvent.Deleted -> {
                logger.info("Deleting assistant chats for user {}", event.userId)
                assistantMessageService.deleteAllForUser(event.userId)
            }

            else -> Unit
        }
    }
}
