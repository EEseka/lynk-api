package com.eeseka.lynk.user.api.config

import com.eeseka.lynk.common.api.util.GUEST_AUTHORITY
import com.eeseka.lynk.common.domain.exception.InvalidTokenException
import com.eeseka.lynk.common.service.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            if (jwtService.validateAccessToken(authHeader)) {
                val auth = try {
                    val userId = jwtService.getUserIdFromToken(authHeader)
                    val authorities = if (jwtService.isGuestAccessToken(authHeader)) {
                        listOf(SimpleGrantedAuthority(GUEST_AUTHORITY))
                    } else {
                        emptyList()
                    }
                    UsernamePasswordAuthenticationToken(userId, null, authorities)
                } catch (e: InvalidTokenException) {
                    sendUnauthorizedResponse(response, e.message)
                    return
                }

                SecurityContextHolder.getContext().authentication = auth
            }
        }
        filterChain.doFilter(request, response)
    }

    // Written by hand to match what the exception handlers send, because filters run before the
    // DispatcherServlet and nothing downstream will ever get the chance to format this.
    private fun sendUnauthorizedResponse(response: HttpServletResponse, message: String?) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = "application/json"
        response.writer.write("""{"code": "INVALID_TOKEN", "message": "$message"}""")
    }
}