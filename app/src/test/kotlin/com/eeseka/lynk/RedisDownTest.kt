package com.eeseka.lynk

import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.authenticatedAs
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * Only the rate limiters need Redis, so an outage is theirs to decide. The front door (signing in)
 * stays open, and anything that spends money or reveals someone's details waits it out with a 503.
 *
 * Redis is pointed at a port nothing listens on, so every count fails the way a real outage would.
 */
@TestPropertySource(
    properties = [
        "spring.data.redis.port=1",
        "lynk.rate-limit.ip.apply-limit=true",
        "lynk.rate-limit.user.apply-limit=true"
    ]
)
class RedisDownTest : IntegrationTest() {

    private companion object {
        const val API_KEY_HEADER = "X-API-Key"
    }

    @Test
    fun `still lets a guest in`() {
        mockMvc.post("/api/auth/guest") {
            header(API_KEY_HEADER, accounts.newApiKey())
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `still lets somebody sign in with Google`() {
        val user = accounts.signIn()

        mockMvc.get("/api/users/me") {
            authenticatedAs(user)
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `holds off a spot search until Redis is back`() {
        val user = accounts.signIn()

        mockMvc.get("/api/spots/search") {
            authenticatedAs(user)
            param("latitude", "6.4281")
            param("longitude", "3.4219")
        }.andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("RATE_LIMITER_UNAVAILABLE") }
        }
    }

    @Test
    fun `holds off a bank account lookup until Redis is back`() {
        val user = accounts.signIn()

        mockMvc.get("/api/payments/bank-account") {
            authenticatedAs(user)
            param("accountNumber", "0123456789")
            param("bankCode", "058")
        }.andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("RATE_LIMITER_UNAVAILABLE") }
        }
    }
}
