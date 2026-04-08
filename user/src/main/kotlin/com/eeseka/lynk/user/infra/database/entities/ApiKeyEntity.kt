package com.eeseka.lynk.user.infra.database.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "api_keys",
    schema = "user_service"
)
class ApiKeyEntity(
    @Id
    var key: String,
    @Column(nullable = false)
    var email: String,
    @Column(nullable = false)
    var validFrom: Instant,
    @Column(nullable = false)
    var expiresAt: Instant,
)