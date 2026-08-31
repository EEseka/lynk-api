package com.eeseka.lynk.infra.rate_limiting

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

/**
 * The address a request is counted against.
 *
 * Nothing is read from a header here. In production `server.forward-headers-strategy: framework`
 * puts Spring's own ForwardedHeaderFilter ahead of everything, and it has already replaced the
 * proxy's address with the caller's by the time this runs. Locally, there is no proxy, and the
 * address is the caller's to begin with.
 */
@Component
class IpResolver {

    fun getClientIp(request: HttpServletRequest): String = request.remoteAddr
}