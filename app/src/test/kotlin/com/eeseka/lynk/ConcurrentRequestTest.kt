package com.eeseka.lynk

import com.eeseka.lynk.notification.domain.model.Platform
import com.eeseka.lynk.notification.infra.database.entities.DeviceTokenEntity
import com.eeseka.lynk.notification.infra.database.repositories.DeviceTokenRepository
import com.eeseka.lynk.spot.domain.model.SpotCategory
import com.eeseka.lynk.spot.infra.database.entities.SavedSpotEntity
import com.eeseka.lynk.spot.infra.database.repositories.SavedSpotRepository
import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.authenticatedAs
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The same request sent several times at once, the way a double tap or two phones send it.
 *
 * A repeat that finds its work already done has to end cleanly: the same answer as the first, or a plain
 * "not found", never a crash. Each of these crashed with a 409 or a 500 before its delete became one query.
 */
class ConcurrentRequestTest : IntegrationTest() {

    @Autowired
    private lateinit var savedSpotRepository: SavedSpotRepository

    @Autowired
    private lateinit var deviceTokenRepository: DeviceTokenRepository

    private companion object {
        // Two requests rarely overlap closely enough to collide; eight reliably do.
        const val RACERS = 8
    }

    @Test
    fun `unsaves a spot cleanly when several unsaves arrive together`() {
        val ada = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")
        savedSpotRepository.save(
            SavedSpotEntity(
                userId = ada.userId,
                googlePlaceId = "ChIJtestspot",
                name = "Terra Kulture",
                category = SpotCategory.CAFE,
                latitude = 6.43,
                longitude = 3.42
            )
        )

        val statuses = race {
            mockMvc.delete("/api/spots/ChIJtestspot/save") { authenticatedAs(ada) }.andReturn()
        }

        assertTrue(statuses.all { it == 204 }, "every unsave should succeed: $statuses")
        assertEquals(0, savedSpotRepository.count())
    }

    @Test
    fun `removes a push token cleanly when several removals arrive together`() {
        val ada = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")
        deviceTokenRepository.save(DeviceTokenEntity(userId = ada.userId, token = "phone-token", platform = Platform.ANDROID))

        val statuses = race {
            mockMvc.delete("/api/notifications/phone-token") { authenticatedAs(ada) }.andReturn()
        }

        assertTrue(statuses.all { it == 204 }, "every removal should succeed: $statuses")
        assertEquals(0, deviceTokenRepository.count())
    }

    @Test
    fun `registers a new push token once when several registrations arrive together`() {
        val ada = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")
        given(firebasePushNotificationClient.isValidToken(any())).willReturn(true)

        val statuses = race {
            mockMvc.post("/api/notifications/register") {
                contentType = MediaType.APPLICATION_JSON
                authenticatedAs(ada)
                content = """{"token":"new-phone-token","platform":"ANDROID"}"""
            }.andReturn()
        }

        assertTrue(statuses.all { it in 200..299 }, "every registration should succeed: $statuses")
        assertEquals(1, deviceTokenRepository.findAll().count { it.token == "new-phone-token" })
    }

    @Test
    fun `registers a known push token cleanly while it is being removed`() {
        val ada = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")
        deviceTokenRepository.save(DeviceTokenEntity(userId = ada.userId, token = "phone-token", platform = Platform.ANDROID))
        given(firebasePushNotificationClient.isValidToken(any())).willReturn(true)

        // Half re-register the token, half remove it, all at once
        var turn = 0
        val statuses = race {
            if (synchronized(this) { turn++ } % 2 == 0) {
                mockMvc.post("/api/notifications/register") {
                    contentType = MediaType.APPLICATION_JSON
                    authenticatedAs(ada)
                    content = """{"token":"phone-token","platform":"ANDROID"}"""
                }.andReturn()
            } else {
                mockMvc.delete("/api/notifications/phone-token") { authenticatedAs(ada) }.andReturn()
            }
        }

        assertTrue(statuses.all { it in 200..299 }, "every request should succeed: $statuses")
        assertTrue(deviceTokenRepository.findAll().count { it.token == "phone-token" } <= 1)
    }

    @Test
    fun `logs out cleanly when several logouts arrive together`() {
        val ada = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")

        val statuses = race {
            mockMvc.post("/api/auth/logout") {
                contentType = MediaType.APPLICATION_JSON
                authenticatedAs(ada)
                content = """{"refreshToken":"${ada.refreshToken}"}"""
            }.andReturn()
        }

        assertTrue(statuses.all { it in 200..299 }, "every logout should succeed: $statuses")
    }

    @Test
    fun `lets one refresh token buy only one new session`() {
        val ada = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")

        val statuses = race {
            mockMvc.post("/api/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                authenticatedAs(ada)
                content = """{"refreshToken":"${ada.refreshToken}"}"""
            }.andReturn()
        }

        assertEquals(1, statuses.count { it == 200 }, "expected exactly one refresh to succeed: $statuses")
        assertTrue(statuses.all { it == 200 || it == 401 }, "a refresh failed for the wrong reason: $statuses")
    }

    @Test
    fun `deletes an account once and says goodbye once when several deletions arrive together`() {
        val ada = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")

        val statuses = race {
            mockMvc.delete("/api/auth/account") { authenticatedAs(ada) }.andReturn()
        }
        broker.deliverEvents()

        assertEquals(1, statuses.count { it == 200 }, "expected exactly one deletion to succeed: $statuses")
        assertTrue(statuses.all { it == 200 || it == 404 }, "a deletion failed for the wrong reason: $statuses")
        verify(brevoEmailClient, times(1)).sendHtmlEmail(eq("ada@lynk.test"), eq("Sorry to see you go"), any())
    }

    /** Every request waits at the same barrier, so they all start together. */
    private fun race(call: () -> MvcResult): List<Int> {
        val barrier = CyclicBarrier(RACERS)
        val executor = Executors.newFixedThreadPool(RACERS)
        try {
            return (1..RACERS)
                .map { executor.submit<MvcResult> { barrier.await(); call() } }
                .map { it.get(30, TimeUnit.SECONDS).response.status }
        } finally {
            executor.shutdownNow()
        }
    }
}
