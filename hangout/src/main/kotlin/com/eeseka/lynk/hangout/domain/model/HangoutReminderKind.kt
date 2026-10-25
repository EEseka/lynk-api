package com.eeseka.lynk.hangout.domain.model

enum class HangoutReminderKind {
    SPOT_BEFORE_START,  // Still voting with less than a day to go.
    SPOT_AFTER_START,   // Still voting after the start passed.
    COMPLETION_DAY_1,   // Still going a day after the start.
    COMPLETION_DAY_3,   // Still going three days after the start.
    COMPLETION_DAY_7,   // Still going a week after the start. The last nudge.
    SOLO_BEFORE_START   // Nobody but the host going, with less than a day to go.
}
