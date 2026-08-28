package com.eeseka.lynk.payment.infra.messaging

import com.eeseka.lynk.common.domain.events.hangout.HangoutEvent
import com.eeseka.lynk.common.infra.message_queue.MessageQueues
import com.eeseka.lynk.payment.service.RefundService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class HangoutRefundListener(
    private val refundService: RefundService
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
        logger.info("Hangout {} was cancelled, refunding everyone who paid", event.hangoutId)
        refundService.refundSettledPaymentsForHangout(event.hangoutId)
    }

    private fun onParticipantLeft(event: HangoutEvent.ParticipantLeft) {
        logger.info("Participant {} left hangout {}, refunding their money", event.leaverId, event.hangoutId)
        refundService.refundSettledPaymentForParticipant(
            hangoutId = event.hangoutId,
            userId = event.leaverId
        )
    }
}
