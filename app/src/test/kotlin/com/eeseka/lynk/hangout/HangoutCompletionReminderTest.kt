package com.eeseka.lynk.hangout

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutRepository
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.notification.domain.model.NotificationType
import com.eeseka.lynk.notification.infra.database.repositories.NotificationRepository
import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.TestAccount
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The pushes a host gets at 1, 3, and 7 days while a hangout is still going, since only the host can
 * finish it and the album stays locked until they do.
 */
class HangoutCompletionReminderTest : IntegrationTest() {

    @Autowired
    private lateinit var hangoutService: HangoutService

    @Autowired
    private lateinit var hangoutRepository: HangoutRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Test
    fun `reminds the host once when a hangout is still going a day later`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = ongoing(host, startedAgo = Duration.ofHours(30), attendees = listOf(bola))

        hangoutService.remindHostsToCompleteHangouts()
        hangoutService.remindHostsToCompleteHangouts()
        broker.deliverEvents()

        val reminders = notificationRepository.findAll().filter { it.type == NotificationType.HANGOUT_COMPLETION_REMINDER }
        assertEquals(1, reminders.size)
        assertEquals(host.userId, reminders.single().userId)
        assertEquals(hangoutId, reminders.single().hangoutId)
    }

    @Test
    fun `reminds the host again at three days and a week, then stops`() {
        val host = signIn("ada")
        val hangoutId = ongoing(host, startedAgo = Duration.ofHours(30))
        hangoutService.remindHostsToCompleteHangouts()

        fixtures.moveScheduledAt(hangoutId, Instant.now().minus(Duration.ofDays(4)))
        hangoutService.remindHostsToCompleteHangouts()
        fixtures.moveScheduledAt(hangoutId, Instant.now().minus(Duration.ofDays(8)))
        hangoutService.remindHostsToCompleteHangouts()
        fixtures.moveScheduledAt(hangoutId, Instant.now().minus(Duration.ofDays(30)))
        hangoutService.remindHostsToCompleteHangouts()
        broker.deliverEvents()

        assertEquals(3, remindersSent())
    }

    @Test
    fun `waits a full day before reminding anybody`() {
        val host = signIn("ada")
        ongoing(host, startedAgo = Duration.ofHours(3))

        hangoutService.remindHostsToCompleteHangouts()
        broker.deliverEvents()

        assertEquals(0, remindersSent())
    }

    @Test
    fun `leaves a hangout the host already finished alone`() {
        val host = signIn("ada")
        val hangoutId = hangouts.completed(host)
        fixtures.moveScheduledAt(hangoutId, Instant.now().minus(Duration.ofDays(2)))

        hangoutService.remindHostsToCompleteHangouts()
        broker.deliverEvents()

        assertEquals(0, remindersSent())
    }

    @Test
    fun `will not let a save from before the reminder undo it`() {
        val host = signIn("ada")
        val hangoutId = ongoing(host, startedAgo = Duration.ofHours(30))
        val loadedBeforeReminder = hangoutRepository.findByIdOrNull(hangoutId)!!

        hangoutService.remindHostsToCompleteHangouts()

        // Without the version bump this save would write the reminder count back to zero
        loadedBeforeReminder.name = "Renamed"
        assertFailsWith<ObjectOptimisticLockingFailureException> {
            hangoutRepository.save(loadedBeforeReminder)
        }
    }

    private fun remindersSent(): Int =
        notificationRepository.findAll().count { it.type == NotificationType.HANGOUT_COMPLETION_REMINDER }

    /** A hangout the real sweep has started, with its start moved back by [startedAgo]. */
    private fun ongoing(
        host: TestAccount,
        startedAgo: Duration,
        attendees: List<TestAccount> = emptyList()
    ): HangoutId {
        val hangoutId = hangouts.scheduled(host).id
        attendees.forEach {
            hangouts.invite(host, hangoutId, it)
            hangouts.accept(it, hangoutId)
        }
        fixtures.moveScheduledAt(hangoutId, Instant.now().minus(startedAgo))
        hangoutService.transitionDueHangoutsToOngoing()
        return hangoutId
    }

    private fun signIn(name: String): TestAccount =
        accounts.signIn(email = "$name@lynk.test", displayName = name.replaceFirstChar { it.uppercase() }, username = name)
}
