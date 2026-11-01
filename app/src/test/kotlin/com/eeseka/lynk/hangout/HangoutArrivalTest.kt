package com.eeseka.lynk.hangout

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutParticipantRepository
import com.eeseka.lynk.hangout.service.HangoutParticipantService
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.notification.domain.model.NotificationType
import com.eeseka.lynk.notification.infra.database.repositories.NotificationRepository
import com.eeseka.lynk.notification.service.PushNotificationService
import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.TestAccount
import com.eeseka.lynk.support.authenticatedAs
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The hour before a hangout and the hangout itself: everyone going hears it starts soon, and saying
 * "I'm here" tells whoever is still on the way, once.
 */
class HangoutArrivalTest : IntegrationTest() {

    @Autowired
    private lateinit var hangoutService: HangoutService

    @Autowired
    private lateinit var hangoutParticipantRepository: HangoutParticipantRepository

    @Autowired
    private lateinit var hangoutParticipantService: HangoutParticipantService

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @MockitoSpyBean
    private lateinit var pushNotificationService: PushNotificationService

    @Test
    fun `tells everyone going once when a hangout planned ahead starts within the hour`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = groupHangout(host, bola, startsIn = Duration.ofMinutes(50))

        hangoutService.remindAttendeesOfStartingHangouts()
        hangoutService.remindAttendeesOfStartingHangouts()
        broker.deliverEvents()

        val notified = notificationRepository.findAll().filter { it.type == NotificationType.HANGOUT_STARTING_SOON }
        assertEquals(setOf(host.userId, bola.userId), notified.map { it.userId }.toSet())
        assertEquals(2, notified.size)
        assertEquals(hangoutId, notified.first().hangoutId)
    }

    @Test
    fun `leaves alone a hangout moved too close for about an hour to be true`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        groupHangout(host, bola, startsIn = Duration.ofMinutes(20))

        hangoutService.remindAttendeesOfStartingHangouts()
        broker.deliverEvents()

        assertEquals(0, notificationRepository.findAll().count { it.type == NotificationType.HANGOUT_STARTING_SOON })
    }

    @Test
    fun `leaves alone a hangout made inside the hour`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        groupHangout(host, bola, startsIn = Duration.ofMinutes(50), plannedAhead = false)

        hangoutService.remindAttendeesOfStartingHangouts()
        broker.deliverEvents()

        assertEquals(0, notificationRepository.findAll().count { it.type == NotificationType.HANGOUT_STARTING_SOON })
    }

    @Test
    fun `leaves alone a hangout only its host is going to`() {
        val host = signIn("ada")
        val hangoutId = hangouts.scheduled(host).id
        fixtures.moveScheduledAt(hangoutId, Instant.now().plus(Duration.ofMinutes(50)))
        planAhead(hangoutId)

        hangoutService.remindAttendeesOfStartingHangouts()
        broker.deliverEvents()

        assertEquals(0, notificationRepository.findAll().count { it.type == NotificationType.HANGOUT_STARTING_SOON })
    }

    @Test
    fun `saying I am here tells the others once`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = groupHangout(host, bola, startsIn = Duration.ofMinutes(30))

        arrive(bola, hangoutId).andExpect { status { isNoContent() } }
        arrive(bola, hangoutId).andExpect { status { isNoContent() } }
        broker.deliverEvents()

        verify(pushNotificationService, times(1)).sendToUsers(
            eq(setOf(host.userId)),
            any(),
            eq("Bola is here. Start heading over"),
            eq(hangoutId),
            eq(NotificationType.PARTICIPANT_ARRIVED)
        )
        val arrivals = notificationRepository.findAll().filter { it.type == NotificationType.PARTICIPANT_ARRIVED }
        assertEquals(listOf(host.userId), arrivals.map { it.userId })
        assertNotNull(arrivedAt(hangoutId, bola))
    }

    @Test
    fun `does not tell someone who is already there`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val cara = signIn("cara")
        val hangoutId = groupHangout(host, bola, startsIn = Duration.ofMinutes(30))
        hangouts.invite(host, hangoutId, cara)
        hangouts.accept(cara, hangoutId)

        arrive(bola, hangoutId).andExpect { status { isNoContent() } }
        arrive(cara, hangoutId).andExpect { status { isNoContent() } }
        broker.deliverEvents()

        verify(pushNotificationService).sendToUsers(
            eq(setOf(host.userId)),
            any(),
            eq("Cara is here. Start heading over"),
            eq(hangoutId),
            eq(NotificationType.PARTICIPANT_ARRIVED)
        )
    }

    @Test
    fun `cannot say I am here before the hour opens`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = groupHangout(host, bola, startsIn = Duration.ofHours(3))

        arrive(bola, hangoutId).andExpect { status { isConflict() } }
        broker.deliverEvents()

        verify(pushNotificationService, never()).sendToUsers(any(), any(), any(), any(), eq(NotificationType.PARTICIPANT_ARRIVED))
        assertNull(arrivedAt(hangoutId, bola))
    }

    @Test
    fun `can say I am here once the hangout is under way`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = groupHangout(host, bola, startsIn = Duration.ofMinutes(30))
        fixtures.moveScheduledAt(hangoutId, Instant.now().minus(Duration.ofHours(1)))
        hangoutService.transitionDueHangoutsToOngoing()

        arrive(bola, hangoutId).andExpect { status { isNoContent() } }

        assertNotNull(arrivedAt(hangoutId, bola))
    }

    @Test
    fun `leaving forgets the arrival`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = groupHangout(host, bola, startsIn = Duration.ofMinutes(30))
        arrive(bola, hangoutId).andExpect { status { isNoContent() } }

        hangoutParticipantService.leaveHangout(bola.userId, hangoutId)

        assertNull(arrivedAt(hangoutId, bola))
    }

    private fun groupHangout(
        host: TestAccount,
        attendee: TestAccount,
        startsIn: Duration,
        plannedAhead: Boolean = true
    ): HangoutId {
        val hangoutId = hangouts.scheduled(host).id
        hangouts.invite(host, hangoutId, attendee)
        hangouts.accept(attendee, hangoutId)
        fixtures.moveScheduledAt(hangoutId, Instant.now().plus(startsIn))
        if (plannedAhead) planAhead(hangoutId)
        return hangoutId
    }

    private fun planAhead(hangoutId: HangoutId) {
        fixtures.ageHangout(hangoutId, Instant.now().minus(Duration.ofDays(2)))
    }

    private fun arrive(account: TestAccount, hangoutId: HangoutId) =
        mockMvc.post("/api/hangouts/$hangoutId/arrival") { authenticatedAs(account) }

    private fun arrivedAt(hangoutId: HangoutId, account: TestAccount): Instant? =
        hangoutParticipantRepository.findByHangoutIdAndHangoutUserUserId(hangoutId, account.userId)?.arrivedAt

    private fun signIn(name: String): TestAccount =
        accounts.signIn(email = "$name@lynk.test", displayName = name.replaceFirstChar { it.uppercase() }, username = name)
}
