package com.eeseka.lynk.common.domain.events.hangout

/**
 * What actually changed about a hangout. [HangoutEvent.HangoutUpdated] carries a set of these so a
 * consumer can tell the publication sites apart.
 */
enum class HangoutChangeKind {
    DETAILS_EDITED,
    SCHEDULE_CHANGED,
    SPOT_CHOSEN,
    VOTING_REOPENED,
    PAYMENTS_ENABLED
}