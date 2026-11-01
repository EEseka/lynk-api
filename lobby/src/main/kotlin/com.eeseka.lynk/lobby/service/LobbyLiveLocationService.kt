package com.eeseka.lynk.lobby.service

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.lobby.domain.model.LiveLocation
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

// Live locations live in memory only and are never written to the database.
@Service
class LobbyLiveLocationService {

    private val liveLocationLock = ReentrantReadWriteLock()

    private val hangoutLiveLocations = ConcurrentHashMap<HangoutId, MutableMap<UserId, LiveLocation>>()

    fun share(hangoutId: HangoutId, userId: UserId, latitude: Double, longitude: Double): LiveLocation {
        val liveLocation = LiveLocation(
            userId = userId,
            latitude = latitude,
            longitude = longitude,
            sharedAt = Instant.now()
        )
        liveLocationLock.write {
            hangoutLiveLocations.getOrPut(hangoutId) { mutableMapOf() }[userId] = liveLocation
        }
        return liveLocation
    }

    // Returns whether this user was sharing.
    fun stop(hangoutId: HangoutId, userId: UserId): Boolean {
        return liveLocationLock.write {
            val liveLocations = hangoutLiveLocations[hangoutId] ?: return@write false
            val wasSharing = liveLocations.remove(userId) != null
            if (liveLocations.isEmpty()) hangoutLiveLocations.remove(hangoutId)
            wasSharing
        }
    }

    // Returns whether anyone was sharing.
    fun clear(hangoutId: HangoutId): Boolean {
        return liveLocationLock.write {
            hangoutLiveLocations.remove(hangoutId) != null
        }
    }

    fun getLiveLocations(hangoutId: HangoutId): List<LiveLocation> {
        return liveLocationLock.read {
            hangoutLiveLocations[hangoutId]?.values?.toList() ?: emptyList()
        }
    }

    // Drops everyone last heard from before the cutoff and returns who went, per hangout.
    fun removeSharedBefore(cutoff: Instant): Map<HangoutId, List<UserId>> {
        return liveLocationLock.write {
            val removed = mutableMapOf<HangoutId, List<UserId>>()
            hangoutLiveLocations.entries.removeIf { (hangoutId, liveLocations) ->
                val expired = liveLocations.values.filter { it.sharedAt.isBefore(cutoff) }.map { it.userId }
                if (expired.isNotEmpty()) removed[hangoutId] = expired
                liveLocations.keys.removeAll(expired.toSet())
                liveLocations.isEmpty()
            }
            removed
        }
    }
}
