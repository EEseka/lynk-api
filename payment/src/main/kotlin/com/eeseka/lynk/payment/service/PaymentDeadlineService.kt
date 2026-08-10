package com.eeseka.lynk.payment.service

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.service.HangoutParticipantService
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.payment.domain.exception.PaymentIllegalArgumentException
import com.eeseka.lynk.payment.domain.model.DeadlineDecision
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PaymentDeadlineService(
    private val hangoutService: HangoutService,
    private val hangoutParticipantService: HangoutParticipantService
) {
    fun changeDeadline(hostId: UserId, hangoutId: HangoutId, newDeadline: Instant) {
        hangoutService.changePaymentDeadline(
            hostId = hostId,
            hangoutId = hangoutId,
            newDeadline = newDeadline
        )
    }

    @Transactional
    fun applyDeadlineDecision(
        hostId: UserId,
        hangoutId: HangoutId,
        decision: DeadlineDecision,
        newDeadline: Instant?
    ) {
        when (decision) {
            DeadlineDecision.EXTEND -> {
                val deadline = newDeadline
                    ?: throw PaymentIllegalArgumentException("Choosing to extend needs a new deadline.")

                hangoutService.changePaymentDeadline(
                    hostId = hostId,
                    hangoutId = hangoutId,
                    newDeadline = deadline
                )
            }

            DeadlineDecision.REMOVE_NON_PAYERS -> {
                hangoutParticipantService.removeNonPayers(
                    hostId = hostId,
                    hangoutId = hangoutId
                )
                hangoutService.proceedWithoutFullPayment(hostId = hostId, hangoutId = hangoutId)
            }

            DeadlineDecision.PROCEED_ANYWAY -> {
                hangoutService.proceedWithoutFullPayment(hostId = hostId, hangoutId = hangoutId)
            }

            DeadlineDecision.CANCEL -> {
                hangoutService.cancelHangout(hostId = hostId, hangoutId = hangoutId)
            }
        }
    }
}