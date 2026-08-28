package com.eeseka.lynk.notification.infra.database.repositories

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.notification.infra.database.entities.NotificationUserEntity
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationUserRepository : JpaRepository<NotificationUserEntity, UserId>