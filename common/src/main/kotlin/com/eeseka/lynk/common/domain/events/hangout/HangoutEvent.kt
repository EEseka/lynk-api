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

    data class ParticipantLeft(
        val hangoutId: HangoutId,
        val hangoutName: String,
        val hostId: UserId,
        val leaverDisplayName: String,
        override val eventKey: String = HangoutEventConstants.HANGOUT_PARTICIPANT_LEFT_KEY
    ) : HangoutEvent(), LynkEvent

    data class HangoutUpdated(
        val hangoutId: HangoutId,
        val hangoutName: String,
        val recipientIds: Set<UserId>,
        val hostDisplayName: String,
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
}