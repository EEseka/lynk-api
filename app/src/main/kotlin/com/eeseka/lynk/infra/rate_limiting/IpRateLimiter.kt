package com.eeseka.lynk.infra.rate_limiting

import com.eeseka.lynk.common.api.config.WhenRedisIsDown
import com.eeseka.lynk.common.domain.exception.RateLimitException
import com.eeseka.lynk.common.domain.exception.RateLimiterUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.core.io.Resource
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class IpRateLimiter(
    private val redisTemplate: StringRedisTemplate
) {
    companion object {
        private const val IP_RATE_LIMIT_PREFIX = "rate_limit:ip"
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

    fun <T> withIpRateLimit(
        ipAddress: String,
        route: String,
        resetsIn: Duration,
        maxRequestsPerIp: Int,
        whenRedisIsDown: WhenRedisIsDown,
        action: () -> T
    ): T {
        val key = "$IP_RATE_LIMIT_PREFIX:$route:$ipAddress"

        val result = try {
            redisTemplate.execute(
                rateLimitScript,
                listOf(key),
                maxRequestsPerIp.toString(),
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

        return if (currentCount <= maxRequestsPerIp) {
            action()
        } else {
            val ttl = result[1]
            throw RateLimitException(resetsInSeconds = ttl)
        }
    }
}