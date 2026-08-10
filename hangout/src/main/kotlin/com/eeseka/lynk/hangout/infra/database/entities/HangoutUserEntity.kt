package com.eeseka.lynk.hangout.infra.database.entities

import com.eeseka.lynk.common.domain.type.UserId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(
    name = "hangout_users",
    schema = "hangout_service"
)
class HangoutUserEntity(
    @Id
    var userId: UserId,

    @Column(nullable = false, unique = true)
    var email: String,

    @Column(nullable = false, unique = true)
    var username: String,

    @Column(nullable = false)
    var displayName: String,

    @Column(nullable = true)
    var profilePictureUrl: String? = null,

    @CreationTimestamp
    var createdAt: Instant = Instant.now()
)