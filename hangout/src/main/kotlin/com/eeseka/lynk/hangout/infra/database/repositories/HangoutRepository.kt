package com.eeseka.lynk.hangout.infra.database.repositories

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.HangoutVibe
import com.eeseka.lynk.hangout.domain.model.PaymentState
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.infra.database.entities.HangoutEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface HangoutRepository : JpaRepository<HangoutEntity, HangoutId> {

    // Answers: "Give me this hangout, but only if I'm a participant in it"
    @Query("""
        SELECT h
        FROM HangoutEntity h
        LEFT JOIN FETCH h.participants p
        LEFT JOIN FETCH p.hangoutUser
        WHERE h.id = :id
        AND EXISTS (
            SELECT 1
            FROM h.participants p
            WHERE p.hangoutUser.userId = :userId
        )
    """)
    // 1 is a dummy value; we don't want the data, just YES/NO does a row exist
    fun findHangoutById(id: HangoutId, userId: UserId): HangoutEntity?

    // Answers: "What hangouts am I hosting?"
    @Query("""
        SELECT h
        FROM HangoutEntity h
        LEFT JOIN FETCH h.participants p
        LEFT JOIN FETCH p.hangoutUser
        WHERE h.hostId = :userId
    """)
    fun findAllHostedByUserId(userId: UserId): List<HangoutEntity>

    // All feeds: cursor-paginated by createdAt, status drives which tab is served.
    @Query("""
        SELECT h
        FROM HangoutEntity h
        WHERE EXISTS (
            SELECT 1
            FROM h.participants p
            WHERE p.hangoutUser.userId = :userId
            AND p.rsvpStatus = :rsvpStatus
        )
        AND h.createdAt < :before
        AND h.status IN :statuses
        AND (:vibe IS NULL OR h.vibe = :vibe)
        AND LOWER(h.name) LIKE LOWER(CONCAT('%', COALESCE(:query, ''), '%'))
        ORDER BY h.createdAt DESC
    """)
    fun findByParticipantAndCreatedAtBeforeAndStatusInAndVibeAndNameContaining(
        userId: UserId,
        rsvpStatus: RsvpStatus,
        before: Instant,
        statuses: Collection<HangoutStatus>,
        vibe: HangoutVibe?,
        query: String?,
        pageable: Pageable
    ): Slice<HangoutEntity>

    @Query("SELECT h.status FROM HangoutEntity h WHERE h.id = :hangoutId")
    fun findStatusById(hangoutId: HangoutId): HangoutStatus?

    @Query("SELECT h.hostId FROM HangoutEntity h WHERE h.id = :hangoutId")
    fun findHostIdById(hangoutId: HangoutId): UserId?

    @Query("SELECT h.name FROM HangoutEntity h WHERE h.id = :hangoutId")
    fun findNameById(hangoutId: HangoutId): String?

    // Drives the payout sweep: everything collected and waiting to be sent to a host.
    @Query("""
        SELECT h.id
        FROM HangoutEntity h
        WHERE h.payment.state = :paymentState
        AND h.status <> :excludedStatus
    """)
    fun findIdsByPaymentStateAndStatusNot(
        paymentState: PaymentState,
        excludedStatus: HangoutStatus
    ): List<HangoutId>

    @Query("SELECT h.id FROM HangoutEntity h WHERE h.payment.state = :paymentState")
    fun findIdsByPaymentState(paymentState: PaymentState): List<HangoutId>

    // Drives the payment deadline sweep: hangouts still collecting whose deadline has passed.
    @Query("""
        SELECT h
        FROM HangoutEntity h
        LEFT JOIN FETCH h.participants
        WHERE h.payment.state = :paymentState
        AND h.payment.deadline < :deadline
        AND h.status <> :excludedStatus
    """)
    fun findByPaymentStateAndPaymentDeadlineBeforeAndStatusNot(
        paymentState: PaymentState,
        deadline: Instant,
        excludedStatus: HangoutStatus
    ): List<HangoutEntity>

    // Drives the last-resort sweep: the host never answered, and the hangout is starting anyway.
    @Query("""
        SELECT h
        FROM HangoutEntity h
        LEFT JOIN FETCH h.participants
        WHERE h.payment.state = :paymentState
        AND h.scheduledAt <= :now
        AND h.status <> :excludedStatus
    """)
    fun findByPaymentStateAndScheduledAtBeforeAndStatusNot(
        paymentState: PaymentState,
        now: Instant,
        excludedStatus: HangoutStatus
    ): List<HangoutEntity>

    @Query("""
        SELECT h
        FROM HangoutEntity h
        LEFT JOIN FETCH h.participants p
        LEFT JOIN FETCH p.hangoutUser
        WHERE h.status IN :statuses
        AND h.scheduledAt <= :now
    """)
    fun findByStatusInAndScheduledAtBefore(
        statuses: Collection<HangoutStatus>,
        now: Instant
    ): List<HangoutEntity>

    // Scheduled job: "Flip all hangouts whose start time has arrived from waiting to ongoing"
    @Modifying
    @Query("""
        UPDATE HangoutEntity h
        SET h.status = :ongoingStatus
        WHERE h.status IN :activeStatuses
        AND h.scheduledAt <= :now
    """)
    fun transitionDueHangoutsToOngoing(
        now: Instant,
        ongoingStatus: HangoutStatus,
        activeStatuses: Collection<HangoutStatus>
    )

    // Scheduled job: "Sweep hangouts nobody ever joined and nobody ever paid for, long after the date"
    @Modifying
    @Query("""
        DELETE FROM HangoutEntity h
        WHERE h.participantCount = 1
        AND h.scheduledAt < :cutoff
        AND h.payment.state IS NULL
    """)
    fun deleteSoloUnpaidHangoutsScheduledBefore(cutoff: Instant)

    @Query("""
        SELECT COUNT(h)
        FROM HangoutEntity h
        WHERE h.hostId = :userId
        AND h.status = :completedStatus
    """)
    fun countByHostIdAndStatus(userId: UserId, completedStatus: HangoutStatus): Long

    // Account deletion guard: a hangout this user is still hosting that has not finished.
    fun existsByHostIdAndStatusIn(hostId: UserId, statuses: Collection<HangoutStatus>): Boolean

    // Account deletion guard: money of theirs, or money owed to them, that has not settled yet.
    @Query("""
        SELECT COUNT(h) > 0
        FROM HangoutEntity h
        WHERE h.hostId = :hostId
        AND h.payment.state IN :paymentStates
    """)
    fun existsByHostIdAndPaymentStateIn(
        hostId: UserId,
        paymentStates: Collection<PaymentState>
    ): Boolean
}