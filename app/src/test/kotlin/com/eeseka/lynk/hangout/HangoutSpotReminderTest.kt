package com.eeseka.lynk.hangout

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutRepository
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.notification.domain.model.NotificationType
import com.eeseka.lynk.notification.infra.database.repositories.NotificationRepository
import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.TestAccount
import com.eeseka.lynk.support.authenticatedAs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.put
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

/**
 * The pushes a host gets while a hangout is still voting as its date comes: one when the start is less
 * than a day away and one once it has passed, since nothing picks the spot for them.
 */
class HangoutSpotReminderTest : IntegrationTest() {

    @Autowired
    private lateinit var hangoutService: HangoutService

    @Autowired
    private lateinit var hangoutRepository: HangoutRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Test
    fun `reminds only the host once when a hangout starts within a day with no spot`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = votingPlannedAhead(host, startsIn = Duration.ofHours(10))
        hangouts.invite(host, hangoutId, bola)
        hangouts.accept(bola, hangoutId)

        hangoutService.remindHostsToPickSpots()
        hangoutService.remindHostsToPickSpots()
        broker.deliverEvents()

        val reminders = reminders(NotificationType.SPOT_REMINDER_BEFORE_START)
        assertEquals(1, reminders.size)
        assertEquals(host.userId, reminders.single().userId)
        assertEquals(hangoutId, reminders.single().hangoutId)
    }

    @Test
    fun `reminds the host again once the start passes and then stops`() {
        val host = signIn("ada")
        val hangoutId = votingPlannedAhead(host, startsIn = Duration.ofHours(10))
        hangoutService.remindHostsToPickSpots()

        fixtures.moveScheduledAt(hangoutId, Instant.now().minus(Duration.ofHours(1)))
        hangoutService.remindHostsToPickSpots()
        fixtures.moveScheduledAt(hangoutId, Instant.now().minus(Duration.ofDays(3)))
        hangoutService.remindHostsToPickSpots()
        broker.deliverEvents()

        assertEquals(1, reminders(NotificationType.SPOT_REMINDER_BEFORE_START).size)
        assertEquals(1, reminders(NotificationType.SPOT_REMINDER_AFTER_START).size)
    }

    @Test
    fun `waits for the start of a hangout made for later the same day`() {
        val host = signIn("ada")
        val hangoutId = hangouts.scheduled(host, spotId = null).id
        joinedByAFriend(host, hangoutId)
        fixtures.moveScheduledAt(hangoutId, Instant.now().plus(Duration.ofHours(5)))

        hangoutService.remindHostsToPickSpots()
        fixtures.moveScheduledAt(hangoutId, Instant.now().minus(Duration.ofHours(1)))
        hangoutService.remindHostsToPickSpots()
        broker.deliverEvents()

        assertEquals(0, reminders(NotificationType.SPOT_REMINDER_BEFORE_START).size)
        assertEquals(1, reminders(NotificationType.SPOT_REMINDER_AFTER_START).size)
    }

    @Test
    fun `leaves a hangout that already has a spot alone`() {
        val host = signIn("ada")
        val hangoutId = hangouts.scheduled(host).id
        fixtures.ageHangout(hangoutId, Instant.now().minus(Duration.ofDays(3)))
        fixtures.moveScheduledAt(hangoutId, Instant.now().plus(Duration.ofHours(10)))

        hangoutService.remindHostsToPickSpots()
        broker.deliverEvents()

        assertEquals(0, notificationRepository.findAll().count { it.type == NotificationType.SPOT_REMINDER_BEFORE_START })
    }

    @Test
    fun `gives a new date its own reminders`() {
        val host = signIn("ada")
        val hangoutId = votingPlannedAhead(host, startsIn = Duration.ofHours(10))
        hangoutService.remindHostsToPickSpots()

        moveDate(host, hangoutId, Instant.now().plus(Duration.ofDays(3)))
        fixtures.moveScheduledAt(hangoutId, Instant.now().plus(Duration.ofHours(10)))
        hangoutService.remindHostsToPickSpots()
        broker.deliverEvents()

        assertEquals(2, reminders(NotificationType.SPOT_REMINDER_BEFORE_START).size)
    }

    @Test
    fun `a save from before the reminder goes through and does not undo it`() {
        val host = signIn("ada")
        val hangoutId = votingPlannedAhead(host, startsIn = Duration.ofHours(10))
        val loadedBeforeReminder = hangoutRepository.findByIdOrNull(hangoutId)!!

        hangoutService.remindHostsToPickSpots()
        loadedBeforeReminder.name = "Renamed"
        hangoutRepository.save(loadedBeforeReminder)
        hangoutService.remindHostsToPickSpots()
        broker.deliverEvents()

        assertEquals(1, reminders(NotificationType.SPOT_REMINDER_BEFORE_START).size)
    }

    private fun reminders(type: NotificationType) = notificationRepository.findAll().filter { it.type == type }

    /** A hangout still voting that a friend has joined, made days ago and starting in [startsIn]. */
    private fun votingPlannedAhead(host: TestAccount, startsIn: Duration): HangoutId {
        val hangoutId = hangouts.scheduled(host, spotId = null).id
        joinedByAFriend(host, hangoutId)
        fixtures.ageHangout(hangoutId, Instant.now().minus(Duration.ofDays(3)))
        fixtures.moveScheduledAt(hangoutId, Instant.now().plus(startsIn))
        return hangoutId
    }

    // A host going alone gets the solo reminder instead, so these hangouts have someone else going
    private fun joinedByAFriend(host: TestAccount, hangoutId: HangoutId) {
        val cleo = signIn("cleo")
        hangouts.invite(host, hangoutId, cleo)
        hangouts.accept(cleo, hangoutId)
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
