package com.eeseka.lynk.lobby

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.lobby.domain.model.Coordinates
import com.eeseka.lynk.lobby.service.LobbyVotingService
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The group center never lets one person work out where another is standing.
 */
class GroupCenterTest {

    private val votingService = LobbyVotingService()

    private val hangoutId: HangoutId = UUID.randomUUID()
    private val ada: UserId = UUID.randomUUID()
    private val bola: UserId = UUID.randomUUID()
    private val chidi: UserId = UUID.randomUUID()

    @Test
    fun `shows no center until three people share where they are`() {
        votingService.shareLocation(hangoutId, ada, 6.5244, 3.3792)
        votingService.shareLocation(hangoutId, bola, 6.6018, 3.3515)

        assertNull(votingService.getCenter(hangoutId))
        assertNull(votingService.getVotingSnapshot(hangoutId).centerLatitude)

        val centerMoved = votingService.shareLocation(hangoutId, chidi, 6.4541, 3.3947)

        assertTrue(centerMoved)
        assertEquals(Coordinates(6.525, 3.375), votingService.getCenter(hangoutId))
    }

    @Test
    fun `keeps the center where it was when someone moves a few steps`() {
        votingService.shareLocation(hangoutId, ada, 6.5244, 3.3792)
        votingService.shareLocation(hangoutId, bola, 6.6018, 3.3515)
        votingService.shareLocation(hangoutId, chidi, 6.4541, 3.3947)

        val centerMoved = votingService.shareLocation(hangoutId, chidi, 6.4545, 3.3950)

        assertFalse(centerMoved)
        assertEquals(Coordinates(6.525, 3.375), votingService.getCenter(hangoutId))
    }

    @Test
    fun `takes the center away when someone leaves and fewer than three remain`() {
        votingService.shareLocation(hangoutId, ada, 6.5244, 3.3792)
        votingService.shareLocation(hangoutId, bola, 6.6018, 3.3515)
        votingService.shareLocation(hangoutId, chidi, 6.4541, 3.3947)

        val centerMoved = votingService.removeLocation(hangoutId, chidi)

        assertTrue(centerMoved)
        assertNull(votingService.getCenter(hangoutId))
    }
}
