package com.eeseka.lynk.notification.service

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.NotificationId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.notification.domain.exception.NotificationNotFoundException
import com.eeseka.lynk.notification.domain.model.Notification
import com.eeseka.lynk.notification.domain.model.NotificationType
import com.eeseka.lynk.notification.infra.database.entities.NotificationEntity
import com.eeseka.lynk.notification.infra.database.mappers.toNotification
import com.eeseka.lynk.notification.infra.database.repositories.NotificationRepository
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository
) {
    companion object {
        private const val RETENTION_DAYS = 90L
    }

    fun createNotifications(
        userIds: Collection<UserId>,
        type: NotificationType,
        hangoutId: HangoutId,
        hangoutName: String,
        actorDisplayName: String? = null,
        amountKobo: Long? = null
    ) {
        notificationRepository.saveAll(
            userIds.map { userId ->
                NotificationEntity(
                    userId = userId,
                    type = type,
                    hangoutId = hangoutId,
                    hangoutName = hangoutName,
                    actorDisplayName = actorDisplayName,
                    amountKobo = amountKobo
                )
            }
        )
    }

    fun getNotifications(
        userId: UserId,
        before: Instant?,
        pageSize: Int
    ): List<Notification> {
        return notificationRepository.findByUserIdAndCreatedAtBefore(
            userId = userId,
            before = before ?: Instant.now(),
            pageable = PageRequest.of(0, pageSize)
        ).content.map { it.toNotification() }
    }

    fun getUnreadCount(userId: UserId): Long = notificationRepository.countByUserIdAndIsReadFalse(userId)

    @Transactional
    fun markAsRead(userId: UserId, notificationId: NotificationId) {
        val notification = notificationRepository.findByIdAndUserId(
            id = notificationId,
            userId = userId
        ) ?: throw NotificationNotFoundException(notificationId.toString())

        notificationRepository.save(
            notification.apply { isRead = true }
        )
    }

    @Transactional
    fun markAllAsRead(userId: UserId) {
        notificationRepository.markAllAsReadByUserId(userId)
    }

    @Transactional
    fun deleteAllForUser(userId: UserId) {
        notificationRepository.deleteByUserId(userId)
    }

    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    fun deleteOldReadNotifications() {
        val cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS)
        notificationRepository.deleteByIsReadTrueAndCreatedAtBefore(cutoff)
    }
}