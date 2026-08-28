package com.eeseka.lynk.hangout.service

import com.eeseka.lynk.common.domain.events.hangout.HangoutEvent
import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.common.infra.message_queue.EventPublisher
import com.eeseka.lynk.hangout.domain.HangoutConstants.MAX_ATTENDEES
import com.eeseka.lynk.hangout.domain.event.HangoutInviteWithdrawnEvent
import com.eeseka.lynk.hangout.domain.event.HangoutNonPayerRemovedEvent
import com.eeseka.lynk.hangout.domain.event.HangoutParticipantInvitedEvent
import com.eeseka.lynk.hangout.domain.event.HangoutParticipantLeftEvent
import com.eeseka.lynk.hangout.domain.event.HangoutPaymentReceivedEvent
import com.eeseka.lynk.hangout.domain.event.HangoutRsvpUpdatedEvent
import com.eeseka.lynk.hangout.domain.event.HangoutUpdatedEvent
import com.eeseka.lynk.hangout.domain.exception.HangoutAccessDeniedException
import com.eeseka.lynk.hangout.domain.exception.HangoutIllegalArgumentException
import com.eeseka.lynk.hangout.domain.exception.HangoutIllegalStateException
import com.eeseka.lynk.hangout.domain.exception.HangoutNotFoundException
import com.eeseka.lynk.hangout.domain.exception.HangoutParticipantNotFoundException
import com.eeseka.lynk.hangout.domain.exception.HangoutUserNotFoundException
import com.eeseka.lynk.hangout.domain.model.HangoutParticipant
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.PaymentState
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.infra.database.entities.HangoutParticipantEntity
import com.eeseka.lynk.hangout.infra.database.mappers.toHangoutParticipant
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutParticipantRepository
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutRepository
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutUserRepository
import org.slf4j.LoggerFactory
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
    private val logger = LoggerFactory.getLogger(javaClass)

    // Statuses in which the roster can still change (invite / rsvp)
    private val openStatuses = listOf(HangoutStatus.VOTING, HangoutStatus.SCHEDULED)

    // Statuses that occupy a slot toward maxAttendees (PENDING reserves)
    private val activeStatuses = listOf(RsvpStatus.ATTENDING, RsvpStatus.PENDING)

    // Whether someone is still going, for callers that need nothing else about them.
    fun findRsvpStatus(hangoutId: HangoutId, userId: UserId): RsvpStatus? =
        hangoutParticipantRepository.findRsvpStatusByHangoutIdAndUserId(
            hangoutId = hangoutId,
            userId = userId
        )

    // Live lobby needs every hangout a user attends
    fun findAttendingHangoutIds(userId: UserId): List<HangoutId> =
        hangoutParticipantRepository.findHangoutIdsByUserIdAndRsvpStatus(
            userId = userId,
            rsvpStatus = RsvpStatus.ATTENDING
        )

    @Transactional
    fun markParticipantAsPaid(hangoutId: HangoutId, userId: UserId) {
        val participant = hangoutParticipantRepository
            .findByHangoutIdAndHangoutUserUserId(hangoutId = hangoutId, userId = userId)
            ?: throw HangoutParticipantNotFoundException(userId.toString())

        if (participant.hasPaid) return

        hangoutParticipantRepository.save(
            participant.apply { hasPaid = true }
        )

        markReadyForPayoutIfEveryoneHasPaid(hangoutId)

        val hangout = participant.hangout
        eventPublisher.publish(
            HangoutEvent.PaymentReceived(
                hangoutId = hangoutId,
                hangoutName = hangout.name,
                hostId = hangout.hostId,
                payerId = userId,
                payerDisplayName = participant.hangoutUser.displayName,
                amountKobo = hangout.payment?.costPerPersonKobo
            )
        )

        applicationEventPublisher.publishEvent(
            HangoutPaymentReceivedEvent(
                hangoutId = hangoutId,
                userId = userId,
                displayName = participant.hangoutUser.displayName
            )
        )
    }

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

        // Past the deadline a payer can still leave but is past refunding. Refilling their seat would
        // charge a second person the same frozen share, leaving the host holding two payments for one.
        val payment = hangout.payment
        if (payment != null && payment.state != PaymentState.COLLECTING) {
            throw HangoutIllegalStateException("Payments for this hangout are already settled.")
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

        // Once the bill is split, the frozen headcount is the real ceiling, whatever capacity says. A
        // seat only opens when someone leaves and is refunded; anyone past it would owe an extra share.
        if (payment != null && activeCount >= payment.splitHeadcount) {
            throw HangoutIllegalStateException("Every share of this bill is already taken.")
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
                userId = savedParticipant.user.userId,
                displayName = savedParticipant.user.displayName
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
            ?: throw HangoutParticipantNotFoundException(userId.toString())

        if (participant.rsvpStatus != RsvpStatus.PENDING) {
            throw HangoutIllegalStateException("You have already responded to this invite.")
        }

        val payment = hangout.payment
        if (status == RsvpStatus.ATTENDING && payment != null) {
            if (payment.state != PaymentState.COLLECTING) {
                throw HangoutIllegalStateException("Payments for this hangout are already settled.")
            }

            val attendingCount = hangout.participants.count { it.rsvpStatus == RsvpStatus.ATTENDING }
            if (attendingCount >= payment.splitHeadcount) {
                throw HangoutIllegalStateException("Every share of this bill is already taken.")
            }
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
                userId = savedParticipant.user.userId,
                displayName = savedParticipant.user.displayName,
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
            ?: throw HangoutParticipantNotFoundException(targetUserId.toString())

        if (participant.rsvpStatus != RsvpStatus.PENDING) {
            throw HangoutIllegalStateException("Only a pending invite can be withdrawn.")
        }

        val withdrawnDisplayName = participant.hangoutUser.displayName

        hangout.participants.remove(participant)

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
    fun removeNonPayers(hostId: UserId, hangoutId: HangoutId) {
        val hangout = hangoutRepository.findHangoutById(hangoutId, hostId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        if (hangout.hostId != hostId) {
            throw HangoutAccessDeniedException("Only the host can remove people who did not pay.")
        }

        val nonPayers = hangout.participants.filter {
            it.rsvpStatus == RsvpStatus.ATTENDING && !it.hasPaid && it.hangoutUser.userId != hostId
        }

        if (nonPayers.isEmpty()) return

        val hostDisplayName = hangout.participants
            .first { it.hangoutUser.userId == hangout.hostId }
            .hangoutUser.displayName

        nonPayers.forEach { participant ->
            val removedDisplayName = participant.hangoutUser.displayName
            hangout.participants.remove(participant)

            eventPublisher.publish(
                HangoutEvent.RemovedForNonPayment(
                    hangoutId = hangoutId,
                    hangoutName = hangout.name,
                    participantId = participant.hangoutUser.userId,
                    hostDisplayName = hostDisplayName
                )
            )
            applicationEventPublisher.publishEvent(
                HangoutNonPayerRemovedEvent(
                    hangoutId = hangoutId,
                    userId = participant.hangoutUser.userId,
                    displayName = removedDisplayName
                )
            )
        }

        hangoutRepository.save(
            hangout.apply { participantCount -= nonPayers.size }
        )

        logger.info("Host removed {} non-payers from hangout {}", nonPayers.size, hangoutId)
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
            ?: throw HangoutParticipantNotFoundException(userId.toString())

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

        // The person who just walked may have been the last one who still owed money. Nobody is going
        // to pay now, so without this the host waits for a decision they can no longer be asked to make.
        markReadyForPayoutIfEveryoneHasPaid(hangoutId)

        val leaverDisplayName = participant.hangoutUser.displayName
        eventPublisher.publish(
            HangoutEvent.ParticipantLeft(
                hangoutId = hangoutId,
                hangoutName = hangout.name,
                hostId = hangout.hostId,
                leaverId = userId,
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
    }

    private fun markReadyForPayoutIfEveryoneHasPaid(hangoutId: HangoutId) {
        val hangout = hangoutRepository.findByIdOrNull(hangoutId) ?: return

        // Cancelling leaves the payment state untouched, so a canceled hangout can still be sitting
        // here. There is no payout coming for it, and this money is owed back rather than readied.
        if (hangout.status == HangoutStatus.CANCELLED) return
        val payment = hangout.payment ?: return
        if (payment.state != PaymentState.AWAITING_HOST_DECISION) return

        val everyoneHasPaid = hangout.participants
            .filter { it.rsvpStatus == RsvpStatus.ATTENDING }
            .all { it.hasPaid }
        if (!everyoneHasPaid) return

        hangoutRepository.save(
            hangout.apply { payment.state = PaymentState.READY_FOR_PAYOUT }
        )

        val hostDisplayName = hangout.participants
            .first { it.hangoutUser.userId == hangout.hostId }
            .hangoutUser.displayName
        // Refresh the live lobby, so the host stops being asked to decide.
        applicationEventPublisher.publishEvent(
            HangoutUpdatedEvent(
                hangoutId = hangoutId,
                hostDisplayName = hostDisplayName
            )
        )

        logger.info("The last payment for hangout {} landed, so it no longer needs a decision", hangoutId)
    }
}