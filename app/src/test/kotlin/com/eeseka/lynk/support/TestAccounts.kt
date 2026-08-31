package com.eeseka.lynk.support

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.user.api.dto.AuthenticatedUserDto
import com.eeseka.lynk.user.service.ApiKeyService
import com.eeseka.lynk.user.service.GoogleAuthService
import org.mockito.BDDMockito.given
import org.springframework.boot.test.context.TestComponent
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper

/**
 * Somebody who can call the API, arranged in one line.
 *
 * Every request past the front door needs two separate things — `X-API-Key` for the app and a
 * bearer token for the person — and a test about Hangouts should not have to say so. Accounts are
 * made through the real endpoints rather than by writing rows, so what the tests use is what a
 * device would get.
 */
@TestComponent
class TestAccounts(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val apiKeyService: ApiKeyService,
    private val googleAuthService: GoogleAuthService,
    private val broker: TestBroker
) {

    private companion object {
        const val API_KEY_HEADER = "X-API-Key"
    }

    /**
     * A signed-in user. Google is the only way to a full account, so the verifier is told what the
     * token it is about to be handed means.
     *
     * The profile is completed by default because that is the point at which the other modules are
     * told this person exists — without it, they cannot host or be invited to anything. Pass
     * `withProfile = false` for a test about that gap.
     */
    fun signIn(
        email: String = "ada@lynk.test",
        displayName: String = "Ada",
        username: String = usernameFor(email),
        withProfileCompletion: Boolean = true
    ): TestAccount {
        val apiKey = newApiKey()
        val googleToken = "google-token-for-$email"

        given(googleAuthService.verify(googleToken)).willReturn(
            GoogleAuthService.GoogleUser(
                email = email,
                displayName = displayName,
                pictureUrl = null
            )
        )

        val response = mockMvc.post("/api/auth/google") {
            contentType = MediaType.APPLICATION_JSON
            header(API_KEY_HEADER, apiKey)
            content = """{"token":"$googleToken"}"""
        }.andReturn().response.contentAsString

        val authenticated = objectMapper.readValue(response, AuthenticatedUserDto::class.java)
        val account = accountFrom(authenticated, apiKey)

        if (withProfileCompletion && authenticated.user.username == null) {
            completeProfile(account, username = username, displayName = displayName)
        }

        return account
    }

    fun completeProfile(
        account: TestAccount,
        username: String,
        displayName: String
    ) {
        mockMvc.post("/api/users/profile") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(account)
            content = """{"username":"$username","displayName":"$displayName","profilePhotoUrl":null}"""
        }.andExpect { status { isOk() } }

        broker.deliverEvents()
    }

    fun signInAsGuest(): TestAccount {
        val apiKey = newApiKey()

        val response = mockMvc.post("/api/auth/guest") {
            header(API_KEY_HEADER, apiKey)
        }.andReturn().response.contentAsString

        return accountFrom(objectMapper.readValue(response, AuthenticatedUserDto::class.java), apiKey)
    }

    fun newApiKey(): String = apiKeyService.createKey("tests@lynk.test").key

    private fun accountFrom(authenticated: AuthenticatedUserDto, apiKey: String): TestAccount {
        return TestAccount(
            userId = authenticated.user.id,
            accessToken = authenticated.accessToken,
            refreshToken = authenticated.refreshToken,
            apiKey = apiKey
        )
    }

    private fun usernameFor(email: String): String =
        email.substringBefore("@").filter { it.isLetterOrDigit() }.take(12).ifEmpty { "guest" }
}

data class TestAccount(
    val userId: UserId,
    val accessToken: String,
    val refreshToken: String,
    val apiKey: String
)

/** Attaches both halves of what the filters ask for: the app's key and the person's token. */
fun MockHttpServletRequestDsl.authenticatedAs(account: TestAccount) {
    header("X-API-Key", account.apiKey)
    header("Authorization", "Bearer ${account.accessToken}")
}