package com.eeseka.lynk.notification.infra.database.repositories

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.notification.infra.database.entities.NotificationUserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface NotificationUserRepository : JpaRepository<NotificationUserEntity, UserId> {

    @Modifying
    @Query("DELETE FROM NotificationUserEntity u WHERE u.userId = :userId")
    fun deleteByUserId(userId: UserId)
}