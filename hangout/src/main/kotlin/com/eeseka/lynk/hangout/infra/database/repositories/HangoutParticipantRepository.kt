package com.eeseka.lynk.hangout.infra.database.repositories

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.infra.database.entities.HangoutParticipantEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface HangoutParticipantRepository : JpaRepository<HangoutParticipantEntity, Long> {

    fun findAllByHangoutId(hangoutId: HangoutId): List<HangoutParticipantEntity>

    fun existsByHangoutIdAndHangoutUserUserId(hangoutId: HangoutId, userId: UserId): Boolean

    // Used to enforce maxAttendees: count ATTENDING + PENDING (active slots)
    fun countByHangoutIdAndRsvpStatusIn(
        hangoutId: HangoutId,
        statuses: List<RsvpStatus>
    ): Int

    @Query("""
        SELECT p.hangout.id 
        FROM HangoutParticipantEntity p
        WHERE p.hangoutUser.userId = :userId
        AND p.rsvpStatus = :rsvpStatus
    """)
    fun findHangoutIdsByAttendee(
        userId: UserId,
        rsvpStatus: RsvpStatus
    ): List<HangoutId>
}
