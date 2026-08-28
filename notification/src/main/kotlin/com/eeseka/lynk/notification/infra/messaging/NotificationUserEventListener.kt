package com.eeseka.lynk.notification.infra.messaging

import com.eeseka.lynk.common.domain.events.user.UserEvent
import com.eeseka.lynk.common.infra.message_queue.MessageQueues
import com.eeseka.lynk.notification.service.DeviceTokenService
import com.eeseka.lynk.notification.service.EmailService
import com.eeseka.lynk.notification.service.NotificationService
import com.eeseka.lynk.notification.service.NotificationUserService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class NotificationUserEventListener(
    private val notificationUserService: NotificationUserService,
    private val notificationService: NotificationService,
    private val deviceTokenService: DeviceTokenService,
    private val emailService: EmailService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = [MessageQueues.NOTIFICATION_USER_EVENTS])
    fun handleUserEvent(event: UserEvent) {
        try {
            when (event) {
                is UserEvent.Created -> onCreated(event)
                is UserEvent.ProfileCompleted -> onProfileCompleted(event)
                is UserEvent.ProfileUpdated -> onProfileUpdated(event)
                is UserEvent.Deleted -> onDeleted(event)
            }
        } catch (e: Exception) {
            logger.error("Could not handle ${event.eventKey} for user event ${event.eventId}", e)
        }
    }

    private fun onCreated(event: UserEvent.Created) {
        emailService.sendCompleteProfileEmail(
            email = event.email,
            displayName = event.displayName
        )
    }

    private fun onProfileCompleted(event: UserEvent.ProfileCompleted) {
        notificationUserService.createNotificationUser(
            userId = event.userId,
            email = event.email,
            displayName = event.displayName
        )

        emailService.sendWelcomeEmail(
            email = event.email,
            displayName = event.displayName,
            username = event.username
        )
    }

    private fun onProfileUpdated(event: UserEvent.ProfileUpdated) {
        notificationUserService.updateNotificationUser(userId = event.userId, displayName = event.displayName)
    }

    private fun onDeleted(event: UserEvent.Deleted) {
        emailService.sendGoodbyeEmail(email = event.email, displayName = event.displayName)
        notificationService.deleteAllForUser(event.userId)
        deviceTokenService.unregisterAllDevices(event.userId)
        notificationUserService.deleteNotificationUser(event.userId)
    }
}