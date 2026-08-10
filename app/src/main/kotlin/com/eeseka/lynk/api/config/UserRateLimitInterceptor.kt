package com.eeseka.lynk.api.config

import com.eeseka.lynk.common.api.config.UserRateLimit
import com.eeseka.lynk.common.api.util.requestUserId
import com.eeseka.lynk.infra.rate_limiting.UserRateLimiter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import java.time.Duration

@Component
class UserRateLimitInterceptor(
    private val userRateLimiter: UserRateLimiter,
    @param:Value("\${lynk.rate-limit.user.apply-limit}")
    private val applyLimit: Boolean
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler is HandlerMethod && applyLimit) {
            val annotation = handler.getMethodAnnotation(UserRateLimit::class.java)
            if (annotation != null) {
                return userRateLimiter.withUserRateLimit(
                    userId = requestUserId, // Security's filter chain has already run by now, so the user is on the context.
                    route = "${handler.beanType.simpleName}.${handler.method.name}",
                    resetsIn = Duration.of(
                        annotation.duration,
                        annotation.unit.toChronoUnit()
                    ),
                    maxRequestsPerUser = annotation.requests,
                    action = { true }
                )
            }
        }

        return true
    }
}
