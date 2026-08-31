package com.eeseka.lynk.user

import com.eeseka.lynk.notification.domain.model.NotificationType
import com.eeseka.lynk.notification.infra.database.repositories.NotificationRepository
import com.eeseka.lynk.notification.service.NotificationService
import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.TestAccount
import com.eeseka.lynk.support.authenticatedAs
import com.eeseka.lynk.user.infra.database.repositories.RefreshTokenRepository
import com.eeseka.lynk.user.infra.database.repositories.UserRepository
import com.eeseka.lynk.user.service.AuthService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.patch
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The three nightly sweeps that only throw things away: guest accounts nobody came back to, refresh
 * tokens that have expired, and notifications somebody read months ago.
 *
 * None can be reached over HTTP. Each one is asked to delete something it should and to leave three
 * things it should not, because a cleanup with a wrong predicate deletes real accounts.
 */
class CleanupSweepTest : IntegrationTest() {

    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var notificationService: NotificationService

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Test
    fun `clears out a guest account nobody came back to`() {
        val staleGuest = accounts.signInAsGuest()
        fixtures.ageUser(staleGuest.userId, Instant.now().minus(Duration.ofDays(31)))

        authService.cleanupStaleGuestAccounts()

        assertTrue(userRepository.findById(staleGuest.userId).isEmpty)
    }

    @Test
    fun `keeps a guest who is still using the app`() {
        val guest = accounts.signInAsGuest()
        fixtures.ageUser(guest.userId, Instant.now().minus(Duration.ofDays(3)))

        authService.cleanupStaleGuestAccounts()

        assertTrue(userRepository.findById(guest.userId).isPresent)
    }

    /**
     * The sweep is only ever meant to clear guests. An account somebody signed into with Google is
     * theirs, however long they leave it.
     */
    @Test
    fun `never touches a real account, however old`() {
        val account = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")
        fixtures.ageUser(account.userId, Instant.now().minus(Duration.ofDays(400)))

        authService.cleanupStaleGuestAccounts()

        assertTrue(userRepository.findById(account.userId).isPresent)
    }

    @Test
    fun `throws away a refresh token that has expired`() {
        val account = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")
        fixtures.expireRefreshTokens(account.userId, Instant.now().minus(Duration.ofDays(1)))

        authService.cleanupExpiredRefreshTokens()

        assertEquals(0, refreshTokenRepository.count())
    }

    @Test
    fun `keeps a refresh token somebody is still signed in with`() {
        accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")

        authService.cleanupExpiredRefreshTokens()

        assertEquals(1, refreshTokenRepository.count())
    }

    @Test
    fun `clears a notification that was read long ago`() {
        val account = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")
        notify(account)
        markEverythingRead(account)
        fixtures.ageNotifications(account.userId, Instant.now().minus(Duration.ofDays(91)))

        notificationService.deleteOldReadNotifications()

        assertEquals(0, notificationRepository.count())
    }

    /** Unread is unread, however long it has been sitting there. */
    @Test
    fun `keeps an old notification nobody read`() {
        val account = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")
        notify(account)
        fixtures.ageNotifications(account.userId, Instant.now().minus(Duration.ofDays(400)))

        notificationService.deleteOldReadNotifications()

        assertEquals(1, notificationRepository.count())
    }

    @Test
    fun `keeps a notification read this week`() {
        val account = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")
        notify(account)
        markEverythingRead(account)

        notificationService.deleteOldReadNotifications()

        assertEquals(1, notificationRepository.count())
    }

    private fun notify(account: TestAccount) {
        notificationService.createNotifications(
            userIds = setOf(account.userId),
            type = NotificationType.PARTICIPANT_INVITED,
            hangoutId = UUID.randomUUID(),
            hangoutName = "Sunday jollof",
            actorDisplayName = "Bola"
        )
    }

    private fun markEverythingRead(account: TestAccount) {
        mockMvc.patch("/api/notifications/read-all") {
            authenticatedAs(account)
        }.andExpect { status { isNoContent() } }
    }
}
