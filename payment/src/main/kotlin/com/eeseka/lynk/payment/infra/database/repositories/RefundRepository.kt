package com.eeseka.lynk.payment.infra.database.repositories

import com.eeseka.lynk.common.domain.type.PaymentId
import com.eeseka.lynk.payment.domain.model.RefundStatus
import com.eeseka.lynk.payment.infra.database.entities.RefundEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface RefundRepository : JpaRepository<RefundEntity, Long> {

    // REQUIRES_NEW because it also runs from an after-commit listener, where the caller's transaction is already over.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query("""
        INSERT INTO RefundEntity (paymentId, amountKobo, status, createdAt)
        VALUES (:paymentId, :amountKobo, :status, :createdAt)
    """)
    fun insert(paymentId: PaymentId, amountKobo: Long, status: RefundStatus, createdAt: Instant)

    // REQUIRES_NEW for the same reason as insert
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query("""
        UPDATE RefundEntity r
        SET r.status = :newStatus, r.failureReason = :failureReason, r.settledAt = :settledAt
        WHERE r.paymentId = :paymentId
        AND r.status = :inFlightStatus
    """)
    fun settleInFlight(
        paymentId: PaymentId,
        newStatus: RefundStatus,
        failureReason: String?,
        settledAt: Instant,
        inFlightStatus: RefundStatus
    ): Int
}
