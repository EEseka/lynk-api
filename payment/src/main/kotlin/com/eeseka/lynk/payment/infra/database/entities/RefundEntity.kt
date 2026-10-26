package com.eeseka.lynk.payment.infra.database.entities

import com.eeseka.lynk.common.domain.type.PaymentId
import com.eeseka.lynk.payment.domain.model.RefundStatus
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
    name = "refunds",
    schema = "payment_service",
    indexes = [
        Index(name = "idx_refunds_payment_id", columnList = "payment_id")
    ]
)
class RefundEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(nullable = false)
    var paymentId: PaymentId,

    @Column(nullable = false)
    var amountKobo: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: RefundStatus = RefundStatus.REQUESTED,

    @Column(nullable = true, length = 255)
    var failureReason: String? = null,

    @CreationTimestamp
    var createdAt: Instant = Instant.now(),

    @Column(nullable = true)
    var settledAt: Instant? = null
)
