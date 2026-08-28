package com.eeseka.lynk.notification.infra.database.repositories

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.notification.infra.database.entities.DeviceTokenEntity
import org.springframework.data.jpa.repository.JpaRepository

interface DeviceTokenRepository : JpaRepository<DeviceTokenEntity, Long> {

    fun findByUserIdIn(userIds: Collection<UserId>): List<DeviceTokenEntity>

    fun findByToken(token: String): DeviceTokenEntity?

    fun deleteByToken(token: String)

    fun deleteByTokenIn(tokens: Collection<String>)

    fun deleteByUserId(userId: UserId)
}