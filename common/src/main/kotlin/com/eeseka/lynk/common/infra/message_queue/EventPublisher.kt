package com.eeseka.lynk.common.infra.message_queue

import com.eeseka.lynk.common.domain.events.LynkEvent
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Component
class EventPublisher(
    private val rabbitTemplate: RabbitTemplate
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * The in-process events get AFTER_COMMIT for free from @TransactionalEventListener. AMQP has no
     * such annotation, so we register the same synchronization by hand and hold the message until the
     * transaction that produced it has actually committed.
     *
     * Without this, a transaction that rolls back has already sent its message. Consumers that only
     * push a notification would show something that never happened; the ones that delete saved spots,
     * anonymize a user, or refund a hangout would do it for real.
     */
    fun <T : LynkEvent> publish(event: T) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() = send(event)
                }
            )
        } else {
            send(event)
        }
    }

    private fun <T : LynkEvent> send(event: T) {
        try {
            rabbitTemplate.convertAndSend(
                event.exchange,
                event.eventKey,
                event
            )
            logger.info("Successfully published event: ${event.eventKey}")
        } catch (e: Exception) {
            logger.error("Failed to publish ${event.eventKey} event", e)
        }
    }
}