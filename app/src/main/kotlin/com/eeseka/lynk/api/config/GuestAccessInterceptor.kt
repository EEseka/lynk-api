package com.eeseka.lynk.api.config

import com.eeseka.lynk.common.api.config.AllowGuest
import com.eeseka.lynk.common.api.util.isGuestRequest
import com.eeseka.lynk.common.domain.exception.GuestActionNotAllowedException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
class GuestAccessInterceptor : HandlerInterceptor {

    companion object {
        private val SAFE_METHODS = setOf("GET", "HEAD", "OPTIONS")
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler is HandlerMethod && request.method !in SAFE_METHODS) {
            val annotation = handler.getMethodAnnotation(AllowGuest::class.java)
            if (annotation == null) {
                if (isGuestRequest) {
                    throw GuestActionNotAllowedException()
                }
            }
        }

        return true
    }
}
