package com.eeseka.lynk.lobby.service

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.HangoutConstants.MAX_VOTING_CANDIDATES
import com.eeseka.lynk.lobby.domain.model.Coordinates
import com.eeseka.lynk.lobby.domain.model.ProposeOutcome
import com.eeseka.lynk.lobby.domain.model.RemoveOutcome
import com.eeseka.lynk.lobby.domain.model.VotingCandidate
import com.eeseka.lynk.lobby.domain.model.VotingSnapshot
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.round

// Voting lives in memory only: the ballot, the votes and the shared locations per hangout.
@Service
class LobbyVotingService {

    companion object {
        private const val MIN_LOCATIONS_FOR_CENTER = 3
        private const val CENTER_PRECISION = 200.0
    }

    private val votingLock = ReentrantReadWriteLock()

    private val hangoutCandidates = ConcurrentHashMap<HangoutId, LinkedHashMap<String, VotingCandidate>>()
    private val hangoutVotes = ConcurrentHashMap<HangoutId, MutableMap<UserId, String>>()
    private val hangoutLocations = ConcurrentHashMap<HangoutId, MutableMap<UserId, Coordinates>>()

    fun proposeSpot(hangoutId: HangoutId, candidate: VotingCandidate): ProposeOutcome {
        return votingLock.write {
            val candidates = hangoutCandidates.getOrPut(hangoutId) { LinkedHashMap() }
            when {
                candidates.containsKey(candidate.spot.id) -> ProposeOutcome.DUPLICATE
                candidates.size >= MAX_VOTING_CANDIDATES -> ProposeOutcome.FULL
                else -> {
                    candidates[candidate.spot.id] = candidate
                    ProposeOutcome.ADDED
                }
            }
        }
    }

    fun removeCandidate(hangoutId: HangoutId, spotId: String, userId: UserId, isHost: Boolean): RemoveOutcome {
        return votingLock.write {
            val candidates = hangoutCandidates[hangoutId]
            val candidate = candidates?.get(spotId) ?: return@write RemoveOutcome.NotACandidate
            if (!isHost && candidate.suggestedById != userId) return@write RemoveOutcome.NotAllowed

            candidates.remove(spotId)
            val votes = hangoutVotes[hangoutId]
            votes?.values?.removeAll { it == spotId }
            RemoveOutcome.Removed(votes = votes?.let { HashMap(it) } ?: emptyMap())
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

    fun shareLocation(hangoutId: HangoutId, userId: UserId, latitude: Double, longitude: Double): Boolean {
        return votingLock.write {
            val previousCenter = computeCenter(hangoutId)
            hangoutLocations.getOrPut(hangoutId) { mutableMapOf() }[userId] = Coordinates(latitude, longitude)
            computeCenter(hangoutId) != previousCenter
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
            val previousCenter = computeCenter(hangoutId)
            hangoutLocations[hangoutId]?.remove(userId)
            computeCenter(hangoutId) != previousCenter
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

    // Rounded average of every shared location, or null until enough people shared. Callers hold votingLock.
    private fun computeCenter(hangoutId: HangoutId): Coordinates? {
        val coordinates = hangoutLocations[hangoutId]?.values ?: return null
        if (coordinates.size < MIN_LOCATIONS_FOR_CENTER) return null

        return Coordinates(
            latitude = round(coordinates.map { it.latitude }.average() * CENTER_PRECISION) / CENTER_PRECISION,
            longitude = round(coordinates.map { it.longitude }.average() * CENTER_PRECISION) / CENTER_PRECISION
        )
    }
}
