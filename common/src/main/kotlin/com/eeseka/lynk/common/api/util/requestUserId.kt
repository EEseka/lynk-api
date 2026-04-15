package com.eeseka.lynk.common.api.util

import com.eeseka.lynk.common.domain.exception.UnauthorizedException
import com.eeseka.lynk.common.domain.type.UserId
import org.springframework.security.core.context.SecurityContextHolder

val requestUserId: UserId
    get() = SecurityContextHolder.getContext().authentication?.principal as? UserId
        ?: throw UnauthorizedException()