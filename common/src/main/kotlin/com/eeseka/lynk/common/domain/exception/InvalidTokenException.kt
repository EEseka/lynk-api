package com.eeseka.lynk.common.domain.exception

class InvalidTokenException(override val message: String?) : RuntimeException(message ?: "Invalid token")