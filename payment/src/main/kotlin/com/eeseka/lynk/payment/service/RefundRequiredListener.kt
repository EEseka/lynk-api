package com.eeseka.lynk.payment.service

import com.eeseka.lynk.payment.domain.events.RefundRequiredEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Not in infra/messaging: nothing here touches a broker. This is a plain in-process Spring event, and
 * the only thing this class knows that [RefundService] does not is when it is safe to act.
 */
@Component
class RefundRequiredListener(
    private val refundService: RefundService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRefundRequired(event: RefundRequiredEvent) {
        logger.info("Sending payment {} back: {}", event.reference, event.reason)

        refundService.refundPaymentByReference(event.reference)
    }
}