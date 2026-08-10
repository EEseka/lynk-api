package com.eeseka.lynk.common.api.util

import org.springframework.security.core.context.SecurityContextHolder

const val GUEST_AUTHORITY = "GUEST"

val isGuestRequest: Boolean
    get() = SecurityContextHolder.getContext().authentication?.authorities?.any { it.authority == GUEST_AUTHORITY } == true