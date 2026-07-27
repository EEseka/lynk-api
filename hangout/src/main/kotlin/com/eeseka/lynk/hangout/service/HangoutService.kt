package com.eeseka.lynk.hangout.service

import com.eeseka.lynk.common.domain.events.hangout.HangoutEvent
import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.common.infra.message_queue.EventPublisher
import com.eeseka.lynk.hangout.domain.event.HangoutCancelledEvent
import com.eeseka.lynk.hangout.domain.event.HangoutCompletedEvent
import com.eeseka.lynk.hangout.domain.event.HangoutCreatedEvent
import com.eeseka.lynk.hangout.domain.event.HangoutUpdatedEvent
import com.eeseka.lynk.hangout.domain.exception.HangoutAccessDeniedException
import com.eeseka.lynk.hangout.domain.exception.HangoutIllegalStateException
import com.eeseka.lynk.hangout.domain.exception.HangoutNotFoundException
import com.eeseka.lynk.hangout.domain.model.Hangout
import com.eeseka.lynk.hangout.domain.model.HangoutPreview
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.HangoutSummary
import com.eeseka.lynk.hangout.domain.model.HangoutVibe
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.infra.database.entities.HangoutEntity
import com.eeseka.lynk.hangout.infra.database.entities.HangoutParticipantEntity
import com.eeseka.lynk.hangout.infra.database.mappers.toHangout
import com.eeseka.lynk.hangout.infra.database.mappers.toHangoutPreview
import com.eeseka.lynk.hangout.infra.database.mappers.toHangoutSummary
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutParticipantRepository
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutRepository
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutUserRepository
import com.eeseka.lynk.spot.domain.model.Spot
import com.eeseka.lynk.spot.service.SpotService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
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
    private val spotService: SpotService,
    private val eventPublisher: EventPublisher,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    private val logger = LoggerFactory.getLogger(HangoutService::class.java)

    @Transactional
    fun createHangout(
        hostId: UserId,
        name: String,
        description: String?,
        vibe: HangoutVibe,
        scheduledAt: Instant,
        maxAttendees: Int?,
        spotId: String?
    ): Pair<Hangout, Spot?> {
        val hangoutUserEntity = hangoutUserRepository.findByIdOrNull(hostId)
            ?: throw HangoutAccessDeniedException("Complete your profile before creating a hangout.")

        val hangoutEntity = HangoutEntity(
            hostId = hostId,
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

        applicationEventPublisher.publishEvent(
            HangoutCreatedEvent(
                hangoutId = savedHangout.id,
                hostId = hostId
            )
        )

        return Pair(savedHangout, fetchSpotSafely(spotId, hostId))
    }

    @Transactional
    fun updateHangout(
        hostId: UserId,
        hangoutId: HangoutId,
        name: String,
        description: String?,
        vibe: HangoutVibe,
        scheduledAt: Instant,
        maxAttendees: Int?,
        spotId: String?
    ): Pair<Hangout, Spot?> {
        val hangoutEntity = hangoutRepository.findHangoutById(hangoutId, hostId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        if (hangoutEntity.hostId != hostId) {
            throw HangoutAccessDeniedException("Only the host can update this hangout.")
        }

        if (hangoutEntity.status != HangoutStatus.VOTING && hangoutEntity.status != HangoutStatus.SCHEDULED) {
            throw HangoutIllegalStateException("Cannot update a ${hangoutEntity.status.name.lowercase()} hangout.")
        }

        // TODO: block spot change / revert-to-voting once a non-host has paid — refund first (Phase 4)

        // Capacity can't be cut below people already occupying slots (ATTENDING + PENDING).
        if (maxAttendees != null) {
            val activeCount = hangoutParticipantRepository.countByHangoutIdAndRsvpStatusIn(
                hangoutId,
                listOf(RsvpStatus.ATTENDING, RsvpStatus.PENDING)
            )
            if (maxAttendees < activeCount) {
                throw HangoutIllegalStateException("Capacity can't be below the current $activeCount attendees.")
            }
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

        val hostDisplayName =
            hangoutEntity.participants.first { it.hangoutUser.userId == hostId }.hangoutUser.displayName
        val recipientIds = hangoutEntity.participants
            .filter { it.rsvpStatus == RsvpStatus.ATTENDING && it.hangoutUser.userId != hostId }
            .map { it.hangoutUser.userId }
            .toSet()
        // Push to attendees not in the lobby (schedule/spot may have changed).
        eventPublisher.publish(
            HangoutEvent.HangoutUpdated(
                hangoutId = hangoutId,
                hangoutName = hangoutEntity.name,
                recipientIds = recipientIds,
                hostDisplayName = hostDisplayName
            )
        )
        // Tell everyone in the live lobby to refresh their detail & list view.
        applicationEventPublisher.publishEvent(
            HangoutUpdatedEvent(
                hangoutId = hangoutId,
                hostDisplayName = hostDisplayName
            )
        )

        return Pair(savedHangout, fetchSpotSafely(spotId, hostId))
    }

    fun getHangoutDetails(userId: UserId, hangoutId: HangoutId): Pair<Hangout, Spot?> {
        val hangout = hangoutRepository.findHangoutById(hangoutId, userId)?.toHangout()
            ?: throw HangoutNotFoundException(hangoutId.toString())

        // Only confirmed attendees enter the lobby; PENDING/DECLINED are gated out.
        val isAttending = hangout.participants.any {
            it.userId == userId && it.rsvpStatus == RsvpStatus.ATTENDING
        }
        if (!isAttending) {
            throw HangoutAccessDeniedException("Accept the invite to view this hangout.")
        }

        return Pair(hangout, fetchSpotSafely(hangout.chosenSpotID, userId))
    }

    fun getHangoutPreview(userId: UserId, hangoutId: HangoutId): Pair<HangoutPreview, Spot?> {
        val hangoutEntity = hangoutRepository.findHangoutById(hangoutId, userId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        // Only a PENDING invitee sees the preview
        val isPending = hangoutEntity.participants.any {
            it.hangoutUser.userId == userId && it.rsvpStatus == RsvpStatus.PENDING
        }
        if (!isPending) {
            throw HangoutAccessDeniedException("This preview is only available for a pending invite.")
        }

        return Pair(hangoutEntity.toHangoutPreview(), fetchSpotSafely(hangoutEntity.chosenSpotId, userId))
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
            attendingStatus = RsvpStatus.ATTENDING,
            pageable = PageRequest.of(0, pageSize)
        ).content.map { it.toHangoutSummary() }
    }

    @Transactional
    fun cancelHangout(hostId: UserId, hangoutId: HangoutId) {
        val hangoutEntity = hangoutRepository.findHangoutById(hangoutId, hostId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        if (hangoutEntity.hostId != hostId) {
            throw HangoutAccessDeniedException("Only the host can cancel this hangout.")
        }

        if (hangoutEntity.status == HangoutStatus.COMPLETED || hangoutEntity.status == HangoutStatus.CANCELLED) {
            throw HangoutIllegalStateException("Cannot cancel a ${hangoutEntity.status.name.lowercase()} hangout.")
        }

        hangoutRepository.save(hangoutEntity.apply { this.status = HangoutStatus.CANCELLED })

        val hostDisplayName =
            hangoutEntity.participants.first { it.hangoutUser.userId == hostId }.hangoutUser.displayName
        val recipientIds = hangoutEntity.participants
            .filter { it.rsvpStatus == RsvpStatus.ATTENDING && it.hangoutUser.userId != hostId }
            .map { it.hangoutUser.userId }
            .toSet()
        // Push: attendees must know the plan is off, even if the app is closed.
        eventPublisher.publish(
            HangoutEvent.HangoutCancelled(
                hangoutId = hangoutId,
                hangoutName = hangoutEntity.name,
                recipientIds = recipientIds,
                hostDisplayName = hostDisplayName
            )
        )
        // Terminal: lobby closes for everyone still connected.
        applicationEventPublisher.publishEvent(
            HangoutCancelledEvent(
                hangoutId = hangoutId,
                hostDisplayName = hostDisplayName
            )
        )

        // TODO: refund every attendee who has paid (Phase 4)
    }

    @Transactional
    fun completeHangout(hostId: UserId, hangoutId: HangoutId) {
        val hangoutEntity = hangoutRepository.findHangoutById(hangoutId, hostId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        if (hangoutEntity.hostId != hostId) {
            throw HangoutAccessDeniedException("Only the host can complete this hangout.")
        }

        if (hangoutEntity.status != HangoutStatus.ONGOING) {
            throw HangoutIllegalStateException("Cannot complete a ${hangoutEntity.status.name.lowercase()} hangout.")
        }

        hangoutRepository.save(hangoutEntity.apply { this.status = HangoutStatus.COMPLETED })

        val hostDisplayName =
            hangoutEntity.participants.first { it.hangoutUser.userId == hostId }.hangoutUser.displayName
        val recipientIds = hangoutEntity.participants
            .filter { it.rsvpStatus == RsvpStatus.ATTENDING && it.hangoutUser.userId != hostId }
            .map { it.hangoutUser.userId }
            .toSet()
        // Push: attendees can review.
        eventPublisher.publish(
            HangoutEvent.HangoutCompleted(
                hangoutId = hangoutId,
                hangoutName = hangoutEntity.name,
                recipientIds = recipientIds,
                hostDisplayName = hostDisplayName
            )
        )
        // Terminal: lobby closes for everyone still connected.
        applicationEventPublisher.publishEvent(
            HangoutCompletedEvent(
                hangoutId = hangoutId,
                hostDisplayName = hostDisplayName
            )
        )
    }

    // Lean checks for the live-lobby voting gate.
    fun isVotingOpen(hangoutId: HangoutId): Boolean =
        hangoutRepository.findStatusById(hangoutId) == HangoutStatus.VOTING

    fun isHost(hangoutId: HangoutId, userId: UserId): Boolean =
        hangoutRepository.findHostIdById(hangoutId) == userId

    // Lock the winning spot: the lobby calls this when the host closes voting.
    @Transactional
    fun chooseSpot(hostId: UserId, hangoutId: HangoutId, spotId: String) {
        val hangoutEntity = hangoutRepository.findHangoutById(hangoutId, hostId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        if (hangoutEntity.hostId != hostId) {
            throw HangoutAccessDeniedException("Only the host can lock the spot.")
        }
        if (hangoutEntity.status != HangoutStatus.VOTING) {
            throw HangoutIllegalStateException("Voting is not open for this hangout.")
        }

        hangoutRepository.save(
            hangoutEntity.apply {
                this.chosenSpotId = spotId
                this.status = HangoutStatus.SCHEDULED
            }
        )

        val hostDisplayName =
            hangoutEntity.participants.first { it.hangoutUser.userId == hostId }.hangoutUser.displayName
        val recipientIds = hangoutEntity.participants
            .filter { it.rsvpStatus == RsvpStatus.ATTENDING && it.hangoutUser.userId != hostId }
            .map { it.hangoutUser.userId }
            .toSet()
        // Push attendees not in the lobby: the spot is locked, the hangout is now scheduled.
        eventPublisher.publish(
            HangoutEvent.HangoutUpdated(
                hangoutId = hangoutId,
                hangoutName = hangoutEntity.name,
                recipientIds = recipientIds,
                hostDisplayName = hostDisplayName
            )
        )
        // Refresh the live lobby (detail now shows the chosen spot + SCHEDULED status).
        applicationEventPublisher.publishEvent(
            HangoutUpdatedEvent(
                hangoutId = hangoutId,
                hostDisplayName = hostDisplayName
            )
        )
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    @Transactional
    fun transitionHangoutsToOngoing() {
        hangoutRepository.transitionToOngoing(
            now = Instant.now(),
            ongoingStatus = HangoutStatus.ONGOING,
            activeStatuses = listOf(HangoutStatus.SCHEDULED)
        )

        // TODO: notify every participant that the hangout has started
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
