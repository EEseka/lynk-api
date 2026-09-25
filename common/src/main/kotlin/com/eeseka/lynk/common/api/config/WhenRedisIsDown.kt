package com.eeseka.lynk.common.api.config

// What a rate-limited endpoint does when Redis can't count the request
enum class WhenRedisIsDown {
    ALLOW,
    REFUSE
}
