package com.eeseka.lynk.user.infra.database.repositories

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.user.domain.type.AuthProvider
import com.eeseka.lynk.user.infra.database.entities.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface UserRepository : JpaRepository<UserEntity, UserId> {
    fun findByEmail(email: String): UserEntity?
    fun existsByUsername(username: String): Boolean
    fun findByUsername(username: String): UserEntity?
    @Modifying
    @Query("DELETE FROM UserEntity u WHERE u.authProvider = :authProvider AND u.createdAt < :date")
    fun deleteByAuthProviderAndCreatedAtLessThan(authProvider: AuthProvider, date: Instant)
}