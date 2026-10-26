package com.eeseka.lynk.hangout

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.notification.domain.model.NotificationType
import com.eeseka.lynk.notification.infra.database.repositories.NotificationRepository
import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.TestAccount
import com.eeseka.lynk.support.authenticatedAs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

/**
 * A hangout only its host is going to. It is never canceled or held back, but every push meant for a
 * group stays quiet, and the host gets one nudge to invite whoever is coming before it starts.
 */
class HangoutSoloTest : IntegrationTest() {

    @Autowired
    private lateinit var hangoutService: HangoutService

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Test
    fun `reminds the host once to invite people when nobody has joined a day before`() {
        val host = signIn("ada")
        val hangoutId = soloPlannedAhead(host, spotId = "ChIJtestspot", startsIn = Duration.ofHours(10))

        hangoutService.remindHostsOfSoloHangouts()
        hangoutService.remindHostsOfSoloHangouts()
        broker.deliverEvents()

        val reminders = notifications(NotificationType.SOLO_REMINDER)
        assertEquals(1, reminders.size)
        assertEquals(host.userId, reminders.single().userId)
        assertEquals(hangoutId, reminders.single().hangoutId)
    }

    @Test
    fun `a solo hangout still voting gets the solo reminder instead of the spot one`() {
        val host = signIn("ada")
        soloPlannedAhead(host, spotId = null, startsIn = Duration.ofHours(10))

        hangoutService.remindHostsToPickSpots()
        hangoutService.remindHostsOfSoloHangouts()
        broker.deliverEvents()

        assertEquals(0, notifications(NotificationType.SPOT_REMINDER_BEFORE_START).size)
        assertEquals(1, notifications(NotificationType.SOLO_REMINDER).size)
    }

    @Test
    fun `leaves a hangout somebody joined alone`() {
        val host = signIn("ada")
        val hangoutId = soloPlannedAhead(host, spotId = "ChIJtestspot", startsIn = Duration.ofHours(10))
        val bola = signIn("bola")
        hangouts.invite(host, hangoutId, bola)
        hangouts.accept(bola, hangoutId)

        hangoutService.remindHostsOfSoloHangouts()
        broker.deliverEvents()

        assertEquals(0, notifications(NotificationType.SOLO_REMINDER).size)
    }

    @Test
    fun `waits until the hangout is less than a day away`() {
        val host = signIn("ada")
        soloPlannedAhead(host, spotId = "ChIJtestspot", startsIn = Duration.ofDays(2))

        hangoutService.remindHostsOfSoloHangouts()
        broker.deliverEvents()

        assertEquals(0, notifications(NotificationType.SOLO_REMINDER).size)
    }

    @Test
    fun `gives a new date its own solo reminder`() {
        val host = signIn("ada")
        val hangoutId = soloPlannedAhead(host, spotId = null, startsIn = Duration.ofHours(10))
        hangoutService.remindHostsOfSoloHangouts()

        moveDate(host, hangoutId, Instant.now().plus(Duration.ofDays(3)))
        fixtures.moveScheduledAt(hangoutId, Instant.now().plus(Duration.ofHours(10)))
        hangoutService.remindHostsOfSoloHangouts()
        broker.deliverEvents()

        assertEquals(2, notifications(NotificationType.SOLO_REMINDER).size)
    }

    @Test
    fun `tells a host going alone it has started and asks them to invite who came`() {
        val host = signIn("ada")
        val hangoutId = hangouts.scheduled(host).id
        fixtures.moveScheduledAt(hangoutId, Instant.now().minus(Duration.ofMinutes(5)))

        hangoutService.transitionDueHangoutsToOngoing()
        broker.deliverEvents()

        assertEquals(0, notifications(NotificationType.HANGOUT_STARTED).size)
        assertEquals(host.userId, notifications(NotificationType.SOLO_STARTED).single().userId)
    }

    @Test
    fun `counts only hangouts somebody else went to as hosted`() {
        val host = signIn("ada")
        hangouts.completed(host)
        hangouts.completed(host, attendees = listOf(signIn("bola")))

        mockMvc.get("/api/hangouts/stats/me") {
            authenticatedAs(host)
        }.andExpect {
            status { isOk() }
            jsonPath("$.hostedCount") { value(1) }
        }
    }

    @Test
    fun `clears the inbox of a solo hangout the nightly tidy up deletes`() {
        val host = signIn("ada")
        val hangoutId = soloPlannedAhead(host, spotId = "ChIJtestspot", startsIn = Duration.ofHours(10))
        hangoutService.remindHostsOfSoloHangouts()
        broker.deliverEvents()

        fixtures.moveScheduledAt(hangoutId, Instant.now().minus(Duration.ofDays(40)))
        hangoutService.cleanupSoloUnpaidHangouts()
        broker.deliverEvents()

        assertEquals(0, notificationRepository.findAll().count { it.hangoutId == hangoutId })
    }

    private fun notifications(type: NotificationType) = notificationRepository.findAll().filter { it.type == type }

    /** A hangout nobody but its host is going to, made days ago and starting in [startsIn]. */
    private fun soloPlannedAhead(host: TestAccount, spotId: String?, startsIn: Duration): HangoutId {
        val hangoutId = hangouts.scheduled(host, spotId = spotId).id
        fixtures.ageHangout(hangoutId, Instant.now().minus(Duration.ofDays(3)))
        fixtures.moveScheduledAt(hangoutId, Instant.now().plus(startsIn))
        return hangoutId
    }

    private fun moveDate(host: TestAccount, hangoutId: HangoutId, scheduledAt: Instant) {
        mockMvc.put("/api/hangouts/$hangoutId") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(host)
            content = """
                {
                  "name": "Sunday jollof",
                  "description": null,
                  "vibe": "FOOD",
                  "scheduledAt": "$scheduledAt",
                  "maxAttendees": null,
                  "spotId": null
                }
            """.trimIndent()
        }.andExpect { status { isOk() } }
    }

    private fun signIn(name: String): TestAccount =
        accounts.signIn(email = "$name@lynk.test", displayName = name.replaceFirstChar { it.uppercase() }, username = name)
}
