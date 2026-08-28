package com.eeseka.lynk.notification.service

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.notification.infra.database.entities.NotificationUserEntity
import com.eeseka.lynk.notification.infra.database.repositories.NotificationUserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationUserService(
    private val notificationUserRepository: NotificationUserRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun createNotificationUser(userId: UserId, email: String, displayName: String) {
        notificationUserRepository.save(
            NotificationUserEntity(
                userId = userId,
                email = email,
                displayName = displayName
            )
        )
    }

    @Transactional
    fun updateNotificationUser(userId: UserId, displayName: String) {
        val entity = notificationUserRepository.findByIdOrNull(userId) ?: run {
            logger.warn("ProfileUpdated event received for unknown NotificationUser {}, skipping!", userId)
            return
        }

        notificationUserRepository.save(
            entity.apply { this.displayName = displayName }
        )
    }

    fun deleteNotificationUser(userId: UserId) {
        notificationUserRepository.deleteById(userId)
    }

    fun findByUserIds(userIds: Collection<UserId>): List<NotificationUserEntity> =
        notificationUserRepository.findAllById(userIds)
}
