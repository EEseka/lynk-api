package com.eeseka.lynk.hangout.service

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.exception.HangoutAccessDeniedException
import com.eeseka.lynk.hangout.domain.exception.HangoutIllegalStateException
import com.eeseka.lynk.hangout.domain.exception.HangoutNotFoundException
import com.eeseka.lynk.hangout.domain.model.Hangout
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.HangoutSummary
import com.eeseka.lynk.hangout.domain.model.HangoutVibe
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.infra.database.entities.HangoutEntity
import com.eeseka.lynk.hangout.infra.database.entities.HangoutParticipantEntity
import com.eeseka.lynk.hangout.infra.database.mappers.toHangout
import com.eeseka.lynk.hangout.infra.database.mappers.toHangoutSummary
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutParticipantRepository
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutRepository
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutUserRepository
import com.eeseka.lynk.spot.domain.model.Spot
import com.eeseka.lynk.spot.service.SpotService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class HangoutService(
    private val hangoutRepository: HangoutRepository,
    private val hangoutParticipantRepository: HangoutParticipantRepository,
    private val hangoutUserRepository: HangoutUserRepository,
    private val spotService: SpotService
) {
    private val logger = LoggerFactory.getLogger(HangoutService::class.java)

    @Transactional
    fun createHangout(
        userId: UserId,
        name: String,
        description: String?,
        vibe: HangoutVibe,
        scheduledAt: Instant,
        maxAttendees: Int?,
        spotId: String?
    ): Pair<Hangout, Spot?> {
        val hangoutUserEntity = hangoutUserRepository.findByIdOrNull(userId)
            ?: throw HangoutAccessDeniedException("Complete your profile before creating a hangout.")

        val hangoutEntity = HangoutEntity(
            hostId = userId,
            name = name.trim(),
            description = description?.trim(),
            vibe = vibe,
            status = if (spotId != null) HangoutStatus.SCHEDULED else HangoutStatus.VOTING,
            scheduledAt = scheduledAt,
            maxAttendees = maxAttendees,
            participantCount = 1,
            chosenSpotId = spotId
        )

        val hostParticipantEntity = HangoutParticipantEntity(
            hangout = hangoutEntity,
            hangoutUser = hangoutUserEntity,
            rsvpStatus = RsvpStatus.ATTENDING,
            hasPaid = true
        )
        hangoutEntity.participants.add(hostParticipantEntity)

        val savedHangout = hangoutRepository.save(hangoutEntity).toHangout()
        return Pair(savedHangout, fetchSpotSafely(spotId, userId))
    }

    @Transactional
    fun updateHangout(
        userId: UserId,
        hangoutId: HangoutId,
        name: String,
        description: String?,
        vibe: HangoutVibe,
        scheduledAt: Instant,
        maxAttendees: Int?,
        spotId: String?
    ): Pair<Hangout, Spot?> {
        val hangoutEntity = hangoutRepository.findHangoutById(hangoutId, userId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        if (hangoutEntity.hostId != userId) {
            throw HangoutAccessDeniedException("Only the host can update this hangout.")
        }

        if (hangoutEntity.status == HangoutStatus.COMPLETED || hangoutEntity.status == HangoutStatus.CANCELLED) {
            throw HangoutIllegalStateException("Cannot update a ${hangoutEntity.status.name.lowercase()} hangout.")
        }

        val savedHangout = hangoutRepository.save(
            hangoutEntity.apply {
                this.name = name.trim()
                this.description = description?.trim()
                this.vibe = vibe
                this.status = if (spotId != null) HangoutStatus.SCHEDULED else HangoutStatus.VOTING
                this.scheduledAt = scheduledAt
                this.maxAttendees = maxAttendees
                this.chosenSpotId = spotId
            }
        ).toHangout()

        return Pair(savedHangout, fetchSpotSafely(spotId, userId))
    }

    fun getHangoutDetails(userId: UserId, hangoutId: HangoutId): Pair<Hangout, Spot?> {
        val hangout = hangoutRepository.findHangoutById(hangoutId, userId)?.toHangout()
            ?: throw HangoutNotFoundException(hangoutId.toString())

        return Pair(hangout, fetchSpotSafely(hangout.chosenSpotID, userId))
    }

    fun getHangouts(
        userId: UserId,
        pageSize: Int,
        before: Instant?,
        status: HangoutStatus?,
        vibe: HangoutVibe?,
        query: String?
    ): List<HangoutSummary> {
        val statuses = when (status) {
            HangoutStatus.VOTING -> listOf(HangoutStatus.VOTING)
            HangoutStatus.SCHEDULED -> listOf(HangoutStatus.SCHEDULED)
            HangoutStatus.ONGOING -> listOf(HangoutStatus.ONGOING)
            HangoutStatus.COMPLETED -> listOf(HangoutStatus.COMPLETED)
            HangoutStatus.CANCELLED -> listOf(HangoutStatus.CANCELLED)
            null -> listOf(HangoutStatus.VOTING, HangoutStatus.SCHEDULED)
        }
        return hangoutRepository.findByUserIdBefore(
            userId = userId,
            before = before ?: Instant.now(),
            statuses = statuses,
            vibe = vibe,
            query = query,
            pageable = PageRequest.of(0, pageSize)
        ).content.map { it.toHangoutSummary() }
    }

    @Transactional
    fun cancelHangout(userId: UserId, hangoutId: HangoutId) {
        val hangoutEntity = hangoutRepository.findHangoutById(hangoutId, userId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        if (hangoutEntity.hostId != userId) {
            throw HangoutAccessDeniedException("Only the host can cancel this hangout.")
        }

        if (hangoutEntity.status == HangoutStatus.COMPLETED || hangoutEntity.status == HangoutStatus.CANCELLED) {
            throw HangoutIllegalStateException("Cannot cancel a ${hangoutEntity.status.name.lowercase()} hangout.")
        }

        hangoutRepository.save(hangoutEntity.apply { this.status = HangoutStatus.CANCELLED })
    }

    @Transactional
    fun completeHangout(userId: UserId, hangoutId: HangoutId) {
        val hangoutEntity = hangoutRepository.findHangoutById(hangoutId, userId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        if (hangoutEntity.hostId != userId) {
            throw HangoutAccessDeniedException("Only the host can complete this hangout.")
        }

        if (hangoutEntity.status != HangoutStatus.ONGOING) {
            throw HangoutIllegalStateException("Cannot complete a ${hangoutEntity.status.name.lowercase()} hangout.")
        }

        hangoutRepository.save(hangoutEntity.apply { this.status = HangoutStatus.COMPLETED })
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    @Transactional
    fun transitionHangoutsToOngoing() {
        hangoutRepository.transitionToOngoing(
            now = Instant.now(),
            ongoingStatus = HangoutStatus.ONGOING,
            activeStatuses = listOf(HangoutStatus.VOTING, HangoutStatus.SCHEDULED)
        )
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    fun cleanupGhostHangouts() {
        val cutoff = Instant.now().minus(30, ChronoUnit.DAYS)
        hangoutRepository.deleteGhostHangouts(cutoff)
    }

    private fun fetchSpotSafely(spotId: String?, userId: UserId): Spot? {
        if (spotId == null) return null
        return try {
            spotService.getSpotById(spotId, userId)
        } catch (e: Exception) {
            logger.warn("Failed to fetch spot $spotId for hangout", e)
            null
        }
    }
}
