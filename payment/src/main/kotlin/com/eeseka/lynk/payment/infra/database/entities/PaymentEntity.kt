package com.eeseka.lynk.payment.infra.database.entities

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.PaymentId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.payment.domain.model.PaymentStatus
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
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(
    name = "payments",
    schema = "payment_service",
    indexes = [
        Index(name = "idx_payments_hangout_id", columnList = "hangout_id"),
        Index(name = "idx_payments_user_id", columnList = "user_id")
    ]
)
class PaymentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: PaymentId? = null,

    @Column(nullable = false)
    var hangoutId: HangoutId,

    @Column(nullable = false)
    var userId: UserId,

    @Column(nullable = false, unique = true, length = 100)
    var reference: String,

    @Column(nullable = false)
    var amountKobo: Long,

    @Column(nullable = false)
    var netAmountKobo: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentStatus = PaymentStatus.PENDING,

    @Column(nullable = true)
    var paidAt: Instant? = null,

    @Column(nullable = true, length = 255)
    var failureReason: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var refundStatus: RefundStatus = RefundStatus.NONE,

    @Column(nullable = true)
    var refundedAmountKobo: Long? = null,

    @Column(nullable = true)
    var refundedAt: Instant? = null,

    @CreationTimestamp
    var createdAt: Instant = Instant.now(),

    @UpdateTimestamp
    var updatedAt: Instant = Instant.now()
)