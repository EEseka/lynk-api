package com.eeseka.lynk.user.api.config

import com.eeseka.lynk.user.service.ApiKeyService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class ApiKeyAuthFilter(
    private val apiKeyService: ApiKeyService
) : OncePerRequestFilter() {

    companion object Companion {
        private const val API_KEY_HEADER = "X-API-Key"
        private const val AUTH_API_KEY_PATH = "/api/auth/apiKey"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (shouldSkipAuthentication(request)) {
            filterChain.doFilter(request, response)
            return
        }

        val apiKey = request.getHeader(API_KEY_HEADER)

        if (apiKey.isNullOrBlank()) {
            sendUnauthorizedResponse(response, "Missing API key. Make sure to attach it as an X-API-Key header.")
            return
        }

        if (!apiKeyService.isValidKey(apiKey)) {
            sendUnauthorizedResponse(response, "Invalid API key")
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun shouldSkipAuthentication(request: HttpServletRequest): Boolean {
        return request.method == HttpMethod.POST.name() &&
                request.servletPath == AUTH_API_KEY_PATH
    }

    private fun sendUnauthorizedResponse(response: HttpServletResponse, message: String) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = "application/json"
        response.writer.write("""{"error": "$message"}""")
    }
}