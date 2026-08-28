package com.eeseka.lynk.hangout.service

import com.eeseka.lynk.common.domain.events.hangout.HangoutChangeKind
import com.eeseka.lynk.common.domain.events.hangout.HangoutEvent
import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.common.infra.message_queue.EventPublisher
import com.eeseka.lynk.hangout.domain.HangoutConstants.MIN_ATTENDEES_FOR_PAYMENTS
import com.eeseka.lynk.hangout.domain.HangoutConstants.MIN_COST_PER_PERSON_KOBO
import com.eeseka.lynk.hangout.domain.event.HangoutCancelledEvent
import com.eeseka.lynk.hangout.domain.event.HangoutCompletedEvent
import com.eeseka.lynk.hangout.domain.event.HangoutCreatedEvent
import com.eeseka.lynk.hangout.domain.event.HangoutPaymentDeadlineResolvedEvent
import com.eeseka.lynk.hangout.domain.event.HangoutPayoutOutcomeEvent
import com.eeseka.lynk.hangout.domain.event.HangoutUpdatedEvent
import com.eeseka.lynk.hangout.domain.exception.HangoutAccessDeniedException
import com.eeseka.lynk.hangout.domain.exception.HangoutIllegalArgumentException
import com.eeseka.lynk.hangout.domain.exception.HangoutIllegalStateException
import com.eeseka.lynk.hangout.domain.exception.HangoutNotFoundException
import com.eeseka.lynk.hangout.domain.model.Hangout
import com.eeseka.lynk.hangout.domain.model.HangoutPreview
import com.eeseka.lynk.hangout.domain.model.HangoutStats
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.HangoutSummary
import com.eeseka.lynk.hangout.domain.model.HangoutVibe
import com.eeseka.lynk.hangout.domain.model.PaymentState
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.infra.database.entities.HangoutEntity
import com.eeseka.lynk.hangout.infra.database.entities.HangoutParticipantEntity
import com.eeseka.lynk.hangout.infra.database.entities.HangoutPaymentEntity
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
    private val logger = LoggerFactory.getLogger(javaClass)

    private val cancellationRecipientStatuses = listOf(RsvpStatus.ATTENDING, RsvpStatus.PENDING)

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

        val hasNonHostPaid = hangoutEntity.participants.any {
            it.hasPaid && it.hangoutUser.userId != hostId
        }
        if (hasNonHostPaid && spotId != hangoutEntity.chosenSpotId) {
            throw HangoutIllegalStateException("The spot cannot change once someone has paid for it.")
        }

        val paymentDeadline = hangoutEntity.payment?.deadline
        if (paymentDeadline != null && scheduledAt.isBefore(paymentDeadline)) {
            throw HangoutIllegalArgumentException("The hangout cannot start before the payment deadline.")
        }

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

        val cleanName = name.trim()
        val cleanDescription = description?.trim()

        val changes = buildSet {
            if (hangoutEntity.name != cleanName ||
                hangoutEntity.description != cleanDescription ||
                hangoutEntity.vibe != vibe ||
                hangoutEntity.maxAttendees != maxAttendees
            ) {
                add(HangoutChangeKind.DETAILS_EDITED)
            }
            if (hangoutEntity.scheduledAt != scheduledAt) {
                add(HangoutChangeKind.SCHEDULE_CHANGED)
            }
            if (hangoutEntity.chosenSpotId != spotId) {
                add(
                    if (spotId == null) HangoutChangeKind.VOTING_REOPENED
                    else HangoutChangeKind.SPOT_CHOSEN
                )
            }
        }

        val savedHangout = hangoutRepository.save(
            hangoutEntity.apply {
                this.name = cleanName
                this.description = cleanDescription
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

        if (changes.isNotEmpty()) {
            eventPublisher.publish(
                HangoutEvent.HangoutUpdated(
                    hangoutId = hangoutId,
                    hangoutName = cleanName,
                    recipientIds = recipientIds,
                    hostDisplayName = hostDisplayName,
                    changes = changes
                )
            )
        }
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
            it.user.userId == userId && it.rsvpStatus == RsvpStatus.ATTENDING
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
        return hangoutRepository.findByParticipantAndCreatedAtBeforeAndStatusInAndVibeAndNameContaining(
            userId = userId,
            rsvpStatus = RsvpStatus.ATTENDING,
            before = before ?: Instant.now(),
            statuses = statuses,
            vibe = vibe,
            query = query,
            pageable = PageRequest.of(0, pageSize)
        ).content.map { it.toHangoutSummary() }
    }

    fun getStats(userId: UserId): HangoutStats {
        return HangoutStats(
            hostedCount = hangoutRepository.countByHostIdAndStatus(
                userId = userId,
                completedStatus = HangoutStatus.COMPLETED
            ),
            attendedCount = hangoutParticipantRepository.countAttendedAsGuestByUserIdAndStatus(
                userId = userId,
                attendingStatus = RsvpStatus.ATTENDING,
                completedStatus = HangoutStatus.COMPLETED
            )
        )
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

        hangoutRepository.save(hangoutEntity.apply { status = HangoutStatus.CANCELLED })

        val hostDisplayName =
            hangoutEntity.participants.first { it.hangoutUser.userId == hostId }.hangoutUser.displayName
        val recipientIds = hangoutEntity.participants
            .filter { it.rsvpStatus in cancellationRecipientStatuses && it.hangoutUser.userId != hostId }
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

        hangoutRepository.save(hangoutEntity.apply { status = HangoutStatus.COMPLETED })

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

    // Where a hangout has got to, for callers that only need the status and none of the rest of it.
    fun findHangoutStatus(hangoutId: HangoutId): HangoutStatus? =
        hangoutRepository.findStatusById(hangoutId)

    // What a hangout is called, for callers that only need it to name the hangout in a notification.
    fun findHangoutName(hangoutId: HangoutId): String? =
        hangoutRepository.findNameById(hangoutId)

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
                chosenSpotId = spotId
                status = HangoutStatus.SCHEDULED
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
                hostDisplayName = hostDisplayName,
                changes = setOf(HangoutChangeKind.SPOT_CHOSEN)
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

    /**
     * Hangouts whose money is collected and waiting to be sent to the host. Drives the payout sweep.
     */
    fun findHangoutIdsReadyForPayout(): List<HangoutId> =
        hangoutRepository.findIdsByPaymentStateAndStatusNot(
            paymentState = PaymentState.READY_FOR_PAYOUT,
            excludedStatus = HangoutStatus.CANCELLED
        )

    fun findHangoutIdsPayingOut(): List<HangoutId> =
        hangoutRepository.findIdsByPaymentState(PaymentState.PAYING_OUT)


    fun findHangoutIdsWithPayoutFailed(): List<HangoutId> =
        hangoutRepository.findIdsByPaymentState(PaymentState.PAYOUT_FAILED)


    @Transactional
    fun markPayoutInFlight(hangoutId: HangoutId) {
        val hangoutEntity = hangoutRepository.findByIdOrNull(hangoutId)
            ?: throw HangoutNotFoundException(hangoutId.toString())
        val payment = hangoutEntity.payment
            ?: throw HangoutIllegalStateException("Payments were never turned on for this hangout.")

        hangoutRepository.save(
            hangoutEntity.apply { payment.state = PaymentState.PAYING_OUT }
        )
    }

    /**
     * Records what happened to a payout. Called by the payment module, which is the only thing that
     * knows whether the money actually moved.
     */
    @Transactional
    fun recordPayoutOutcome(
        hangoutId: HangoutId,
        succeeded: Boolean,
        reference: String?,
        amountKobo: Long
    ) {
        val hangoutEntity = hangoutRepository.findByIdOrNull(hangoutId)
            ?: throw HangoutNotFoundException(hangoutId.toString())
        val payment = hangoutEntity.payment
            ?: throw HangoutIllegalStateException("Payments were never turned on for this hangout.")

        hangoutRepository.save(
            hangoutEntity.apply {
                payment.state = if (succeeded) PaymentState.PAID_OUT else PaymentState.PAYOUT_FAILED
            }
        )

        eventPublisher.publish(
            HangoutEvent.PayoutOutcome(
                hangoutId = hangoutId,
                hangoutName = hangoutEntity.name,
                hostId = hangoutEntity.hostId,
                succeeded = succeeded,
                reference = reference,
                amountKobo = amountKobo
            )
        )
        applicationEventPublisher.publishEvent(
            HangoutPayoutOutcomeEvent(
                hangoutId = hangoutId,
                hostId = hangoutEntity.hostId,
                succeeded = succeeded
            )
        )
    }

    /**
     * Puts a failed payout back in the queue, so the sweep tries it again.
     */
    @Transactional
    fun retryPayout(hostId: UserId, hangoutId: HangoutId) {
        val hangoutEntity = hangoutRepository.findHangoutById(hangoutId, hostId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        if (hangoutEntity.hostId != hostId) {
            throw HangoutAccessDeniedException("Only the host can retry their own payout.")
        }
        val payment = hangoutEntity.payment
        if (payment?.state != PaymentState.PAYOUT_FAILED) {
            throw HangoutIllegalStateException("There is no failed payout to retry.")
        }

        hangoutRepository.save(
            hangoutEntity.apply { payment.state = PaymentState.READY_FOR_PAYOUT }
        )
    }

    /**
     * Where this hangout's money has got to, for callers that only need to know whether it is still
     * reachable. No participant check: this says nothing private.
     */
    fun findPaymentState(hangoutId: HangoutId): PaymentState? =
        hangoutRepository.findByIdOrNull(hangoutId)?.payment?.state

    /**
     * The hangout on its own, without the trip to Google for the spot. For callers that need the
     * participants and the money fields and nothing else.
     */
    fun getHangout(userId: UserId, hangoutId: HangoutId): Hangout {
        val hangoutEntity = hangoutRepository.findHangoutById(hangoutId, userId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        return hangoutEntity.toHangout()
    }

    @Transactional
    fun enablePayments(
        hostId: UserId,
        hangoutId: HangoutId,
        totalCostKobo: Long,
        paymentDeadline: Instant
    ): Long {
        val hangoutEntity = hangoutRepository.findHangoutById(hangoutId, hostId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        assertPaymentsCanBeEnabled(
            hangoutEntity = hangoutEntity,
            hostId = hostId,
            totalCostKobo = totalCostKobo,
            paymentDeadline = paymentDeadline
        )

        val attendingCount = hangoutEntity.participants.count { it.rsvpStatus == RsvpStatus.ATTENDING }

        // Integer division leaves a remainder of at most a few kobo.
        // It falls to the host rather than being spread around, so the guests' shares are identical and still sum to no more than the total.
        val costPerPersonKobo = totalCostKobo / attendingCount

        hangoutRepository.save(
            hangoutEntity.apply {
                payment = HangoutPaymentEntity(
                    totalCostKobo = totalCostKobo,
                    costPerPersonKobo = costPerPersonKobo,
                    splitHeadcount = attendingCount,
                    deadline = paymentDeadline,
                    state = PaymentState.COLLECTING
                )
            }
        )

        val hostDisplayName =
            hangoutEntity.participants.first { it.hangoutUser.userId == hostId }.hangoutUser.displayName
        val recipientIds = hangoutEntity.participants
            .filter { it.rsvpStatus == RsvpStatus.ATTENDING && it.hangoutUser.userId != hostId }
            .map { it.hangoutUser.userId }
            .toSet()
        // Push attendees who are not in the lobby: they now owe money and have a date to pay by.
        eventPublisher.publish(
            HangoutEvent.HangoutUpdated(
                hangoutId = hangoutId,
                hangoutName = hangoutEntity.name,
                recipientIds = recipientIds,
                hostDisplayName = hostDisplayName,
                changes = setOf(HangoutChangeKind.PAYMENTS_ENABLED)
            )
        )
        // Refresh the live lobby so the detail re-fetch picks up the share and the deadline.
        applicationEventPublisher.publishEvent(
            HangoutUpdatedEvent(
                hangoutId = hangoutId,
                hostDisplayName = hostDisplayName
            )
        )

        return costPerPersonKobo
    }

    fun assertPaymentsCanBeEnabled(
        hostId: UserId,
        hangoutId: HangoutId,
        totalCostKobo: Long,
        paymentDeadline: Instant
    ) {
        val hangoutEntity = hangoutRepository.findHangoutById(hangoutId, hostId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        assertPaymentsCanBeEnabled(
            hangoutEntity = hangoutEntity,
            hostId = hostId,
            totalCostKobo = totalCostKobo,
            paymentDeadline = paymentDeadline
        )
    }

    @Transactional
    fun changePaymentDeadline(hostId: UserId, hangoutId: HangoutId, newDeadline: Instant) {
        val hangoutEntity = hangoutRepository.findHangoutById(hangoutId, hostId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        if (hangoutEntity.hostId != hostId) {
            throw HangoutAccessDeniedException("Only the host can decide what happens at the deadline.")
        }
        val payment = hangoutEntity.payment
        if (payment?.state != PaymentState.COLLECTING &&
            payment?.state != PaymentState.AWAITING_HOST_DECISION
        ) {
            throw HangoutIllegalStateException("This hangout is no longer collecting payments.")
        }
        if (hangoutEntity.status == HangoutStatus.CANCELLED) {
            throw HangoutIllegalStateException("Cannot decide the payment for a cancelled hangout.")
        }

        if (!newDeadline.isAfter(Instant.now())) {
            throw HangoutIllegalArgumentException("The new deadline must be in the future.")
        }
        if (newDeadline.isAfter(hangoutEntity.scheduledAt)) {
            throw HangoutIllegalArgumentException("The payment deadline must be on or before the hangout date.")
        }

        hangoutRepository.save(
            hangoutEntity.apply {
                payment.deadline = newDeadline
                payment.state = PaymentState.COLLECTING
            }
        )

        val hostDisplayName =
            hangoutEntity.participants.first { it.hangoutUser.userId == hostId }.hangoutUser.displayName
        val recipientIds = hangoutEntity.participants
            .filter { it.rsvpStatus == RsvpStatus.ATTENDING && it.hangoutUser.userId != hostId }
            .map { it.hangoutUser.userId }
            .toSet()
        // Push attendees not in the lobby: the date their money is due by has moved.
        eventPublisher.publish(
            HangoutEvent.PaymentDeadlineChanged(
                hangoutId = hangoutId,
                hangoutName = hangoutEntity.name,
                recipientIds = recipientIds,
                hostDisplayName = hostDisplayName,
                newDeadline = newDeadline
            )
        )
        // Refresh the live lobby: the date everyone has to pay by just moved.
        applicationEventPublisher.publishEvent(
            HangoutUpdatedEvent(
                hangoutId = hangoutId,
                hostDisplayName = hostDisplayName
            )
        )
    }

    @Transactional
    fun proceedWithoutFullPayment(hostId: UserId, hangoutId: HangoutId) {
        val hangoutEntity = hangoutRepository.findHangoutById(hangoutId, hostId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        if (hangoutEntity.hostId != hostId) {
            throw HangoutAccessDeniedException("Only the host can decide what happens at the deadline.")
        }
        val payment = hangoutEntity.payment
        if (payment?.state != PaymentState.AWAITING_HOST_DECISION) {
            throw HangoutIllegalStateException("This hangout is not waiting on a payment decision.")
        }
        if (hangoutEntity.status == HangoutStatus.CANCELLED) {
            throw HangoutIllegalStateException("Cannot decide the payment for a cancelled hangout.")
        }

        hangoutRepository.save(
            hangoutEntity.apply { payment.state = PaymentState.READY_FOR_PAYOUT }
        )

        val hostDisplayName =
            hangoutEntity.participants.first { it.hangoutUser.userId == hostId }.hangoutUser.displayName
        // Refresh the live lobby: the hangout is going ahead with whoever paid.
        applicationEventPublisher.publishEvent(
            HangoutUpdatedEvent(
                hangoutId = hangoutId,
                hostDisplayName = hostDisplayName
            )
        )
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    @Transactional
    fun resolvePaymentDeadlines() {
        val dueHangouts = hangoutRepository.findByPaymentStateAndPaymentDeadlineBeforeAndStatusNot(
            paymentState = PaymentState.COLLECTING,
            deadline = Instant.now(),
            excludedStatus = HangoutStatus.CANCELLED
        )

        dueHangouts.forEach { hangout ->
            val payment = hangout.payment ?: run {
                logger.error("Hangout {} came back from the deadline sweep with no payment", hangout.id)
                return@forEach
            }
            val attending = hangout.participants.filter { it.rsvpStatus == RsvpStatus.ATTENDING }
            val unpaidCount = attending.count { !it.hasPaid }
            val everyoneHasPaid = unpaidCount == 0

            hangoutRepository.save(
                hangout.apply {
                    payment.state =
                        if (everyoneHasPaid) PaymentState.READY_FOR_PAYOUT else PaymentState.AWAITING_HOST_DECISION
                }
            )

            eventPublisher.publish(
                HangoutEvent.PaymentDeadlineResolved(
                    hangoutId = hangout.id!!,
                    hangoutName = hangout.name,
                    hostId = hangout.hostId,
                    needsDecision = !everyoneHasPaid,
                    unpaidCount = unpaidCount
                )
            )
            applicationEventPublisher.publishEvent(
                HangoutPaymentDeadlineResolvedEvent(hangoutId = hangout.id!!)
            )
        }

        if (dueHangouts.isNotEmpty()) {
            logger.info("Resolved the payment deadline for {} hangouts", dueHangouts.size)
        }
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    @Transactional
    fun proceedWithHangoutsTheHostNeverDecided() {
        val undecidedHangouts = hangoutRepository.findByPaymentStateAndScheduledAtBeforeAndStatusNot(
            paymentState = PaymentState.AWAITING_HOST_DECISION,
            now = Instant.now(),
            excludedStatus = HangoutStatus.CANCELLED
        )

        undecidedHangouts.forEach { hangout ->
            val payment = hangout.payment ?: run {
                logger.error("Hangout {} came back from the undecided sweep with no payment", hangout.id)
                return@forEach
            }
            hangoutRepository.save(
                hangout.apply { payment.state = PaymentState.READY_FOR_PAYOUT }
            )

            val hostDisplayName = hangout.participants
                .first { it.hangoutUser.userId == hangout.hostId }.hangoutUser.displayName
            // Refresh the live lobby: the hangout is going ahead with whoever paid.
            applicationEventPublisher.publishEvent(
                HangoutUpdatedEvent(
                    hangoutId = hangout.id!!,
                    hostDisplayName = hostDisplayName
                )
            )
        }

        if (undecidedHangouts.isNotEmpty()) {
            logger.info(
                "Proceeded with {} hangouts whose host never answered the payment deadline",
                undecidedHangouts.size
            )
        }
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    @Transactional
    fun transitionDueHangoutsToOngoing() {
        val now = Instant.now()
        val dueHangouts = hangoutRepository.findByStatusInAndScheduledAtBefore(
            statuses = listOf(HangoutStatus.SCHEDULED),
            now = now
        )

        hangoutRepository.transitionDueHangoutsToOngoing(
            now = now,
            ongoingStatus = HangoutStatus.ONGOING,
            activeStatuses = listOf(HangoutStatus.SCHEDULED)
        )

        dueHangouts.forEach { hangout ->
            val recipientIds = hangout.participants
                .filter { it.rsvpStatus == RsvpStatus.ATTENDING }
                .map { it.hangoutUser.userId }
                .toSet()
            // Push: nobody is watching the clock, and this is the moment to head out.
            eventPublisher.publish(
                HangoutEvent.HangoutStarted(
                    hangoutId = hangout.id!!,
                    hangoutName = hangout.name,
                    recipientIds = recipientIds
                )
            )
        }

        if (dueHangouts.isNotEmpty()) {
            logger.info("Started {} hangouts whose time had come", dueHangouts.size)
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    fun cleanupSoloUnpaidHangouts() {
        val cutoff = Instant.now().minus(30, ChronoUnit.DAYS)
        hangoutRepository.deleteSoloUnpaidHangoutsScheduledBefore(cutoff)
    }

    private fun assertPaymentsCanBeEnabled(
        hangoutEntity: HangoutEntity,
        hostId: UserId,
        totalCostKobo: Long,
        paymentDeadline: Instant
    ) {
        if (hangoutEntity.hostId != hostId) {
            throw HangoutAccessDeniedException("Only the host can turn on payments.")
        }
        if (hangoutEntity.status != HangoutStatus.SCHEDULED) {
            throw HangoutIllegalStateException("Payments can only be turned on for a scheduled hangout.")
        }
        if (hangoutEntity.payment != null) {
            throw HangoutIllegalStateException("Payments are already turned on for this hangout.")
        }
        if (paymentDeadline.isAfter(hangoutEntity.scheduledAt)) {
            throw HangoutIllegalArgumentException("The payment deadline must be on or before the hangout date.")
        }

        val attendingCount = hangoutEntity.participants.count { it.rsvpStatus == RsvpStatus.ATTENDING }
        if (attendingCount < MIN_ATTENDEES_FOR_PAYMENTS) {
            throw HangoutIllegalStateException("Invite someone before you turn on payments.")
        }
        if (totalCostKobo / attendingCount < MIN_COST_PER_PERSON_KOBO) {
            throw HangoutIllegalArgumentException("Each person's share must come to at least 1 Naira.")
        }
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
