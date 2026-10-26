package com.eeseka.lynk.payment.infra.database.entities

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.payment.domain.model.PayoutStatus
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
import java.time.Instant

@Entity
@Table(
    name = "payouts",
    schema = "payment_service",
    indexes = [
        Index(name = "idx_payouts_hangout_id", columnList = "hangout_id")
    ]
)
class PayoutEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(nullable = false)
    var hangoutId: HangoutId,

    @Column(nullable = false, unique = true, length = 100)
    var reference: String,

    @Column(nullable = false)
    var amountKobo: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PayoutStatus = PayoutStatus.IN_FLIGHT,

    @Column(nullable = true, length = 255)
    var failureReason: String? = null,

    @CreationTimestamp
    var createdAt: Instant = Instant.now(),

    @Column(nullable = true)
    var settledAt: Instant? = null
)
