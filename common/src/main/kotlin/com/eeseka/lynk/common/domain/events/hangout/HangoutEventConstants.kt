package com.eeseka.lynk.common.domain.events.hangout

object HangoutEventConstants {
    const val HANGOUT_EXCHANGE = "hangout.events"
    const val HANGOUT_PARTICIPANT_INVITED_KEY = "hangout.participant_invited"
    const val HANGOUT_INVITE_CANCELLED_KEY = "hangout.invite_cancelled"
    const val HANGOUT_REMOVED_FOR_NON_PAYMENT_KEY = "hangout.removed_for_non_payment"
    const val HANGOUT_PARTICIPANT_LEFT_KEY = "hangout.participant_left"
    const val HANGOUT_UPDATED_KEY = "hangout.updated"
    const val HANGOUT_COMPLETED_KEY = "hangout.completed"
    const val HANGOUT_CANCELLED_KEY = "hangout.cancelled"
    const val HANGOUT_PAYOUT_OUTCOME_KEY = "hangout.payout_outcome"
    const val HANGOUT_PAYMENT_DEADLINE_RESOLVED_KEY = "hangout.payment_deadline_resolved"
}