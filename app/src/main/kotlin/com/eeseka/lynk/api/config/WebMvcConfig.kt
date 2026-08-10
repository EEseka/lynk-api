package com.eeseka.lynk.api.config

import org.springframework.stereotype.Component
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Component
class WebMvcConfig(
    private val ipRateLimitInterceptor: IpRateLimitInterceptor,
    private val userRateLimitInterceptor: UserRateLimitInterceptor,
    private val guestAccessInterceptor: GuestAccessInterceptor
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry
            .addInterceptor(ipRateLimitInterceptor)
            .addPathPatterns("/api/**")

        registry
            .addInterceptor(userRateLimitInterceptor)
            .addPathPatterns("/api/**")

        registry
            .addInterceptor(guestAccessInterceptor)
            .addPathPatterns("/api/**")
    }
}