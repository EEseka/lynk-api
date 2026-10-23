package com.eeseka.lynk.lobby.service

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.HangoutConstants.MAX_VOTING_CANDIDATES
import com.eeseka.lynk.lobby.domain.model.Coordinates
import com.eeseka.lynk.lobby.domain.model.ProposeOutcome
import com.eeseka.lynk.lobby.domain.model.VotingSnapshot
import com.eeseka.lynk.spot.domain.model.Spot
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

// Voting lives in memory only: the ballot, the votes and the shared locations per hangout.
@Service
class LobbyVotingService {

    private val votingLock = ReentrantReadWriteLock()

    private val hangoutCandidates = ConcurrentHashMap<HangoutId, LinkedHashMap<String, Spot>>()
    private val hangoutVotes = ConcurrentHashMap<HangoutId, MutableMap<UserId, String>>()
    private val hangoutLocations = ConcurrentHashMap<HangoutId, MutableMap<UserId, Coordinates>>()

    fun proposeSpot(hangoutId: HangoutId, spotId: String, spot: Spot): ProposeOutcome {
        return votingLock.write {
            val candidates = hangoutCandidates.getOrPut(hangoutId) { LinkedHashMap() }
            when {
                candidates.containsKey(spotId) -> ProposeOutcome.DUPLICATE
                candidates.size >= MAX_VOTING_CANDIDATES -> ProposeOutcome.FULL
                else -> {
                    candidates[spotId] = spot
                    ProposeOutcome.ADDED
                }
            }
        }
    }

    // Purges any votes for the spot so a removed spot can never win at close time.
    // Returns the votes left or null when the spot isn't on the ballot.
    fun removeCandidate(hangoutId: HangoutId, spotId: String): Map<UserId, String>? {
        return votingLock.write {
            if (hangoutCandidates[hangoutId]?.remove(spotId) == null) return@write null
            val votes = hangoutVotes[hangoutId]
            votes?.values?.removeAll { it == spotId }
            votes?.let { HashMap(it) } ?: emptyMap()
        }
    }

    // Overwrites any previous vote. Returns the whole tally or null when the spot isn't a candidate.
    fun castVote(hangoutId: HangoutId, userId: UserId, spotId: String): Map<UserId, String>? {
        return votingLock.write {
            if (hangoutCandidates[hangoutId]?.containsKey(spotId) != true) return@write null
            val votes = hangoutVotes.getOrPut(hangoutId) { mutableMapOf() }
            votes[userId] = spotId
            HashMap(votes)
        }
    }

    // The spotId(s) with the most votes, empty when nobody has voted.
    fun findLeaders(hangoutId: HangoutId): Set<String> {
        return votingLock.read {
            val picks = hangoutVotes[hangoutId]?.values ?: emptyList()
            if (picks.isEmpty()) {
                emptySet()
            } else {
                val counts = picks.groupingBy { it }.eachCount()
                val topCount = counts.values.max()
                counts.filterValues { it == topCount }.keys.toSet()
            }
        }
    }

    fun shareLocation(hangoutId: HangoutId, userId: UserId, latitude: Double, longitude: Double) {
        votingLock.write {
            hangoutLocations.getOrPut(hangoutId) { mutableMapOf() }[userId] = Coordinates(latitude, longitude)
        }
    }

    // Returns the votes left, or null when this user had not voted.
    fun removeVote(hangoutId: HangoutId, userId: UserId): Map<UserId, String>? {
        return votingLock.write {
            val votes = hangoutVotes[hangoutId] ?: return@write null
            if (votes.remove(userId) == null) return@write null
            HashMap(votes)
        }
    }

    fun removeLocation(hangoutId: HangoutId, userId: UserId): Boolean {
        return votingLock.write {
            hangoutLocations[hangoutId]?.remove(userId) != null
        }
    }

    fun clearVotingState(hangoutId: HangoutId) {
        votingLock.write {
            hangoutCandidates.remove(hangoutId)
            hangoutVotes.remove(hangoutId)
            hangoutLocations.remove(hangoutId)
        }
    }

    // Ballot, votes and center from one read, so they always agree with each other.
    fun getVotingSnapshot(hangoutId: HangoutId): VotingSnapshot {
        return votingLock.read {
            val center = computeCenter(hangoutId)
            VotingSnapshot(
                hangoutId = hangoutId,
                candidates = hangoutCandidates[hangoutId]?.values?.toList() ?: emptyList(),
                votes = hangoutVotes[hangoutId]?.let { HashMap(it) } ?: emptyMap(),
                centerLatitude = center?.latitude,
                centerLongitude = center?.longitude
            )
        }
    }

    fun getCenter(hangoutId: HangoutId): Coordinates? {
        return votingLock.read { computeCenter(hangoutId) }
    }

    // Average of every shared location for this hangout, or null if none shared yet. Callers hold votingLock.
    private fun computeCenter(hangoutId: HangoutId): Coordinates? {
        val coordinates = hangoutLocations[hangoutId]?.values ?: return null
        if (coordinates.isEmpty()) return null

        return Coordinates(
            latitude = coordinates.map { it.latitude }.average(),
            longitude = coordinates.map { it.longitude }.average()
        )
    }
}
