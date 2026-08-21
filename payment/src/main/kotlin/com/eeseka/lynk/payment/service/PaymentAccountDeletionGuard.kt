package com.eeseka.lynk.payment.service

import com.eeseka.lynk.common.domain.AccountDeletionGuard
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.payment.domain.exception.PaymentIllegalStateException
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.domain.model.RefundStatus
import com.eeseka.lynk.payment.infra.database.repositories.PaymentRepository
import org.springframework.stereotype.Component

@Component
class PaymentAccountDeletionGuard(
    private val paymentRepository: PaymentRepository
) : AccountDeletionGuard {
    companion object {
        private val UNSETTLED_REFUND_STATUSES = listOf(
            RefundStatus.REQUESTED,
            RefundStatus.FAILED
        )
    }

    override fun assertAccountCanBeDeleted(userId: UserId) {
        if (paymentRepository.existsByUserIdAndStatus(userId, PaymentStatus.PENDING)) {
            throw PaymentIllegalStateException(
                "You have a payment we are still confirming. Give it a moment, then delete your account."
            )
        }

        if (paymentRepository.existsByUserIdAndRefundStatusIn(userId, UNSETTLED_REFUND_STATUSES)) {
            throw PaymentIllegalStateException(
                "You are owed a refund that has not reached you yet. Once it lands you can delete your account."
            )
        }
    }
}
