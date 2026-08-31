package com.eeseka.lynk.user

import com.eeseka.lynk.common.domain.exception.InvalidTokenException
import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.authenticatedAs
import com.eeseka.lynk.user.api.dto.AuthenticatedUserDto
import com.eeseka.lynk.user.api.dto.UserDto
import com.eeseka.lynk.user.domain.model.AuthProvider
import com.eeseka.lynk.user.infra.database.repositories.UserRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.willThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Getting in, staying in, and being kept out.
 *
 * Two separate things guard every request: the `X-API-Key` that says which app is calling, and the
 * bearer token that says who is calling. Both are asserted here on their own, because a hole in
 * either one is a hole in all fifty-odd endpoints behind them.
 */
class AuthJourneyTest : IntegrationTest() {

    private companion object {
        const val API_KEY_HEADER = "X-API-Key"
    }

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `signs in a guest without asking anything of them`() {
        val apiKey = accounts.newApiKey()

        val result = mockMvc.post("/api/auth/guest") {
            header(API_KEY_HEADER, apiKey)
        }.andExpect { status { isOk() } }.andReturn()

        val authenticated = authenticatedUserFrom(result)
        assertEquals(AuthProvider.GUEST, authenticated.user.authProvider)
        assertNull(authenticated.user.email, "a guest was never asked for an email")
        assertTrue(authenticated.accessToken.isNotBlank())
        assertTrue(authenticated.refreshToken.isNotBlank())
        assertEquals(1, userRepository.count())
    }

    @Test
    fun `creates an account the first time somebody signs in with Google`() {
        val account = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", withProfileCompletion = false)

        val user = userRepository.findById(account.userId)
        assertTrue(user.isPresent)
        assertEquals("ada@lynk.test", user.get().email)
        assertEquals(AuthProvider.GOOGLE, user.get().authProvider)
        assertNull(user.get().username, "a new account has no username until the profile is made")
    }

    @Test
    fun `gives the same account back the second time`() {
        val first = accounts.signIn(email = "ada@lynk.test")
        val second = accounts.signIn(email = "ada@lynk.test")

        assertEquals(first.userId, second.userId)
        assertEquals(1, userRepository.count(), "signing in again made a second account")
    }

    @Test
    fun `turns away a Google token Google will not vouch for`() {
        val apiKey = accounts.newApiKey()
        willThrow(InvalidTokenException("Failed to verify Google Token"))
            .given(googleAuthService).verify(anyString())

        mockMvc.post("/api/auth/google") {
            contentType = MediaType.APPLICATION_JSON
            header(API_KEY_HEADER, apiKey)
            content = """{"token":"a-token-from-somewhere-else"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_TOKEN") }
        }

        assertEquals(0, userRepository.count())
    }

    @Test
    fun `swaps a refresh token for a fresh pair`() {
        val account = accounts.signIn()

        val result = refreshWith(account.refreshToken, account.apiKey)
            .andExpect { status { isOk() } }
            .andReturn()

        val refreshed = authenticatedUserFrom(result)
        assertEquals(account.userId, refreshed.user.id)
        assertNotEquals(account.refreshToken, refreshed.refreshToken, "the refresh token was not rotated")
    }

    /**
     * Refresh tokens are one-use. A second attempt with the same one is either a replay or a stolen
     * token, and neither should be handed a new session.
     */
    @Test
    fun `refuses a refresh token that has already been spent`() {
        val account = accounts.signIn()
        refreshWith(account.refreshToken, account.apiKey).andExpect { status { isOk() } }

        refreshWith(account.refreshToken, account.apiKey).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `ends the session when somebody logs out`() {
        val account = accounts.signIn()

        mockMvc.post("/api/auth/logout") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(account)
            content = """{"refreshToken":"${account.refreshToken}"}"""
        }.andExpect { status { isOk() } }

        refreshWith(account.refreshToken, account.apiKey).andExpect { status { isUnauthorized() } }
    }

    /**
     * Two devices, one account. Their sessions are separate things: signing out of the phone must
     * leave the tablet signed in.
     */
    @Test
    fun `keeps a second device signed in when the first signs out`() {
        val phone = accounts.signIn(email = "ada@lynk.test")
        val tablet = accounts.signIn(email = "ada@lynk.test")

        assertNotEquals(
            phone.refreshToken,
            tablet.refreshToken,
            "both devices were handed the same refresh token"
        )

        mockMvc.post("/api/auth/logout") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(phone)
            content = """{"refreshToken":"${phone.refreshToken}"}"""
        }.andExpect { status { isOk() } }

        refreshWith(tablet.refreshToken, tablet.apiKey).andExpect { status { isOk() } }
    }

    @Test
    fun `serves the signed-in user their own account`() {
        val account = accounts.signIn(email = "ada@lynk.test", displayName = "Ada")

        val result = mockMvc.get("/api/users/me") {
            authenticatedAs(account)
        }.andExpect { status { isOk() } }.andReturn()

        val user = objectMapper.readValue(result.response.contentAsString, UserDto::class.java)
        assertEquals(account.userId, user.id)
        assertEquals("Ada", user.displayName)
    }

    @Test
    fun `turns away a request with no API key`() {
        val account = accounts.signIn()

        mockMvc.get("/api/users/me") {
            header("Authorization", "Bearer ${account.accessToken}")
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("MISSING_API_KEY") }
        }
    }

    @Test
    fun `turns away an API key that was never issued`() {
        val account = accounts.signIn()

        mockMvc.get("/api/users/me") {
            header(API_KEY_HEADER, "lynk_a_key_we_never_issued")
            header("Authorization", "Bearer ${account.accessToken}")
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_API_KEY") }
        }
    }

    @Test
    fun `turns away a request from nobody`() {
        val apiKey = accounts.newApiKey()

        mockMvc.get("/api/users/me") {
            header(API_KEY_HEADER, apiKey)
        }.andExpect { status { isUnauthorized() } }
    }

    /**
     * A guest account is read-only by design. Everything that writes is closed to them unless it is
     * marked otherwise, so a new endpoint is guest-proof the day it is written.
     */
    @Test
    fun `will not let a guest write anything`() {
        val guest = accounts.signInAsGuest()

        mockMvc.post("/api/users/profile") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(guest)
            content = """{"username":"ada","displayName":"Ada","profilePhotoUrl":null}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("GUEST_ACTION_NOT_ALLOWED") }
        }
    }

    /** The one write a guest is allowed: leaving. */
    @Test
    fun `lets a guest delete their own account`() {
        val guest = accounts.signInAsGuest()

        mockMvc.delete("/api/auth/account") {
            authenticatedAs(guest)
        }.andExpect { status { isOk() } }

        assertEquals(0, userRepository.count())
    }

    @Test
    fun `issues an API key to the admin`() {
        mockMvc.post("/api/auth/apiKey") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", basicAuth("test_admin", "test_password"))
            content = """{"email":"partner@lynk.test"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.key") { exists() }
        }
    }

    @Test
    fun `refuses to issue an API key on the wrong password`() {
        mockMvc.post("/api/auth/apiKey") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", basicAuth("test_admin", "not-the-password"))
            content = """{"email":"partner@lynk.test"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    private fun refreshWith(refreshToken: String, apiKey: String) = mockMvc.post("/api/auth/refresh") {
        contentType = MediaType.APPLICATION_JSON
        header(API_KEY_HEADER, apiKey)
        content = """{"refreshToken":"$refreshToken"}"""
    }

    private fun authenticatedUserFrom(result: MvcResult): AuthenticatedUserDto =
        objectMapper.readValue(result.response.contentAsString, AuthenticatedUserDto::class.java)

    private fun basicAuth(username: String, password: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())
}
