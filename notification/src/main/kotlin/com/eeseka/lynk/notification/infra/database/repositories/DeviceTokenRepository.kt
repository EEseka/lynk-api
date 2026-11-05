package com.eeseka.lynk.notification.infra.database.repositories

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.notification.domain.model.Platform
import com.eeseka.lynk.notification.infra.database.entities.DeviceTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

interface DeviceTokenRepository : JpaRepository<DeviceTokenEntity, Long> {

    fun findByUserIdIn(userIds: Collection<UserId>): List<DeviceTokenEntity>

    fun findByToken(token: String): DeviceTokenEntity?

    @Modifying
    @Transactional
    @Query("UPDATE DeviceTokenEntity d SET d.userId = :userId, d.platform = :platform WHERE d.token = :token")
    fun updateOwnerByToken(token: String, userId: UserId, platform: Platform): Int

    @Modifying
    @Query("DELETE FROM DeviceTokenEntity d WHERE d.token = :token")
    fun deleteByToken(token: String)

    @Modifying
    @Query("DELETE FROM DeviceTokenEntity d WHERE d.token IN :tokens")
    fun deleteByTokenIn(tokens: Collection<String>)

    @Modifying
    @Query("DELETE FROM DeviceTokenEntity d WHERE d.userId = :userId")
    fun deleteByUserId(userId: UserId)
}