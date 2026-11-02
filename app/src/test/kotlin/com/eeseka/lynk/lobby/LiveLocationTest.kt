package com.eeseka.lynk.lobby

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.lobby.api.dto.ws.outbound.LiveLocationDto
import com.eeseka.lynk.lobby.service.LobbyLiveLocationService
import com.eeseka.lynk.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Who can be on a hangout's live map, for how long, and that it never outlives the hangout.
 */
class LiveLocationTest : IntegrationTest() {

    @Autowired
    private lateinit var hangoutService: HangoutService

    @Autowired
    private lateinit var liveLocationService: LobbyLiveLocationService

    private val hangoutId: HangoutId = UUID.randomUUID()
    private val userId: UserId = UUID.randomUUID()

    @Test
    fun `opens an hour before a scheduled hangout and not earlier`() {
        val host = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")
        val hangoutId = hangouts.scheduled(host).id

        fixtures.moveScheduledAt(hangoutId, Instant.now().plus(Duration.ofMinutes(61)))
        assertFalse(hangoutService.isLiveLocationOpen(hangoutId))

        fixtures.moveScheduledAt(hangoutId, Instant.now().plus(Duration.ofMinutes(59)))
        assertTrue(hangoutService.isLiveLocationOpen(hangoutId))
    }

    @Test
    fun `stays shut for a hangout that does not exist`() {
        assertFalse(hangoutService.isLiveLocationOpen(UUID.randomUUID()))
    }

    @Test
    fun `keeps only the latest position for each person`() {
        liveLocationService.share(hangoutId, userId, 6.5, 3.3)
        liveLocationService.share(hangoutId, userId, 6.6, 3.4)

        val liveLocations = liveLocationService.getLiveLocations(hangoutId)

        assertEquals(1, liveLocations.size)
        assertEquals(6.6, liveLocations.single().latitude)
    }

    @Test
    fun `stopping takes a person off and says whether they were on`() {
        liveLocationService.share(hangoutId, userId, 6.5, 3.3)

        assertTrue(liveLocationService.stop(hangoutId, userId))
        assertFalse(liveLocationService.stop(hangoutId, userId))
        assertTrue(liveLocationService.getLiveLocations(hangoutId).isEmpty())
    }

    @Test
    fun `clearing a hangout takes everyone off`() {
        liveLocationService.share(hangoutId, userId, 6.5, 3.3)

        assertTrue(liveLocationService.clear(hangoutId))
        assertFalse(liveLocationService.clear(hangoutId))
        assertTrue(liveLocationService.getLiveLocations(hangoutId).isEmpty())
    }

    @Test
    fun `removes only those silent since the cutoff`() {
        val quietUser: UserId = UUID.randomUUID()
        liveLocationService.share(hangoutId, quietUser, 6.5, 3.3)
        val cutoff = Instant.now().plusMillis(1)
        Thread.sleep(2)
        liveLocationService.share(hangoutId, userId, 6.6, 3.4)

        val removed = liveLocationService.removeSharedBefore(cutoff)

        assertEquals(mapOf(hangoutId to listOf(quietUser)), removed)
        assertEquals(listOf(userId), liveLocationService.getLiveLocations(hangoutId).map { it.userId })
    }

    @Test
    fun `sends the time a position was shared as a date the client can read`() {
        val json = objectMapper.writeValueAsString(
            LiveLocationDto(
                hangoutId = hangoutId,
                userId = userId,
                latitude = 6.5,
                longitude = 3.3,
                sharedAt = Instant.parse("2026-11-01T18:00:00Z")
            )
        )

        assertTrue(json.contains("\"sharedAt\":\"2026-11-01T18:00:00Z\""), json)
    }
}
