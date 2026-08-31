package com.eeseka.lynk.notification

import com.eeseka.lynk.notification.domain.model.NotificationType
import com.eeseka.lynk.notification.infra.database.repositories.DeviceTokenRepository
import com.eeseka.lynk.notification.infra.database.repositories.NotificationRepository
import com.eeseka.lynk.notification.service.NotificationService
import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.TestAccount
import com.eeseka.lynk.support.authenticatedAs
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The notification list a person pulls down, and the device the push goes to.
 *
 * Everything here is per-person, so the tests mostly ask the same question twice: does the owner see
 * it, and does nobody else.
 */
class NotificationJourneyTest : IntegrationTest() {

    private companion object {
        const val DEVICE_TOKEN = "fcm-token-for-adas-phone"
    }

    @Autowired
    private lateinit var notificationService: NotificationService

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var deviceTokenRepository: DeviceTokenRepository

    @Test
    fun `shows somebody their own notifications and nobody else's`() {
        val ada = signIn("ada")
        val bola = signIn("bola")
        notify(ada, "Sunday jollof")
        notify(bola, "Beach day")

        mockMvc.get("/api/notifications") {
            authenticatedAs(ada)
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].hangoutName") { value("Sunday jollof") }
            jsonPath("$[0].isRead") { value(false) }
        }
    }

    @Test
    fun `counts what somebody has not read`() {
        val ada = signIn("ada")
        notify(ada, "Sunday jollof")
        notify(ada, "Beach day")

        mockMvc.get("/api/notifications/unread-count") {
            authenticatedAs(ada)
        }.andExpect {
            status { isOk() }
            jsonPath("$.count") { value(2) }
        }
    }

    @Test
    fun `marks one notification read`() {
        val ada = signIn("ada")
        notify(ada, "Sunday jollof")
        notify(ada, "Beach day")
        val first = notificationRepository.findAll().first()

        mockMvc.patch("/api/notifications/${first.id}/read") {
            authenticatedAs(ada)
        }.andExpect { status { isNoContent() } }

        assertTrue(notificationRepository.findById(first.id!!).get().isRead)
        mockMvc.get("/api/notifications/unread-count") {
            authenticatedAs(ada)
        }.andExpect { jsonPath("$.count") { value(1) } }
    }

    @Test
    fun `will not let somebody read another person's notification`() {
        val ada = signIn("ada")
        val bola = signIn("bola")
        notify(ada, "Sunday jollof")
        val adasNotification = notificationRepository.findAll().first()

        mockMvc.patch("/api/notifications/${adasNotification.id}/read") {
            authenticatedAs(bola)
        }.andExpect { status { isNotFound() } }

        assertTrue(!notificationRepository.findById(adasNotification.id!!).get().isRead)
    }

    @Test
    fun `says nothing is there for a notification that never existed`() {
        val ada = signIn("ada")

        mockMvc.patch("/api/notifications/${UUID.randomUUID()}/read") {
            authenticatedAs(ada)
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `marks everything read at once`() {
        val ada = signIn("ada")
        val bola = signIn("bola")
        notify(ada, "Sunday jollof")
        notify(ada, "Beach day")
        notify(bola, "Someone else's plans")

        mockMvc.patch("/api/notifications/read-all") {
            authenticatedAs(ada)
        }.andExpect { status { isNoContent() } }

        mockMvc.get("/api/notifications/unread-count") {
            authenticatedAs(ada)
        }.andExpect { jsonPath("$.count") { value(0) } }
        mockMvc.get("/api/notifications/unread-count") {
            authenticatedAs(bola)
        }.andExpect { jsonPath("$.count") { value(1) } }
    }

    @Test
    fun `refuses to be asked for more notifications than it will give`() {
        val ada = signIn("ada")

        mockMvc.get("/api/notifications") {
            authenticatedAs(ada)
            param("pageSize", "500")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_ERROR") }
        }
    }

    @Test
    fun `registers the device a push should go to`() {
        val ada = signIn("ada")
        given(firebasePushNotificationClient.isValidToken(eq(DEVICE_TOKEN))).willReturn(true)

        registerDevice(ada, DEVICE_TOKEN).andExpect {
            status { isOk() }
            jsonPath("$.token") { value(DEVICE_TOKEN) }
        }

        assertEquals(1, deviceTokenRepository.count())
    }

    /** Firebase hands the same device a new token now and then; the old row moves rather than piles up. */
    @Test
    fun `keeps one row for a device registered twice`() {
        val ada = signIn("ada")
        given(firebasePushNotificationClient.isValidToken(eq(DEVICE_TOKEN))).willReturn(true)

        registerDevice(ada, DEVICE_TOKEN).andExpect { status { isOk() } }
        registerDevice(ada, DEVICE_TOKEN).andExpect { status { isOk() } }

        assertEquals(1, deviceTokenRepository.count())
    }

    @Test
    fun `turns away a token Firebase does not recognise`() {
        val ada = signIn("ada")
        given(firebasePushNotificationClient.isValidToken(eq("not-a-real-token"))).willReturn(false)

        registerDevice(ada, "not-a-real-token").andExpect { status { isBadRequest() } }

        assertEquals(0, deviceTokenRepository.count())
    }

    @Test
    fun `forgets a device that signed out`() {
        val ada = signIn("ada")
        given(firebasePushNotificationClient.isValidToken(eq(DEVICE_TOKEN))).willReturn(true)
        registerDevice(ada, DEVICE_TOKEN).andExpect { status { isOk() } }

        mockMvc.delete("/api/notifications/$DEVICE_TOKEN") {
            authenticatedAs(ada)
        }.andExpect { status { isNoContent() } }

        assertEquals(0, deviceTokenRepository.count())
    }

    /** Registering a device is a write, and a guest may not write. */
    @Test
    fun `will not register a device for a guest`() {
        val guest = accounts.signInAsGuest()

        registerDevice(guest, DEVICE_TOKEN).andExpect { status { isForbidden() } }
    }

    private fun registerDevice(account: TestAccount, token: String) =
        mockMvc.post("/api/notifications/register") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(account)
            content = """{"token":"$token","platform":"ANDROID"}"""
        }

    private fun notify(account: TestAccount, hangoutName: String) {
        notificationService.createNotifications(
            userIds = setOf(account.userId),
            type = NotificationType.PARTICIPANT_INVITED,
            hangoutId = UUID.randomUUID(),
            hangoutName = hangoutName,
            actorDisplayName = "Ada"
        )
    }

    private fun signIn(username: String): TestAccount = accounts.signIn(
        email = "$username@lynk.test",
        displayName = username.replaceFirstChar { it.uppercase() },
        username = username
    )
}
