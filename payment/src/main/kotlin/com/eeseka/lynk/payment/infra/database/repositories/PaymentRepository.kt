package com.eeseka.lynk.payment.infra.database.repositories

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.PaymentId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.domain.model.RefundStatus
import com.eeseka.lynk.payment.infra.database.entities.PaymentEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface PaymentRepository : JpaRepository<PaymentEntity, PaymentId> {

    fun findByReference(reference: String): PaymentEntity?

    fun findAllByHangoutIdAndStatusAndRefundStatus(
        hangoutId: HangoutId,
        status: PaymentStatus,
        refundStatus: RefundStatus
    ): List<PaymentEntity>

    @Query("""
        SELECT COALESCE(SUM(p.netAmountKobo), 0)
        FROM PaymentEntity p
        WHERE p.hangoutId = :hangoutId
        AND p.status = :status
        AND p.refundStatus = :refundStatus
    """)
    fun sumNetAmountByHangoutIdAndStatusAndRefundStatus(
        hangoutId: HangoutId,
        status: PaymentStatus,
        refundStatus: RefundStatus
    ): Long

    // One person's money on one hangout, for when only they are leaving.
    fun findByHangoutIdAndUserIdAndStatusAndRefundStatus(
        hangoutId: HangoutId,
        userId: UserId,
        status: PaymentStatus,
        refundStatus: RefundStatus
    ): PaymentEntity?

    // Drives the reconciliation sweep: payments we started and never heard back about.
    fun findAllByStatusAndCreatedAtBefore(
        status: PaymentStatus,
        before: Instant
    ): List<PaymentEntity>

    fun existsByHangoutIdAndUserIdAndStatus(
        hangoutId: HangoutId,
        userId: UserId,
        status: PaymentStatus
    ): Boolean

    /**
     * Every payment one person has going on one hangout, locked for the rest of the transaction.
     *
     * A guest who taps Pay twice ends up with two references, and both can be reported paid at the same
     * moment. Without this, each webhook asks "has this person already paid?", both are told no, and two
     * payments are kept. Taking this lock first makes the second webhook wait for the first to finish,
     * so it sees the payment that just succeeded and sends its own money back.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentEntity p WHERE p.hangoutId = :hangoutId AND p.userId = :userId")
    fun lockAllByHangoutIdAndUserId(hangoutId: HangoutId, userId: UserId): List<PaymentEntity>

    fun findAllByRefundStatus(refundStatus: RefundStatus): List<PaymentEntity>

    fun findAllByRefundStatusAndUpdatedAtBefore(
        refundStatus: RefundStatus,
        before: Instant
    ): List<PaymentEntity>

    fun findAllByStatusAndRefundStatusAndPaidAtAfter(
        status: PaymentStatus,
        refundStatus: RefundStatus,
        paidAt: Instant
    ): List<PaymentEntity>

    fun findAllByHangoutIdAndUserIdAndStatus(
        hangoutId: HangoutId,
        userId: UserId,
        status: PaymentStatus
    ): List<PaymentEntity>

    /**
     * Moves a payment's refund to [newStatus], but only if it is still at [expectedStatus], and also
     * records the amount going back. Answers how many rows changed - 0 meaning somebody else got there
     * first and this call did nothing.
     *
     * One statement rather than a read followed by a save, because two refunds can be triggered at the
     * same moment and a read would let both of them see the row as free.
     *
     * [expectedStatus] is NONE for a first attempt and FAILED for a retry, so a retry can only pick up
     * a refund that is genuinely stuck rather than one already on its way.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE PaymentEntity p
        SET p.refundStatus = :newStatus, p.refundedAmountKobo = p.netAmountKobo
        WHERE p.id = :paymentId
        AND p.refundStatus = :expectedStatus
    """)
    fun transitionRefundStatus(
        paymentId: PaymentId,
        newStatus: RefundStatus,
        expectedStatus: RefundStatus
    ): Int

    // Account deletion guard: a charge still in flight, or a refund this user is still owed.
    fun existsByUserIdAndStatus(userId: UserId, status: PaymentStatus): Boolean

    fun existsByUserIdAndRefundStatusIn(
        userId: UserId,
        refundStatuses: Collection<RefundStatus>
    ): Boolean
}