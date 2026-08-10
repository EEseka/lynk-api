package com.eeseka.lynk.payment.infra.messaging

import com.eeseka.lynk.common.domain.events.hangout.HangoutEvent
import com.eeseka.lynk.common.infra.message_queue.MessageQueues
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.service.HangoutParticipantService
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.payment.service.RefundService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class HangoutRefundListener(
    private val refundService: RefundService,
    private val hangoutService: HangoutService,
    private val hangoutParticipantService: HangoutParticipantService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = [MessageQueues.PAYMENT_HANGOUT_EVENTS])
    fun handleHangoutEvent(event: HangoutEvent) {
        when (event) {
            is HangoutEvent.HangoutCancelled -> onHangoutCancelled(event)
            is HangoutEvent.ParticipantLeft -> onParticipantLeft(event)
            else -> Unit
        }
    }

    private fun onHangoutCancelled(event: HangoutEvent.HangoutCancelled) {
        // Nothing else in this codebase re-reads before acting on an event, so here is why this one
        // does. Events are published inside the transaction that caused them, before it has committed.
        // If that transaction then rolls back, the message has already gone out, and it says a hangout
        // was canceled when it was not. Everywhere else that costs a wrong push notification. Here it
        // would refund everybody for a hangout that is still happening, and we cannot take that back.
        // So we ask the database what is true now instead of trusting what the message says.
        val status = hangoutService.findHangoutStatus(event.hangoutId)
        if (status != HangoutStatus.CANCELLED) {
            logger.warn(
                "Ignoring a cancellation for hangout {}: it is {}, so that message outlived its transaction",
                event.hangoutId, status
            )
            return
        }

        logger.info("Hangout {} was cancelled, refunding everyone who paid", event.hangoutId)
        refundService.refundSettledPaymentsForHangout(event.hangoutId)
    }

    private fun onParticipantLeft(event: HangoutEvent.ParticipantLeft) {
        // Same re-read as above, same reason: only send money back to someone the roster agrees has actually gone.
        val rsvpStatus = hangoutParticipantService.findRsvpStatus(
            hangoutId = event.hangoutId,
            userId = event.leaverId
        )
        if (rsvpStatus != RsvpStatus.DECLINED) {
            logger.warn(
                "Ignoring a departure by {} from hangout {}: they are {}, so that message outlived its transaction",
                event.leaverId, event.hangoutId, rsvpStatus
            )
            return
        }

        refundService.refundSettledPaymentForParticipant(
            hangoutId = event.hangoutId,
            userId = event.leaverId
        )
    }
}
