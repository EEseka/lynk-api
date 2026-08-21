package com.eeseka.lynk.hangout.service

import com.eeseka.lynk.common.domain.AccountDeletionGuard
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.exception.HangoutIllegalStateException
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.PaymentState
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutParticipantRepository
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutRepository
import org.springframework.stereotype.Component

@Component
class HangoutAccountDeletionGuard(
    private val hangoutRepository: HangoutRepository,
    private val hangoutParticipantRepository: HangoutParticipantRepository
) : AccountDeletionGuard {
    companion object {
        private val UNFINISHED_STATUSES = listOf(
            HangoutStatus.VOTING,
            HangoutStatus.SCHEDULED,
            HangoutStatus.ONGOING
        )
        private val UNSETTLED_PAYMENT_STATES = listOf(
            PaymentState.COLLECTING,
            PaymentState.AWAITING_HOST_DECISION,
            PaymentState.READY_FOR_PAYOUT,
            PaymentState.PAYING_OUT,
            PaymentState.PAYOUT_FAILED
        )
    }

    override fun assertAccountCanBeDeleted(userId: UserId) {
        if (hangoutRepository.existsByHostIdAndPaymentStateIn(userId, UNSETTLED_PAYMENT_STATES)) {
            throw HangoutIllegalStateException(
                "You are hosting a hangout with money still to settle. Wait for the payout to finish, or cancel the hangout so everyone is refunded, then delete your account."
            )
        }

        if (hangoutRepository.existsByHostIdAndStatusIn(userId, UNFINISHED_STATUSES)) {
            throw HangoutIllegalStateException(
                "You are hosting a hangout that has not finished. Cancel it first, then delete your account."
            )
        }

        val isAttendingUnfinishedHangout = hangoutParticipantRepository
            .existsByUserIdAndRsvpStatusAndHangoutStatusIn(
                userId = userId,
                attendingStatus = RsvpStatus.ATTENDING,
                hangoutStatuses = UNFINISHED_STATUSES
            )

        if (isAttendingUnfinishedHangout) {
            throw HangoutIllegalStateException(
                "You are attending a hangout that has not finished. Leave it first, then delete your account."
            )
        }
    }
}
