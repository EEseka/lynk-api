package com.eeseka.lynk.notification.infra.database.repositories

import com.eeseka.lynk.common.domain.type.NotificationId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.notification.infra.database.entities.NotificationEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface NotificationRepository : JpaRepository<NotificationEntity, NotificationId> {

    // The inbox feed: this user's rows, newest first, cursored on createdAt.
    @Query("""
        SELECT n
        FROM NotificationEntity n
        WHERE n.userId = :userId
        AND n.createdAt < :before
        ORDER BY n.createdAt DESC
    """)
    fun findByUserIdAndCreatedAtBefore(
        userId: UserId,
        before: Instant,
        pageable: Pageable
    ): Slice<NotificationEntity>

    fun countByUserIdAndIsReadFalse(userId: UserId): Long

    fun findByIdAndUserId(id: NotificationId, userId: UserId): NotificationEntity?

    @Modifying
    @Query("""
        UPDATE NotificationEntity n
        SET n.isRead = true
        WHERE n.userId = :userId
        AND n.isRead = false
    """)
    fun markAllAsReadByUserId(userId: UserId)

    @Modifying
    @Query("""
        DELETE FROM NotificationEntity n
        WHERE n.isRead = true
        AND n.createdAt < :cutoff
    """)
    fun deleteByIsReadTrueAndCreatedAtBefore(cutoff: Instant)

    fun deleteByUserId(userId: UserId)
}