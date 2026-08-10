package com.eeseka.lynk.lobby.api.websocket

import com.eeseka.lynk.common.service.JwtService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

@Component
class LobbyHandshakeInterceptor(
    private val jwtService: JwtService
) : HandshakeInterceptor {

    companion object {
        const val USER_ID_ATTRIBUTE = "userId"
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        val authHeader = request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return refuse(response, HttpStatus.UNAUTHORIZED, "missing or malformed Authorization header")
        }

        if (!jwtService.validateAccessToken(authHeader)) {
            return refuse(response, HttpStatus.UNAUTHORIZED, "not a valid access token")
        }

        if (jwtService.isGuestAccessToken(authHeader)) {
            return refuse(response, HttpStatus.FORBIDDEN, "guests have no lobby to join")
        }

        attributes[USER_ID_ATTRIBUTE] = jwtService.getUserIdFromToken(authHeader)

        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) = Unit

    private fun refuse(response: ServerHttpResponse, status: HttpStatus, reason: String): Boolean {
        logger.warn("Lobby handshake refused: {}", reason)
        response.setStatusCode(status)

        return false
    }
}
