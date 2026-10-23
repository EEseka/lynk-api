package com.eeseka.lynk.assistant.infra.messaging

import com.eeseka.lynk.assistant.service.AssistantMessageService
import com.eeseka.lynk.common.domain.events.hangout.HangoutEvent
import com.eeseka.lynk.common.infra.message_queue.MessageQueues
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class AssistantHangoutEventListener(
    private val assistantMessageService: AssistantMessageService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = [MessageQueues.ASSISTANT_HANGOUT_EVENTS])
    fun handleHangoutEvent(event: HangoutEvent) {
        when (event) {
            is HangoutEvent.AbandonedHangoutsDeleted -> {
                logger.info("Deleting assistant chats for {} deleted hangouts", event.hangoutIds.size)
                assistantMessageService.deleteAllForHangouts(event.hangoutIds)
            }

            else -> Unit
        }
    }
}
