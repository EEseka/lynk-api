package com.eeseka.lynk.infra.rate_limiting

import com.eeseka.lynk.common.domain.exception.RateLimitException
import com.eeseka.lynk.common.domain.type.UserId
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class UserRateLimiter(
    private val redisTemplate: StringRedisTemplate
) {
    companion object {
        private const val USER_RATE_LIMIT_PREFIX = "rate_limit:user"
    }

    @Value("classpath:fixed_window_rate_limit.lua")
    lateinit var rateLimitResource: Resource

    private val rateLimitScript by lazy {
        val script = rateLimitResource.inputStream.use {
            it.readBytes().decodeToString()
        }
        @Suppress("UNCHECKED_CAST")
        DefaultRedisScript(script, List::class.java as Class<List<Long>>)
    }

    fun <T> withUserRateLimit(
        userId: UserId,
        route: String,
        resetsIn: Duration,
        maxRequestsPerUser: Int,
        action: () -> T
    ): T {
        val key = "$USER_RATE_LIMIT_PREFIX:$route:$userId"

        val result = redisTemplate.execute(
            rateLimitScript,
            listOf(key),
            maxRequestsPerUser.toString(),
            resetsIn.seconds.toString()
        )

        val currentCount = result[0]

        return if (currentCount <= maxRequestsPerUser) {
            action()
        } else {
            val ttl = result[1]
            throw RateLimitException(resetsInSeconds = ttl)
        }
    }
}
