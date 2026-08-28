package com.eeseka.lynk.notification.infra.messaging

import com.eeseka.lynk.common.domain.events.hangout.HangoutChangeKind
import com.eeseka.lynk.common.domain.events.hangout.HangoutEvent
import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.common.infra.message_queue.MessageQueues
import com.eeseka.lynk.notification.domain.model.NotificationType
import com.eeseka.lynk.notification.service.util.toNairaString
import com.eeseka.lynk.notification.service.EmailService
import com.eeseka.lynk.notification.service.NotificationService
import com.eeseka.lynk.notification.service.NotificationUserService
import com.eeseka.lynk.notification.service.PushNotificationService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class NotificationHangoutEventListener(
    private val notificationService: NotificationService,
    private val pushNotificationService: PushNotificationService,
    private val notificationUserService: NotificationUserService,
    private val emailService: EmailService
) {
    companion object {
        private val CHANGE_PRIORITY = listOf(
            HangoutChangeKind.SCHEDULE_CHANGED,
            HangoutChangeKind.PAYMENTS_ENABLED,
            HangoutChangeKind.SPOT_CHOSEN,
            HangoutChangeKind.VOTING_REOPENED,
            HangoutChangeKind.DETAILS_EDITED
        )
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = [MessageQueues.NOTIFICATION_HANGOUT_EVENTS])
    fun handleHangoutEvent(event: HangoutEvent) {
        try {
            when (event) {
                is HangoutEvent.ParticipantInvited -> onParticipantInvited(event)
                is HangoutEvent.InviteCancelled -> onInviteCancelled(event)
                is HangoutEvent.RemovedForNonPayment -> onRemovedForNonPayment(event)
                is HangoutEvent.ParticipantLeft -> onParticipantLeft(event)
                is HangoutEvent.HangoutUpdated -> onHangoutUpdated(event)
                is HangoutEvent.HangoutCompleted -> onHangoutCompleted(event)
                is HangoutEvent.HangoutCancelled -> onHangoutCancelled(event)
                is HangoutEvent.PaymentDeadlineResolved -> onPaymentDeadlineResolved(event)
                is HangoutEvent.PayoutOutcome -> onPayoutOutcome(event)
                is HangoutEvent.PaymentDeadlineChanged -> onPaymentDeadlineChanged(event)
                is HangoutEvent.HangoutStarted -> onHangoutStarted(event)
                is HangoutEvent.PaymentReceived -> onPaymentReceived(event)
                is HangoutEvent.RefundIssued -> onRefundIssued(event)
            }
        } catch (e: Exception) {
            logger.error("Could not handle ${event.eventKey} for hangout event ${event.eventId}", e)
        }
    }

    private fun onParticipantInvited(event: HangoutEvent.ParticipantInvited) {
        notify(
            recipientIds = setOf(event.inviteeId),
            type = NotificationType.PARTICIPANT_INVITED,
            hangoutId = event.hangoutId,
            hangoutName = event.hangoutName,
            actorDisplayName = event.hostDisplayName,
            message = "${event.hostDisplayName} invited you to this hangout"
        )
    }

    private fun onInviteCancelled(event: HangoutEvent.InviteCancelled) {
        notify(
            recipientIds = setOf(event.inviteeId),
            type = NotificationType.INVITE_CANCELLED,
            hangoutId = event.hangoutId,
            hangoutName = event.hangoutName,
            actorDisplayName = event.hostDisplayName,
            message = "${event.hostDisplayName} withdrew your invite"
        )
    }

    private fun onRemovedForNonPayment(event: HangoutEvent.RemovedForNonPayment) {
        notify(
            recipientIds = setOf(event.participantId),
            type = NotificationType.REMOVED_FOR_NON_PAYMENT,
            hangoutId = event.hangoutId,
            hangoutName = event.hangoutName,
            actorDisplayName = event.hostDisplayName,
            message = "You were removed because your share went unpaid"
        )

        emailEach(setOf(event.participantId)) { email ->
            emailService.sendRemovedForNonPaymentEmail(
                email = email,
                hangoutName = event.hangoutName,
                hostDisplayName = event.hostDisplayName
            )
        }
    }

    private fun onParticipantLeft(event: HangoutEvent.ParticipantLeft) {
        notify(
            recipientIds = setOf(event.hostId),
            type = NotificationType.PARTICIPANT_LEFT,
            hangoutId = event.hangoutId,
            hangoutName = event.hangoutName,
            actorDisplayName = event.leaverDisplayName,
            message = "${event.leaverDisplayName} left this hangout"
        )
    }

    private fun onHangoutUpdated(event: HangoutEvent.HangoutUpdated) {
        val change = CHANGE_PRIORITY.firstOrNull { it in event.changes } ?: return

        val headline = when (change) {
            HangoutChangeKind.PAYMENTS_ENABLED -> "${event.hostDisplayName} split the bill - your share is ready to pay"
            HangoutChangeKind.SPOT_CHOSEN -> "${event.hostDisplayName} locked in where you are going"
            HangoutChangeKind.VOTING_REOPENED -> "${event.hostDisplayName} reopened voting - pick where you are going"
            HangoutChangeKind.SCHEDULE_CHANGED -> "${event.hostDisplayName} moved when this is happening"
            HangoutChangeKind.DETAILS_EDITED -> "${event.hostDisplayName} updated the details"
        }

        val otherChanges = event.changes.size - 1

        notify(
            recipientIds = event.recipientIds,
            type = change.toNotificationType(),
            hangoutId = event.hangoutId,
            hangoutName = event.hangoutName,
            actorDisplayName = event.hostDisplayName,
            message = headline + when (otherChanges) {
                0 -> ""
                1 -> ", and one other change"
                else -> ", and $otherChanges other changes"
            }
        )
    }

    private fun onHangoutCompleted(event: HangoutEvent.HangoutCompleted) {
        notify(
            recipientIds = event.recipientIds,
            type = NotificationType.HANGOUT_COMPLETED,
            hangoutId = event.hangoutId,
            hangoutName = event.hangoutName,
            actorDisplayName = event.hostDisplayName,
            message = "That's a wrap - this hangout is done"
        )
    }

    private fun onHangoutCancelled(event: HangoutEvent.HangoutCancelled) {
        notify(
            recipientIds = event.recipientIds,
            type = NotificationType.HANGOUT_CANCELLED,
            hangoutId = event.hangoutId,
            hangoutName = event.hangoutName,
            actorDisplayName = event.hostDisplayName,
            message = "${event.hostDisplayName} called this hangout off"
        )

        emailEach(event.recipientIds) { email ->
            emailService.sendHangoutCancelledEmail(
                email = email,
                hangoutName = event.hangoutName,
                hostDisplayName = event.hostDisplayName
            )
        }
    }

    private fun onPaymentDeadlineResolved(event: HangoutEvent.PaymentDeadlineResolved) {
        val type = if (event.needsDecision) {
            NotificationType.PAYMENT_DEADLINE_NEEDS_DECISION
        } else {
            NotificationType.PAYMENT_DEADLINE_RESOLVED
        }
        val message = if (event.needsDecision) {
            "The deadline passed with ${event.unpaidCount} unpaid - it is your call now"
        } else {
            "Everyone has paid"
        }

        notify(
            recipientIds = setOf(event.hostId),
            type = type,
            hangoutId = event.hangoutId,
            hangoutName = event.hangoutName,
            message = message
        )

        emailEach(setOf(event.hostId)) { email ->
            emailService.sendPaymentDeadlineResolvedEmail(
                email = email,
                hangoutId = event.hangoutId,
                hangoutName = event.hangoutName,
                needsDecision = event.needsDecision,
                unpaidCount = event.unpaidCount
            )
        }
    }

    private fun onPayoutOutcome(event: HangoutEvent.PayoutOutcome) {
        val nothingToSend = event.succeeded && event.amountKobo <= 0

        val type = when {
            nothingToSend -> NotificationType.PAYOUT_NOTHING_TO_SEND
            event.succeeded -> NotificationType.PAYOUT_SUCCEEDED
            else -> NotificationType.PAYOUT_FAILED
        }
        val message = when {
            nothingToSend -> "Nothing was left to pay out for this one"
            event.succeeded -> "${event.amountKobo.toNairaString()} is on its way to your bank account"
            else -> "The transfer failed - your money is safe, tap to retry"
        }

        notify(
            recipientIds = setOf(event.hostId),
            type = type,
            hangoutId = event.hangoutId,
            hangoutName = event.hangoutName,
            amountKobo = event.amountKobo,
            message = message
        )

        emailEach(setOf(event.hostId)) { email ->
            emailService.sendPayoutOutcomeEmail(
                email = email,
                hangoutId = event.hangoutId,
                hangoutName = event.hangoutName,
                succeeded = event.succeeded,
                reference = event.reference,
                amountKobo = event.amountKobo
            )
        }
    }

    private fun onPaymentDeadlineChanged(event: HangoutEvent.PaymentDeadlineChanged) {
        notify(
            recipientIds = event.recipientIds,
            type = NotificationType.PAYMENT_DEADLINE_CHANGED,
            hangoutId = event.hangoutId,
            hangoutName = event.hangoutName,
            actorDisplayName = event.hostDisplayName,
            message = "${event.hostDisplayName} moved the deadline to pay"
        )
    }

    private fun onHangoutStarted(event: HangoutEvent.HangoutStarted) {
        notify(
            recipientIds = event.recipientIds,
            type = NotificationType.HANGOUT_STARTED,
            hangoutId = event.hangoutId,
            hangoutName = event.hangoutName,
            message = "Kicking off now - see who's there"
        )
    }

    private fun onPaymentReceived(event: HangoutEvent.PaymentReceived) {
        val amount = event.amountKobo?.toNairaString()
        val message = if (amount != null) {
            "${event.payerDisplayName} paid their $amount share"
        } else {
            "${event.payerDisplayName} paid their share"
        }

        notify(
            recipientIds = setOf(event.hostId),
            type = NotificationType.PAYMENT_RECEIVED,
            hangoutId = event.hangoutId,
            hangoutName = event.hangoutName,
            actorDisplayName = event.payerDisplayName,
            amountKobo = event.amountKobo,
            message = message
        )
    }

    private fun onRefundIssued(event: HangoutEvent.RefundIssued) {
        notify(
            recipientIds = setOf(event.participantId),
            type = NotificationType.REFUND_ISSUED,
            hangoutId = event.hangoutId,
            hangoutName = event.hangoutName,
            amountKobo = event.amountKobo,
            message = "Your ${event.amountKobo.toNairaString()} refund is on its way"
        )

        emailEach(setOf(event.participantId)) { email ->
            emailService.sendRefundIssuedEmail(
                email = email,
                hangoutName = event.hangoutName,
                amountKobo = event.amountKobo,
                reference = event.reference
            )
        }
    }

    private fun notify(
        recipientIds: Set<UserId>,
        type: NotificationType,
        hangoutId: HangoutId,
        hangoutName: String,
        message: String,
        actorDisplayName: String? = null,
        amountKobo: Long? = null
    ) {
        notificationService.createNotifications(
            userIds = recipientIds,
            type = type,
            hangoutId = hangoutId,
            hangoutName = hangoutName,
            actorDisplayName = actorDisplayName,
            amountKobo = amountKobo
        )

        pushNotificationService.sendToUsers(
            recipientIds = recipientIds,
            title = hangoutName,
            message = message,
            hangoutId = hangoutId,
            type = type
        )
    }

    private fun emailEach(recipientIds: Set<UserId>, send: (email: String) -> Unit) {
        notificationUserService.findByUserIds(recipientIds).forEach { user ->
            send(user.email)
        }
    }

    private fun HangoutChangeKind.toNotificationType(): NotificationType = when (this) {
        HangoutChangeKind.DETAILS_EDITED -> NotificationType.DETAILS_EDITED
        HangoutChangeKind.SCHEDULE_CHANGED -> NotificationType.SCHEDULE_CHANGED
        HangoutChangeKind.SPOT_CHOSEN -> NotificationType.SPOT_CHOSEN
        HangoutChangeKind.VOTING_REOPENED -> NotificationType.VOTING_REOPENED
        HangoutChangeKind.PAYMENTS_ENABLED -> NotificationType.PAYMENTS_ENABLED
    }
}