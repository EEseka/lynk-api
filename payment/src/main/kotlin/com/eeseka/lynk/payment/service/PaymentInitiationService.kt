package com.eeseka.lynk.payment.service

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.payment.domain.PaystackFees
import com.eeseka.lynk.payment.domain.exception.PaymentAccessDeniedException
import com.eeseka.lynk.payment.domain.exception.PaymentIllegalStateException
import com.eeseka.lynk.payment.domain.model.PaymentInitialization
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.infra.database.entities.PaymentEntity
import com.eeseka.lynk.payment.infra.database.repositories.PaymentRepository
import com.eeseka.lynk.payment.infra.paystack.PaystackClient
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class PaymentInitiationService(
    private val paystackClient: PaystackClient,
    private val paymentRepository: PaymentRepository,
    private val hangoutService: HangoutService
) {
    /**
     * Deliberately not transactional. The row has to be committed before Paystack is asked to start the
     * charge: if the two shared a transaction, and it rolled back after the call, Paystack would hold a
     * live charge against a reference this database has never heard of, and the guest's money would
     * arrive with nothing here to attach it to.
     */
    fun initializePayment(userId: UserId, hangoutId: HangoutId): PaymentInitialization {
        val hangout = hangoutService.getHangout(userId = userId, hangoutId = hangoutId)

        val payment = hangout.payment
            ?: throw PaymentIllegalStateException("The host has not turned on payments for this hangout.")
        val costPerPersonKobo = payment.costPerPersonKobo
        val paymentDeadline = payment.deadline

        if (hangout.status != HangoutStatus.SCHEDULED) {
            throw PaymentIllegalStateException("This hangout is not open for payment.")
        }
        if (paymentDeadline.isBefore(Instant.now())) {
            throw PaymentIllegalStateException("The deadline to pay for this hangout has passed.")
        }
        // The host is fronting the bill, not paying into it.
        if (hangout.hostId == userId) {
            throw PaymentAccessDeniedException("The host does not pay their own hangout.")
        }

        val participant = hangout.participants.firstOrNull { it.user.userId == userId }
            ?: throw PaymentAccessDeniedException("You are not part of this hangout.")

        if (participant.rsvpStatus != RsvpStatus.ATTENDING) {
            throw PaymentAccessDeniedException("Only people who are going can pay.")
        }
        if (participant.hasPaid) {
            throw PaymentIllegalStateException("You have already paid for this hangout.")
        }
        // hasPaid is set a moment after the money lands. Without this, someone could pay twice within that gap.
        val alreadySucceeded = paymentRepository.existsByHangoutIdAndUserIdAndStatus(
            hangoutId = hangoutId,
            userId = userId,
            status = PaymentStatus.SUCCESS
        )
        if (alreadySucceeded) {
            throw PaymentIllegalStateException("You have already paid for this hangout.")
        }

        val email = participant.user.email
        val amountKobo = PaystackFees.grossUpKobo(costPerPersonKobo)
        val reference = newReference()

        paymentRepository.save(
            PaymentEntity(
                hangoutId = hangoutId,
                userId = userId,
                reference = reference,
                amountKobo = amountKobo,
                netAmountKobo = costPerPersonKobo,
                status = PaymentStatus.PENDING
            )
        )

        val authorizationUrl = paystackClient.initializeCharge(
            email = email,
            amountKobo = amountKobo,
            reference = reference,
            hangoutId = hangoutId,
            userId = userId
        )

        return PaymentInitialization(
            authorizationUrl = authorizationUrl,
            reference = reference,
            amountKobo = amountKobo,
            netAmountKobo = costPerPersonKobo
        )
    }

    // Ours, not Paystack's, so the row can exist before the charge does.
    private fun newReference(): String = "lynk_${UUID.randomUUID()}"
}