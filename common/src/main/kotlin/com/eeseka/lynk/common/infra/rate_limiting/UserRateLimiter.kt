package com.eeseka.lynk.common.infra.rate_limiting

import com.eeseka.lynk.common.domain.exception.RateLimitException
import com.eeseka.lynk.common.domain.exception.RateLimiterUnavailableException
import com.eeseka.lynk.common.domain.model.UserRateLimitUsage
import com.eeseka.lynk.common.domain.model.WhenRedisIsDown
import com.eeseka.lynk.common.domain.type.UserId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
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

    private val logger = LoggerFactory.getLogger(javaClass)

    @Value("classpath:fixed_window_rate_limit.lua")
    lateinit var rateLimitResource: Resource

    private val rateLimitScript by lazy {
        val script = rateLimitResource.inputStream.use {
            it.readBytes().decodeToString()
        }
        @Suppress("UNCHECKED_CAST")
        DefaultRedisScript(script, List::class.java as Class<List<Long>>)
    }

    @Value("classpath:refund_rate_limit.lua")
    lateinit var refundResource: Resource

    private val refundScript by lazy {
        val script = refundResource.inputStream.use {
            it.readBytes().decodeToString()
        }
        DefaultRedisScript(script, Long::class.java)
    }

    fun refund(userId: UserId, route: String) {
        val key = "$USER_RATE_LIMIT_PREFIX:$route:$userId"
        try {
            redisTemplate.execute(refundScript, listOf(key))
        } catch (e: DataAccessException) {
            logger.warn("Redis is down, could not refund a use of {}", route, e)
        }
    }

    fun findUsage(userId: UserId, route: String): UserRateLimitUsage {
        val key = "$USER_RATE_LIMIT_PREFIX:$route:$userId"
        return try {
            val used = redisTemplate.opsForValue().get(key)?.toIntOrNull() ?: 0
            val ttl = redisTemplate.getExpire(key).takeIf { it > 0 }
            UserRateLimitUsage(used = used, resetsInSeconds = ttl)
        } catch (e: DataAccessException) {
            logger.warn("Redis is down, could not read the usage of {}", route, e)
            throw RateLimiterUnavailableException()
        }
    }

    fun <T> withUserRateLimit(
        userId: UserId,
        route: String,
        resetsIn: Duration,
        maxRequestsPerUser: Int,
        whenRedisIsDown: WhenRedisIsDown,
        action: () -> T
    ): T {
        val key = "$USER_RATE_LIMIT_PREFIX:$route:$userId"

        val result = try {
            redisTemplate.execute(
                rateLimitScript,
                listOf(key),
                maxRequestsPerUser.toString(),
                resetsIn.seconds.toString()
            )
        } catch (e: DataAccessException) {
            logger.warn("Redis is down, {} for {}", whenRedisIsDown, route, e)
            return when (whenRedisIsDown) {
                WhenRedisIsDown.ALLOW -> action()
                WhenRedisIsDown.REFUSE -> throw RateLimiterUnavailableException()
            }
        }

        val currentCount = result[0]

        return if (currentCount <= maxRequestsPerUser) {
            action()
        } else {
            val ttl = result[1]
            throw RateLimitException(resetsInSeconds = ttl)
        }
    }
}
