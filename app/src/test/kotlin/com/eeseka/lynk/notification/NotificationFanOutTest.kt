package com.eeseka.lynk.notification

import com.eeseka.lynk.hangout.api.dto.HangoutDto
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.notification.domain.model.DeviceToken
import com.eeseka.lynk.notification.domain.model.NotificationType
import com.eeseka.lynk.notification.domain.model.Platform
import com.eeseka.lynk.notification.domain.model.PushNotification
import com.eeseka.lynk.notification.domain.model.PushNotificationSendResult
import com.eeseka.lynk.notification.infra.database.repositories.DeviceTokenRepository
import com.eeseka.lynk.notification.infra.database.repositories.NotificationRepository
import com.eeseka.lynk.notification.service.PushNotificationService
import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.TestAccount
import com.eeseka.lynk.support.authenticatedAs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the rest of the application does to somebody's phone.
 *
 * Nothing in the notification module is called directly by anything: it only ever hears about the
 * world through events. These tests do the real thing over HTTP — invite, leave, cancel — and then
 * hand what was published to the listener, so the whole chain from a tap to a row and a push is
 * covered in one go.
 */
class NotificationFanOutTest : IntegrationTest() {

    private companion object {
        const val DEVICE_TOKEN = "fcm-token-for-bolas-phone"
    }

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var pushNotificationService: PushNotificationService

    @Autowired
    private lateinit var deviceTokenRepository: DeviceTokenRepository

    @BeforeEach
    fun stubDeliveryChannels() {
        given(firebasePushNotificationClient.isValidToken(eq(DEVICE_TOKEN))).willReturn(true)
        given(firebasePushNotificationClient.sendNotification(any()))
            .willReturn(PushNotificationSendResult(emptyList(), emptyList(), emptyList()))
    }

    @Test
    fun `tells somebody they were invited`() {
        val host = signIn("ada")
        val friend = signIn("bola")
        registerDevice(friend)
        val hangout = createHangout(host)

        invite(host, hangout, friend)
        broker.deliverEvents()

        val notification = notificationRepository.findAll().single()
        assertEquals(friend.userId, notification.userId)
        assertEquals(NotificationType.PARTICIPANT_INVITED, notification.type)
        assertEquals(hangout.name, notification.hangoutName)
        assertEquals("Ada", notification.actorDisplayName)

        val push = capturePush()
        assertEquals(hangout.name, push.title)
        assertTrue(push.message.contains("Ada"), "the push does not say who invited them")
        assertEquals(setOf(DEVICE_TOKEN), push.recipients.map { it.token }.toSet())
    }

    @Test
    fun `tells the host somebody walked out`() {
        val host = signIn("ada")
        val friend = signIn("bola")
        val hangout = createHangout(host)
        invite(host, hangout, friend)
        accept(friend, hangout)
        broker.deliverEvents()
        notificationRepository.deleteAll()

        mockMvc.delete("/api/hangouts/${hangout.id}/leave") {
            authenticatedAs(friend)
        }.andExpect { status { isNoContent() } }
        broker.deliverEvents()

        val notification = notificationRepository.findAll().single()
        assertEquals(host.userId, notification.userId)
        assertEquals(NotificationType.PARTICIPANT_LEFT, notification.type)
    }

    /**
     * A cancellation is the one thing somebody must not miss, so it is the only fan-out here that
     * also sends an email.
     */
    @Test
    fun `tells everybody a hangout was called off, by push and by email`() {
        val host = signIn("ada")
        val friend = signIn("bola")
        val hangout = createHangout(host)
        invite(host, hangout, friend)
        accept(friend, hangout)
        broker.deliverEvents()
        notificationRepository.deleteAll()

        mockMvc.delete("/api/hangouts/${hangout.id}") {
            authenticatedAs(host)
        }.andExpect { status { isNoContent() } }
        broker.deliverEvents()

        val notification = notificationRepository.findAll().single()
        assertEquals(friend.userId, notification.userId, "the host does not need telling they cancelled")
        assertEquals(NotificationType.HANGOUT_CANCELLED, notification.type)

        val cancellation = sentEmails().single { it.subject == "\"${hangout.name}\" has been cancelled" }
        assertEquals("bola@lynk.test", cancellation.to)
    }

    @Test
    fun `sends nothing to somebody with no device registered`() {
        val host = signIn("ada")
        val friend = signIn("bola")
        val hangout = createHangout(host)

        invite(host, hangout, friend)
        broker.deliverEvents()

        // The notification is still there to be pulled down; only the push has nowhere to go.
        assertEquals(1, notificationRepository.count())
        then(firebasePushNotificationClient).should(never())
            .sendNotification(any())
    }

    /**
     * Firebase separates "this device is gone" from "try again later". A token that is gone is
     * dropped, so the next push does not waste a call on it.
     */
    @Test
    fun `forgets a device Firebase says no longer exists`() {
        val host = signIn("ada")
        val friend = signIn("bola")
        registerDevice(friend)
        val hangout = createHangout(host)
        given(firebasePushNotificationClient.sendNotification(any()))
            .willReturn(
                PushNotificationSendResult(
                    succeeded = emptyList(),
                    temporaryFailures = emptyList(),
                    permanentFailures = listOf(deviceToken(friend))
                )
            )

        invite(host, hangout, friend)
        broker.deliverEvents()

        assertEquals(0, deviceTokenRepository.count(), "a dead token was kept")
    }

    /** A push that failed for a reason that might pass is tried again by the retry sweep. */
    @Test
    fun `tries a failed push again later`() {
        val friend = signIn("bola")
        val token = DeviceToken(userId = friend.userId, token = DEVICE_TOKEN, platform = Platform.ANDROID)
        given(firebasePushNotificationClient.sendNotification(any()))
            .willReturn(
                PushNotificationSendResult(
                    succeeded = emptyList(),
                    temporaryFailures = listOf(token),
                    permanentFailures = emptyList()
                )
            )

        pushNotificationService.sendWithRetry(
            PushNotification(
                title = "Sunday jollof",
                message = "Ada invited you to this hangout",
                recipients = listOf(token),
                hangoutId = UUID.randomUUID(),
                data = emptyMap()
            )
        )
        then(firebasePushNotificationClient).should()
            .sendNotification(any())

        // The first retry is thirty seconds out, so nothing is due yet.
        pushNotificationService.processRetries()
        then(firebasePushNotificationClient).should()
            .sendNotification(any())
    }

    private fun capturePush(): PushNotification {
        val captor = argumentCaptor<PushNotification>()
        then(firebasePushNotificationClient).should().sendNotification(captor.capture())

        return captor.lastValue
    }

    /**
     * Every email sent so far, not only the one a test is about: signing an account up already sends
     * two of its own, so a test looks for its message among them rather than expecting the only one.
     */
    private fun sentEmails(): List<SentEmail> {
        val toCaptor = argumentCaptor<String>()
        val subjectCaptor = argumentCaptor<String>()
        val htmlCaptor = argumentCaptor<String>()
        then(brevoEmailClient).should(atLeastOnce())
            .sendHtmlEmail(toCaptor.capture(), subjectCaptor.capture(), htmlCaptor.capture())

        return toCaptor.allValues.indices.map { i ->
            SentEmail(
                to = toCaptor.allValues[i],
                subject = subjectCaptor.allValues[i],
                body = htmlCaptor.allValues[i]
            )
        }
    }

    private data class SentEmail(val to: String, val subject: String, val body: String)

    private fun createHangout(host: TestAccount): HangoutDto {
        val response = mockMvc.post("/api/hangouts") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(host)
            content = """
                {
                  "name": "Sunday jollof",
                  "description": null,
                  "vibe": "FOOD",
                  "scheduledAt": "${Instant.now().plus(Duration.ofDays(3))}",
                  "maxAttendees": null,
                  "spotId": null
                }
            """.trimIndent()
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString

        return objectMapper.readValue(response, HangoutDto::class.java)
    }

    private fun invite(host: TestAccount, hangout: HangoutDto, invitee: TestAccount) {
        mockMvc.post("/api/hangouts/${hangout.id}/participants") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(host)
            content = """{"userId":"${invitee.userId}"}"""
        }.andExpect { status { isCreated() } }
    }

    private fun accept(account: TestAccount, hangout: HangoutDto) {
        mockMvc.patch("/api/hangouts/${hangout.id}/rsvp") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(account)
            content = """{"rsvpStatus":"${RsvpStatus.ATTENDING.name}"}"""
        }.andExpect { status { isOk() } }
    }

    private fun registerDevice(account: TestAccount) {
        mockMvc.post("/api/notifications/register") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(account)
            content = """{"token":"$DEVICE_TOKEN","platform":"ANDROID"}"""
        }.andExpect { status { isOk() } }
    }

    private fun deviceToken(account: TestAccount) = DeviceToken(
        userId = account.userId,
        token = DEVICE_TOKEN,
        platform = Platform.ANDROID
    )

    private fun signIn(username: String): TestAccount = accounts.signIn(
        email = "$username@lynk.test",
        displayName = username.replaceFirstChar { it.uppercase() },
        username = username
    )
}
