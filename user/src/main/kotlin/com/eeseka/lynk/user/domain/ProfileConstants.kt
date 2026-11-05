package com.eeseka.lynk.user.domain

object ProfileConstants {
    const val MIN_USERNAME_LENGTH = 3
    const val MAX_USERNAME_LENGTH = 20
    const val USERNAME_PATTERN = "^(?=.*[a-zA-Z])[a-zA-Z0-9]+(?:_[a-zA-Z0-9]+)*\$"
    const val MAX_DISPLAY_NAME_LENGTH = 50
}