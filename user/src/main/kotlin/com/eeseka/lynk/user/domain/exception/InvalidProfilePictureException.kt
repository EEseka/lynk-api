package com.eeseka.lynk.user.domain.exception

class InvalidProfilePictureException(override val message: String?) : RuntimeException(
    message ?: "Invalid profile picture data"
)