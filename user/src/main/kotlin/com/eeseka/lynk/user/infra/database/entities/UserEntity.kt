package com.eeseka.lynk.user.infra.database.entities

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.user.domain.type.AuthProvider
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(
    name = "users",
    schema = "user_service",
    indexes = [
        Index(name = "idx_users_email", columnList = "email"),
        Index(name = "idx_users_username", columnList = "username"),
    ]
)
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UserId? = null,

    @Column(nullable = true, unique = true)
    var email: String?,

    @Column(nullable = true)
    var displayName: String?,

    @Column(nullable = true, unique = true)
    var username: String?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var authProvider: AuthProvider,

    @Column(nullable = true)
    var profilePhotoUrl: String? = null,

    @CreationTimestamp
    var createdAt: Instant = Instant.now(),

    @UpdateTimestamp
    var updatedAt: Instant = Instant.now(),
)