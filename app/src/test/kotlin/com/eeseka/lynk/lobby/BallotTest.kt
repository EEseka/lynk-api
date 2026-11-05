package com.eeseka.lynk.lobby

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.lobby.domain.model.ProposeOutcome
import com.eeseka.lynk.lobby.domain.model.RemoveOutcome
import com.eeseka.lynk.lobby.domain.model.VotingCandidate
import com.eeseka.lynk.lobby.service.LobbyVotingService
import com.eeseka.lynk.spot.domain.model.Spot
import com.eeseka.lynk.spot.domain.model.SpotCategory
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Who can take a spot off the ballot: the host any spot, everyone else only the ones they suggested.
 */
class BallotTest {

    private val votingService = LobbyVotingService()

    private val hangoutId: HangoutId = UUID.randomUUID()
    private val ada: UserId = UUID.randomUUID()
    private val bola: UserId = UUID.randomUUID()

    @Test
    fun `lets whoever suggested a spot take it back even with votes on it`() {
        suggest(spotId = "spot_1", by = bola)
        votingService.castVote(hangoutId, ada, "spot_1")

        val outcome = votingService.removeCandidate(hangoutId, "spot_1", userId = bola, isHost = false)

        assertEquals(RemoveOutcome.Removed(votes = emptyMap()), outcome)
        assertTrue(votingService.getVotingSnapshot(hangoutId).candidates.isEmpty())
    }

    @Test
    fun `will not let somebody remove a spot they did not suggest`() {
        suggest(spotId = "spot_1", by = bola)

        val outcome = votingService.removeCandidate(hangoutId, "spot_1", userId = ada, isHost = false)

        assertEquals(RemoveOutcome.NotAllowed, outcome)
        assertEquals(1, votingService.getVotingSnapshot(hangoutId).candidates.size)
    }

    @Test
    fun `lets the host remove any spot and keeps the other votes`() {
        suggest(spotId = "spot_1", by = bola)
        suggest(spotId = "spot_2", by = bola)
        votingService.castVote(hangoutId, ada, "spot_1")
        votingService.castVote(hangoutId, bola, "spot_2")

        val outcome = votingService.removeCandidate(hangoutId, "spot_1", userId = ada, isHost = true)

        assertEquals(RemoveOutcome.Removed(votes = mapOf(bola to "spot_2")), outcome)
    }

    private fun suggest(spotId: String, by: UserId) {
        val outcome = votingService.proposeSpot(
            hangoutId = hangoutId,
            candidate = VotingCandidate(
                spot = spot(spotId),
                suggestedById = by,
                suggestedByDisplayName = "Bola",
                note = null
            )
        )
        assertEquals(ProposeOutcome.ADDED, outcome)
    }

    private fun spot(id: String) = Spot(
        id = id,
        name = "The Lounge",
        typeLabel = null,
        description = null,
        generativeSummary = null,
        reviewSummary = null,
        photoUrls = emptyList(),
        category = SpotCategory.RESTAURANT,
        priceLevel = null,
        priceRange = null,
        rating = null,
        reviewCount = null,
        businessStatus = null,
        openingHours = null,
        amenities = null,
        parking = null,
        payment = null,
        shortAddress = null,
        latitude = 6.5,
        longitude = 3.3,
        phoneNumber = null,
        websiteUrl = null,
        googleMapsUrl = null,
        directionsUrl = null,
        isSaved = false
    )
}
