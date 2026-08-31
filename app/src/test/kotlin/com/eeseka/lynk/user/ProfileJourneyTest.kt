package com.eeseka.lynk.user

import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.TestAccount
import com.eeseka.lynk.support.authenticatedAs
import com.eeseka.lynk.user.domain.model.ProfilePictureUploadCredentials
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.kotlin.eq
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Duration
import java.time.Instant

/**
 * Naming an account, and what the rest of the application does with that name.
 *
 * Completing a profile is not only a database write: it is the moment the other modules are told
 * this person exists, so the tests follow the name out of the user module and into the hangout one.
 */
class ProfileJourneyTest : IntegrationTest() {

    @Test
    fun `names a new account`() {
        val account = signUpWithoutProfile()

        createProfile(account, username = "ada", displayName = "Ada Eze").andExpect {
            status { isOk() }
            jsonPath("$.username") { value("ada") }
            jsonPath("$.displayName") { value("Ada Eze") }
        }

        mockMvc.get("/api/users/me") {
            authenticatedAs(account)
        }.andExpect {
            status { isOk() }
            jsonPath("$.username") { value("ada") }
        }
    }

    /**
     * The hangout module keeps its own copy of who people are, built from the event this sends. Until
     * it arrives, nobody can invite this person to anything.
     */
    @Test
    fun `tells the rest of the application who they are`() {
        val account = signUpWithoutProfile()
        val searcher = accounts.signIn(email = "bola@lynk.test", displayName = "Bola", username = "bola")

        createProfile(account, username = "ada", displayName = "Ada Eze").andExpect { status { isOk() } }
        broker.deliverEvents()

        mockMvc.get("/api/participants") {
            authenticatedAs(searcher)
            param("query", "ada")
        }.andExpect {
            status { isOk() }
            jsonPath("$.displayName") { value("Ada Eze") }
        }
    }

    @Test
    fun `keeps that name to one account`() {
        accounts.signIn(email = "first@lynk.test", displayName = "Ada", username = "ada")
        val second = signUpWithoutProfile(email = "second@lynk.test")

        createProfile(second, username = "ada", displayName = "Ada Two")
            .andExpect { status { isConflict() } }
    }

    /** Casing is not a difference: `Ada` and `ada` are the same person's name to everyone else. */
    @Test
    fun `treats a name in different letters as the same name`() {
        accounts.signIn(email = "first@lynk.test", displayName = "Ada", username = "ada")
        val second = signUpWithoutProfile(email = "second@lynk.test")

        createProfile(second, username = "ADA", displayName = "Ada Two")
            .andExpect { status { isConflict() } }
    }

    @Test
    fun `refuses a name the app keeps for itself`() {
        val account = signUpWithoutProfile()

        createProfile(account, username = "support", displayName = "Support")
            .andExpect { status { isConflict() } }
    }

    @Test
    fun `will not rename an account that already has a name`() {
        val account = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")

        createProfile(account, username = "adaeze", displayName = "Ada Eze")
            .andExpect { status { isConflict() } }
    }

    @Test
    fun `refuses a username that is not one`() {
        val account = signUpWithoutProfile()

        createProfile(account, username = "no", displayName = "Ada")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `says whether a name is free`() {
        val account = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")

        mockMvc.get("/api/users/username-available") {
            authenticatedAs(account)
            param("username", "ada")
        }.andExpect {
            status { isOk() }
            jsonPath("$.isAvailable") { value(false) }
        }

        mockMvc.get("/api/users/username-available") {
            authenticatedAs(account)
            param("username", "bola")
        }.andExpect {
            status { isOk() }
            jsonPath("$.isAvailable") { value(true) }
        }
    }

    @Test
    fun `changes a display name everywhere it is shown`() {
        val account = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")
        val searcher = accounts.signIn(email = "bola@lynk.test", displayName = "Bola", username = "bola")

        mockMvc.put("/api/users/profile") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(account)
            content = """{"displayName":"Ada The Second","profilePhotoUrl":null}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.displayName") { value("Ada The Second") }
        }
        broker.deliverEvents()

        mockMvc.get("/api/participants") {
            authenticatedAs(searcher)
            param("query", "ada")
        }.andExpect {
            status { isOk() }
            jsonPath("$.displayName") { value("Ada The Second") }
        }
    }

    @Test
    fun `hands back somewhere to upload a picture`() {
        val account = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")
        given(
            supabaseUserStorageClient.generateSignedUploadUrl(
                userId = eq(account.userId),
                mimeType = eq("image/jpeg")
            )
        ).willReturn(
            ProfilePictureUploadCredentials(
                uploadUrl = "https://storage.lynk.test/upload/ada",
                publicUrl = "https://storage.lynk.test/public/ada.jpg",
                headers = mapOf("Authorization" to "Bearer storage-token"),
                expiresAt = Instant.now().plus(Duration.ofMinutes(10))
            )
        )

        mockMvc.post("/api/users/profile-picture/generate-upload-url") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(account)
            content = """{"mimeType":"image/jpeg"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.uploadUrl") { value("https://storage.lynk.test/upload/ada") }
            jsonPath("$.publicUrl") { value("https://storage.lynk.test/public/ada.jpg") }
        }
    }

    /**
     * A profile picture is only ever a file we hold. A link to somebody else's storage would have the
     * app showing, and later trying to delete, something that was never ours.
     */
    @Test
    fun `refuses a picture from somebody else's storage`() {
        val account = signUpWithoutProfile()

        createProfile(
            account = account,
            username = "ada",
            displayName = "Ada",
            profilePhotoUrl = "https://someone-else.supabase.co/storage/v1/object/public/avatars/ada.jpg"
        ).andExpect { status { isBadRequest() } }
    }

    private fun signUpWithoutProfile(email: String = "ada@lynk.test"): TestAccount =
        accounts.signIn(email = email, withProfileCompletion = false)

    private fun createProfile(
        account: TestAccount,
        username: String,
        displayName: String,
        profilePhotoUrl: String? = null
    ): ResultActionsDsl = mockMvc.post("/api/users/profile") {
        contentType = MediaType.APPLICATION_JSON
        authenticatedAs(account)
        content = """
            {
              "username": "$username",
              "displayName": "$displayName",
              "profilePhotoUrl": ${profilePhotoUrl?.let { "\"$it\"" } ?: "null"}
            }
        """.trimIndent()
    }
}
