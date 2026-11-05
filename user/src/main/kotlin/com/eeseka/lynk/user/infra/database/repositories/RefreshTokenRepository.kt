package com.eeseka.lynk.user.infra.database.repositories

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.user.infra.database.entities.RefreshTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, Long> {
    @Modifying
    @Query("DELETE FROM RefreshTokenEntity t WHERE t.userId = :userId AND t.hashedToken = :hashedToken")
    fun deleteByUserIdAndHashedToken(userId: UserId, hashedToken: String): Int

    @Modifying
    @Query("DELETE FROM RefreshTokenEntity t WHERE t.userId = :userId")
    fun deleteByUserId(userId: UserId)

    @Modifying
    @Query("DELETE FROM RefreshTokenEntity t WHERE t.expiresAt < :now")
    fun deleteByExpiresAtLessThan(now: Instant)
}