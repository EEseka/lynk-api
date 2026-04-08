package com.eeseka.lynk.user.domain.exception

class RateLimitException(val resetsInSeconds: Long) : RuntimeException(
    "Rate limit exceeded. Please try again in $resetsInSeconds seconds."
)