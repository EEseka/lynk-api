package com.eeseka.lynk.hangout.infra.messaging

import com.eeseka.lynk.common.domain.events.user.UserEvent
import com.eeseka.lynk.common.infra.message_queue.MessageQueues
import com.eeseka.lynk.hangout.domain.model.HangoutUser
import com.eeseka.lynk.hangout.service.HangoutUserService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class HangoutUserEventListener(
    private val hangoutUserService: HangoutUserService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = [MessageQueues.HANGOUT_USER_EVENTS])
    fun handleUserEvent(event: UserEvent) {
        logger.info("Received user event: {}", event)
        when (event) {
            is UserEvent.ProfileCompleted -> {
                hangoutUserService.createHangoutUser(
                    HangoutUser(
                        userId = event.userId,
                        username = event.username,
                        displayName = event.displayName,
                        profilePictureUrl = event.profilePictureUrl
                    )
                )
            }
            is UserEvent.ProfileUpdated -> {
                hangoutUserService.updateHangoutUser(
                    userId = event.userId,
                    displayName = event.displayName,
                    profilePictureUrl = event.profilePictureUrl
                )
            }
            is UserEvent.Deleted -> {
                hangoutUserService.deleteHangoutUser(event.userId)
            }
            else -> Unit
        }
    }
}
