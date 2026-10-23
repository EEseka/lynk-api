package com.eeseka.lynk.assistant.service.agent

import com.eeseka.lynk.assistant.domain.model.AssistantRecommendation
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.spot.domain.model.Spot
import java.time.Instant

// What one answer may draw on, and what it has done so far. Lives for a single send
class AssistantTurn(
    val userId: UserId,
    val searchLatitude: Double?, // The group center in a hangout, the searched area in Discover
    val searchLongitude: Double?,
    val hangoutTime: Instant?,
    val ballotSpotIds: List<String>,
    val earlierRecommendedSpotIds: List<String>
) {
    val seenSpots = mutableMapOf<String, Spot>() // Only places this answer has seen can become cards, so a made-up id never reaches the phone
    val recommendations = mutableListOf<AssistantRecommendation>()
    var searchCount = 0
}
