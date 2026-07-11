package com.eeseka.lynk.hangout.infra.database.repositories

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.infra.database.entities.HangoutParticipantEntity
import org.springframework.data.jpa.repository.JpaRepository

interface HangoutParticipantRepository : JpaRepository<HangoutParticipantEntity, Long> {

    fun findAllByHangoutId(hangoutId: HangoutId): List<HangoutParticipantEntity>

    fun existsByHangoutIdAndHangoutUserUserId(hangoutId: HangoutId, userId: UserId): Boolean

    // Used to enforce maxAttendees: count ATTENDING + PENDING (active slots)
    fun countByHangoutIdAndRsvpStatusIn(
        hangoutId: HangoutId,
        statuses: List<RsvpStatus>
    ): Int
}
