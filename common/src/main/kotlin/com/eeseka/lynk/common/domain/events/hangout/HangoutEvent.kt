package com.eeseka.lynk.common.domain.events.hangout

import com.eeseka.lynk.common.domain.events.LynkEvent
import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import java.time.Instant
import java.util.*

sealed class HangoutEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val exchange: String = HangoutEventConstants.HANGOUT_EXCHANGE,
    override val occurredAt: Instant = Instant.now(),
) : LynkEvent {

    data class ParticipantInvited(
        val hangoutId: HangoutId,
        val hangoutName: String,
        val inviteeId: UserId,
        val hostDisplayName: String,
        override val eventKey: String = HangoutEventConstants.HANGOUT_PARTICIPANT_INVITED_KEY
    ) : HangoutEvent(), LynkEvent

    data class InviteCancelled(
        val hangoutId: HangoutId,
        val hangoutName: String,
        val inviteeId: UserId,
        val hostDisplayName: String,
        override val eventKey: String = HangoutEventConstants.HANGOUT_INVITE_CANCELLED_KEY
    ) : HangoutEvent(), LynkEvent

    data class RemovedForNonPayment(
        val hangoutId: HangoutId,
        val hangoutName: String,
        val participantId: UserId,
        val hostDisplayName: String,
        override val eventKey: String = HangoutEventConstants.HANGOUT_REMOVED_FOR_NON_PAYMENT_KEY
    ) : HangoutEvent(), LynkEvent

    data class ParticipantLeft(
        val hangoutId: HangoutId,
        val hangoutName: String,
        val hostId: UserId,
        val leaverId: UserId,
        val leaverDisplayName: String,
        override val eventKey: String = HangoutEventConstants.HANGOUT_PARTICIPANT_LEFT_KEY
    ) : HangoutEvent(), LynkEvent

    data class HangoutUpdated(
        val hangoutId: HangoutId,
        val hangoutName: String,
        val recipientIds: Set<UserId>,
        val hostDisplayName: String,
        val changes: Set<HangoutChangeKind>,
        override val eventKey: String = HangoutEventConstants.HANGOUT_UPDATED_KEY
    ) : HangoutEvent(), LynkEvent

    data class HangoutCompleted(
        val hangoutId: HangoutId,
        val hangoutName: String,
        val recipientIds: Set<UserId>,
        val hostDisplayName: String,
        override val eventKey: String = HangoutEventConstants.HANGOUT_COMPLETED_KEY
    ) : HangoutEvent(), LynkEvent

    data class HangoutCancelled(
        val hangoutId: HangoutId,
        val hangoutName: String,
        val recipientIds: Set<UserId>,
        val hostDisplayName: String,
        override val eventKey: String = HangoutEventConstants.HANGOUT_CANCELLED_KEY
    ) : HangoutEvent(), LynkEvent

    data class PaymentDeadlineResolved(
        val hangoutId: HangoutId,
        val hangoutName: String,
        val hostId: UserId,
        val needsDecision: Boolean,
        val unpaidCount: Int,
        override val eventKey: String = HangoutEventConstants.HANGOUT_PAYMENT_DEADLINE_RESOLVED_KEY
    ) : HangoutEvent(), LynkEvent

    data class PayoutOutcome(
        val hangoutId: HangoutId,
        val hangoutName: String,
        val hostId: UserId,
        val succeeded: Boolean,
        val reference: String?,
        val amountKobo: Long,
        override val eventKey: String = HangoutEventConstants.HANGOUT_PAYOUT_OUTCOME_KEY
    ) : HangoutEvent(), LynkEvent

    data class PaymentDeadlineChanged(
        val hangoutId: HangoutId,
        val hangoutName: String,
        val recipientIds: Set<UserId>,
        val hostDisplayName: String,
        val newDeadline: Instant,
        override val eventKey: String = HangoutEventConstants.HANGOUT_PAYMENT_DEADLINE_CHANGED_KEY
    ) : HangoutEvent(), LynkEvent

    data class HangoutStarted(
        val hangoutId: HangoutId,
        val hangoutName: String,
        val recipientIds: Set<UserId>,
        override val eventKey: String = HangoutEventConstants.HANGOUT_STARTED_KEY
    ) : HangoutEvent(), LynkEvent

    data class PaymentReceived(
        val hangoutId: HangoutId,
        val hangoutName: String,
        val hostId: UserId,
        val payerId: UserId,
        val payerDisplayName: String,
        val amountKobo: Long?,
        override val eventKey: String = HangoutEventConstants.HANGOUT_PAYMENT_RECEIVED_KEY
    ) : HangoutEvent(), LynkEvent

    data class RefundIssued(
        val hangoutId: HangoutId,
        val hangoutName: String,
        val participantId: UserId,
        val amountKobo: Long,
        val reference: String,
        override val eventKey: String = HangoutEventConstants.HANGOUT_REFUND_ISSUED_KEY
    ) : HangoutEvent(), LynkEvent
}