package com.eeseka.lynk.spot.infra.messaging

import com.eeseka.lynk.common.domain.events.user.UserEvent
import com.eeseka.lynk.common.infra.message_queue.MessageQueues
import com.eeseka.lynk.spot.service.SpotService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class SpotUserEventListener(
    private val spotService: SpotService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = [MessageQueues.SPOT_USER_EVENTS])
    fun handleUserEvent(event: UserEvent) {
        when (event) {
            is UserEvent.Deleted -> {
                logger.info("Deleting saved spots for user {}", event.userId)
                spotService.deleteAllSavedSpots(event.userId)
            }

            else -> Unit
        }
    }
}
