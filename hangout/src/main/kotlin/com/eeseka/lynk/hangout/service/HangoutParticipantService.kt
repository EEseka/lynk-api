package com.eeseka.lynk.hangout.service

import com.eeseka.lynk.common.domain.events.hangout.HangoutEvent
import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.common.infra.message_queue.EventPublisher
import com.eeseka.lynk.hangout.domain.HangoutConstants.MAX_ATTENDEES
import com.eeseka.lynk.hangout.domain.event.HangoutInviteWithdrawnEvent
import com.eeseka.lynk.hangout.domain.event.HangoutParticipantInvitedEvent
import com.eeseka.lynk.hangout.domain.event.HangoutParticipantLeftEvent
import com.eeseka.lynk.hangout.domain.event.HangoutRsvpUpdatedEvent
import com.eeseka.lynk.hangout.domain.exception.*
import com.eeseka.lynk.hangout.domain.model.HangoutParticipant
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.infra.database.entities.HangoutParticipantEntity
import com.eeseka.lynk.hangout.infra.database.mappers.toHangoutParticipant
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutParticipantRepository
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutRepository
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutUserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class HangoutParticipantService(
    private val hangoutRepository: HangoutRepository,
    private val hangoutParticipantRepository: HangoutParticipantRepository,
    private val hangoutUserRepository: HangoutUserRepository,
    private val eventPublisher: EventPublisher,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    // Statuses in which the roster can still change (invite / rsvp)
    private val openStatuses = listOf(HangoutStatus.VOTING, HangoutStatus.SCHEDULED)

    // Statuses that occupy a slot toward maxAttendees (PENDING reserves)
    private val activeStatuses = listOf(RsvpStatus.ATTENDING, RsvpStatus.PENDING)

    // Live lobby needs every hangout a user attends
    fun findAttendingHangoutIds(userId: UserId): List<HangoutId> =
        hangoutParticipantRepository.findHangoutIdsByAttendee(
            userId = userId,
            rsvpStatus = RsvpStatus.ATTENDING
        )

    @Transactional
    fun inviteParticipant(
        hostId: UserId,
        hangoutId: HangoutId,
        inviteeId: UserId
    ): HangoutParticipant {
        val hangout = hangoutRepository.findHangoutById(hangoutId, hostId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        if (hangout.hostId != hostId) {
            throw HangoutAccessDeniedException("Only the host can invite participants.")
        }

        if (hangout.status !in openStatuses) {
            throw HangoutIllegalStateException("Cannot invite to a ${hangout.status.name.lowercase()} hangout.")
        }

        val existing = hangout.participants.find { it.hangoutUser.userId == inviteeId }
        if (existing != null && existing.rsvpStatus != RsvpStatus.DECLINED) {
            throw HangoutIllegalStateException("This user is already invited.")
        }

        val max = hangout.maxAttendees ?: MAX_ATTENDEES
        val activeCount = hangoutParticipantRepository.countByHangoutIdAndRsvpStatusIn(hangoutId, activeStatuses)
        if (activeCount >= max) {
            throw HangoutIllegalStateException(
                if (hangout.maxAttendees == null) "A hangout can have at most $MAX_ATTENDEES people."
                else "This hangout is full."
            )
        }

        val savedParticipant = if (existing != null) {
            // Re-invite: reopen the declined row with a fresh PENDING state
            hangoutParticipantRepository.save(
                existing.apply {
                    rsvpStatus = RsvpStatus.PENDING
                    hasPaid = false
                }
            ).toHangoutParticipant()
        } else {
            val inviteeEntity = hangoutUserRepository.findByIdOrNull(inviteeId)
                ?: throw HangoutUserNotFoundException(inviteeId.toString())
            hangoutParticipantRepository.save(
                HangoutParticipantEntity(
                    hangout = hangout,
                    hangoutUser = inviteeEntity,
                    rsvpStatus = RsvpStatus.PENDING,
                    hasPaid = false
                )
            ).toHangoutParticipant()
        }

        // Host is always an ATTENDING participant, so first { } is safe and non-null.
        val hostDisplayName = hangout.participants
            .first { it.hangoutUser.userId == hangout.hostId }
            .hangoutUser.displayName

        eventPublisher.publish(
            HangoutEvent.ParticipantInvited(
                hangoutId = hangoutId,
                hangoutName = hangout.name,
                inviteeId = inviteeId,
                hostDisplayName = hostDisplayName
            )
        )

        applicationEventPublisher.publishEvent(
            HangoutParticipantInvitedEvent(
                hangoutId = hangoutId,
                userId = savedParticipant.userId,
                displayName = savedParticipant.displayName
            )
        )

        return savedParticipant
    }

    @Transactional
    fun updateRsvp(
        userId: UserId,
        hangoutId: HangoutId,
        status: RsvpStatus
    ): HangoutParticipant {
        if (status != RsvpStatus.ATTENDING && status != RsvpStatus.DECLINED) {
            throw HangoutIllegalArgumentException("RSVP must be ATTENDING or DECLINED.")
        }

        val hangout = hangoutRepository.findHangoutById(hangoutId, userId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        if (hangout.status !in openStatuses) {
            throw HangoutIllegalStateException("Cannot RSVP to a ${hangout.status.name.lowercase()} hangout.")
        }

        val participant = hangout.participants.find { it.hangoutUser.userId == userId }
            ?: throw HangoutUserNotFoundException(userId.toString())

        if (participant.rsvpStatus != RsvpStatus.PENDING) {
            throw HangoutIllegalStateException("You have already responded to this invite.")
        }

        // Slot was already reserved as PENDING at invite, so no capacity re-check needed.
        // participantCount tracks confirmed attendees only.
        if (status == RsvpStatus.ATTENDING) {
            hangoutRepository.save(
                hangout.apply { participantCount += 1 }
            )
        }

        val savedParticipant = hangoutParticipantRepository.save(
            participant.apply { rsvpStatus = status }
        ).toHangoutParticipant()

        applicationEventPublisher.publishEvent(
            HangoutRsvpUpdatedEvent(
                hangoutId = hangoutId,
                userId = savedParticipant.userId,
                displayName = savedParticipant.displayName,
                rsvpStatus = savedParticipant.rsvpStatus
            )
        )

        return savedParticipant
    }

    @Transactional
    fun withdrawParticipantInvite(
        hostId: UserId,
        hangoutId: HangoutId,
        targetUserId: UserId
    ) {
        val hangout = hangoutRepository.findHangoutById(hangoutId, hostId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        if (hangout.hostId != hostId) {
            throw HangoutAccessDeniedException("Only the host can withdraw invites.")
        }

        if (hangout.status !in openStatuses) {
            throw HangoutIllegalStateException("Cannot change participants of a ${hangout.status.name.lowercase()} hangout.")
        }

        val participant = hangout.participants.find { it.hangoutUser.userId == targetUserId }
            ?: throw HangoutUserNotFoundException(targetUserId.toString())

        if (participant.rsvpStatus != RsvpStatus.PENDING) {
            throw HangoutIllegalStateException("Only a pending invite can be withdrawn.")
        }

        val withdrawnDisplayName = participant.hangoutUser.displayName

        hangoutParticipantRepository.delete(participant)

        // Host is always an ATTENDING participant, so first { } is safe and non-null.
        val hostDisplayName = hangout.participants
            .first { it.hangoutUser.userId == hangout.hostId }
            .hangoutUser.displayName

        eventPublisher.publish(
            HangoutEvent.InviteCancelled(
                hangoutId = hangoutId,
                hangoutName = hangout.name,
                inviteeId = targetUserId,
                hostDisplayName = hostDisplayName
            )
        )

        applicationEventPublisher.publishEvent(
            HangoutInviteWithdrawnEvent(
                hangoutId = hangoutId,
                userId = targetUserId,
                displayName = withdrawnDisplayName
            )
        )
    }

    @Transactional
    fun leaveHangout(
        userId: UserId,
        hangoutId: HangoutId
    ) {
        val hangout = hangoutRepository.findHangoutById(hangoutId, userId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        if (userId == hangout.hostId) {
            throw HangoutIllegalStateException("The host cannot leave; cancel the hangout instead.")
        }

        if (hangout.status !in openStatuses) {
            throw HangoutIllegalStateException("Cannot leave a ${hangout.status.name.lowercase()} hangout.")
        }

        val participant = hangout.participants.find { it.hangoutUser.userId == userId }
            ?: throw HangoutUserNotFoundException(userId.toString())

        // Leave is only for confirmed attendees. A PENDING user declines via updateRsvp instead.
        if (participant.rsvpStatus != RsvpStatus.ATTENDING) {
            throw HangoutIllegalStateException("You are not attending this hangout.")
        }

        // Attendees are the only ones counted; free the slot on the way out.
        hangoutRepository.save(
            hangout.apply { participantCount -= 1 }
        )

        hangoutParticipantRepository.save(
            participant.apply { rsvpStatus = RsvpStatus.DECLINED }
        )

        val leaverDisplayName = participant.hangoutUser.displayName
        eventPublisher.publish(
            HangoutEvent.ParticipantLeft(
                hangoutId = hangoutId,
                hangoutName = hangout.name,
                hostId = hangout.hostId,
                leaverDisplayName = leaverDisplayName
            )
        )

        applicationEventPublisher.publishEvent(
            HangoutParticipantLeftEvent(
                hangoutId = hangoutId,
                userId = userId,
                displayName = leaverDisplayName
            )
        )

        // TODO: if the participant had paid, trigger refund (Phase 4)
    }
}
