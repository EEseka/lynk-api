package com.eeseka.lynk.hangout.infra.database.repositories

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.HangoutVibe
import com.eeseka.lynk.hangout.infra.database.entities.HangoutEntity
import jakarta.transaction.Transactional
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

    // All feeds: cursor-paginated by createdAt, status drives which tab is served
    @Query("""
        SELECT h
        FROM HangoutEntity h
        WHERE EXISTS (
            SELECT 1
            FROM h.participants p
            WHERE p.hangoutUser.userId = :userId
        )
        AND h.createdAt < :before
        AND h.status IN :statuses
        AND (:vibe IS NULL OR h.vibe = :vibe)
        AND LOWER(h.name) LIKE LOWER(CONCAT('%', COALESCE(:query, ''), '%'))
        ORDER BY h.createdAt DESC
    """)
    fun findByUserIdBefore(
        userId: UserId,
        before: Instant,
        statuses: Collection<HangoutStatus>,
        vibe: HangoutVibe?,
        query: String?,
        pageable: Pageable
    ): Slice<HangoutEntity>

    // Scheduled job: "Flip all hangouts whose start time has arrived from waiting to ongoing"
    @Modifying
    @Transactional
    @Query("""
        UPDATE HangoutEntity h
        SET h.status = :ongoingStatus
        WHERE h.status IN :activeStatuses
        AND h.scheduledAt <= :now
    """)
    fun transitionToOngoing(
        now: Instant,
        ongoingStatus: HangoutStatus,
        activeStatuses: Collection<HangoutStatus>
    )

    // Scheduled job: "Sweep empty hangouts whose event date is over 30 days in the past"
    @Modifying
    @Transactional
    @Query("""
        DELETE FROM HangoutEntity h
        WHERE h.participantCount = 1
        AND h.scheduledAt < :cutoff
    """)
    fun deleteGhostHangouts(cutoff: Instant)
}