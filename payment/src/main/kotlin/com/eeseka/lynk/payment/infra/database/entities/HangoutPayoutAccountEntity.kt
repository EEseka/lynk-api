package com.eeseka.lynk.payment.infra.database.entities

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(
    name = "hangout_payout_accounts",
    schema = "payment_service"
)
class HangoutPayoutAccountEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(nullable = false, unique = true)
    var hangoutId: HangoutId,

    @Column(nullable = false)
    var hostId: UserId,

    @Column(nullable = false, length = 100)
    var recipientCode: String, // Paystack's handle for "pay this person". This is what a transfer is sent to.

    @Column(nullable = true, length = 100)
    var bankName: String?,

    @Column(nullable = false, length = 4)
    var accountNumberLast4: String,

    @Column(nullable = false, length = 100)
    var accountHolderName: String,

    @Column(nullable = true, unique = true, length = 100)
    var transferReference: String? = null,

    @Column(nullable = true)
    var paidOutAt: Instant? = null,

    @Column(nullable = true, length = 255)
    var payoutFailureReason: String? = null,

    @CreationTimestamp
    var createdAt: Instant = Instant.now(),

    @UpdateTimestamp
    var updatedAt: Instant = Instant.now()
)