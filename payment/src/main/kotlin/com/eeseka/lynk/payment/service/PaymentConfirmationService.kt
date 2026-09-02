package com.eeseka.lynk.payment.service

import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.service.HangoutParticipantService
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.payment.domain.events.RefundRequiredEvent
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.domain.model.RefundStatus
import com.eeseka.lynk.payment.infra.database.entities.PaymentEntity
import com.eeseka.lynk.payment.infra.database.repositories.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PaymentConfirmationService(
    private val paymentRepository: PaymentRepository,
    private val hangoutParticipantService: HangoutParticipantService,
    private val hangoutService: HangoutService,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun confirm(reference: String, paidAmountKobo: Long?) {
        val reported = paymentRepository.findByReference(reference)
        if (reported == null) {
            logger.warn("A payment was reported for a reference we do not know: {}", reference)
            return
        }

        // Everything this person has going on this hangout, held for the rest of the transaction, so two
        // payments reported at the same moment are dealt with one after the other rather than together.
        val locked = paymentRepository.lockAllByHangoutIdAndUserId(
            hangoutId = reported.hangoutId,
            userId = reported.userId
        )

        val payment = locked.firstOrNull { it.reference == reference } ?: reported

        if (payment.status == PaymentStatus.SUCCESS) {
            logger.info("Payment {} is already successful, nothing to do", payment.reference)
            return
        }

        if (paidAmountKobo == null || paidAmountKobo < payment.amountKobo) {
            failUnderpayment(payment = payment, paidAmountKobo = paidAmountKobo)
            return
        }

        val hasPaidAlready = locked.any {
            it.reference != reference && it.status == PaymentStatus.SUCCESS && it.refundStatus == RefundStatus.NONE
        }

        if (hasPaidAlready) {
            logger.warn(
                "Payment {} is a second payment by {} for hangout {}, sending it back",
                payment.reference, payment.userId, payment.hangoutId
            )

            paymentRepository.save(
                payment.apply {
                    status = PaymentStatus.SUCCESS
                    paidAt = Instant.now()
                    refundStatus = RefundStatus.REQUESTED
                    refundedAmountKobo = netAmountKobo
                }
            )

            requestRefund(
                paymentReference = payment.reference,
                reason = "a second payment for a hangout they had already paid for",
                alreadyClaimed = true
            )
            return
        }

        paymentRepository.save(
            payment.apply {
                status = PaymentStatus.SUCCESS
                paidAt = Instant.now()
            }
        )

        if (hangoutService.findHangoutStatus(payment.hangoutId) == HangoutStatus.CANCELLED) {
            logger.warn(
                "Payment {} landed after hangout {} was cancelled, sending it back",
                payment.reference, payment.hangoutId
            )
            requestRefund(payment.reference, "the hangout was cancelled before it arrived")
            return
        }

        val isStillAttending = hangoutParticipantService.findRsvpStatus(
            hangoutId = payment.hangoutId,
            userId = payment.userId
        ) == RsvpStatus.ATTENDING
        if (!isStillAttending) {
            logger.warn(
                "Payment {} landed after {} stopped attending hangout {}, sending it back",
                payment.reference, payment.userId, payment.hangoutId
            )
            requestRefund(payment.reference, "it arrived after they stopped attending")
            return
        }

        hangoutParticipantService.markParticipantAsPaid(
            hangoutId = payment.hangoutId,
            userId = payment.userId
        )

        logger.info("Payment {} confirmed for hangout {}", payment.reference, payment.hangoutId)
    }

    /**
     * The provider says the charge went through, so the money is real, but it is not the amount we asked
     * for. It does not buy a seat, and we are not keeping it either.
     */
    private fun failUnderpayment(payment: PaymentEntity, paidAmountKobo: Long?) {
        logger.error(
            "The provider reported {} kobo for {} but we asked for {} - not marking as paid",
            paidAmountKobo, payment.reference, payment.amountKobo
        )

        paymentRepository.save(
            payment.apply {
                status = PaymentStatus.FAILED
                failureReason = "Owed $amountKobo kobo, provider reported ${paidAmountKobo ?: "none"}"
            }
        )

        // Send back only what Paystack says it took. A null or zero amount means it never reported
        // taking anything, and refunding on that could return money that was never collected.
        if (paidAmountKobo != null && paidAmountKobo > 0) {
            requestRefund(payment.reference, "the amount paid was short of the amount owed")
        }
    }

    private fun requestRefund(paymentReference: String, reason: String, alreadyClaimed: Boolean = false) {
        applicationEventPublisher.publishEvent(
            RefundRequiredEvent(
                reference = paymentReference,
                reason = reason,
                alreadyClaimed = alreadyClaimed
            )
        )
    }
}