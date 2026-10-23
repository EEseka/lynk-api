package com.eeseka.lynk.common.domain.events.spot

import com.eeseka.lynk.common.domain.events.LynkEvent
import java.time.Instant
import java.util.*

sealed class SpotEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val exchange: String = SpotEventConstants.SPOT_EXCHANGE,
    override val occurredAt: Instant = Instant.now()
) : LynkEvent {

    data class DetailsFetched(
        val spotId: String,
        val name: String,
        val coverPhotoUrl: String?,
        // category and priceLevel are strings, not enums as common module can't see the spot module's enums
        val category: String,
        val priceLevel: String?,
        val shortAddress: String?,
        val latitude: Double,
        val longitude: Double,
        override val eventKey: String = SpotEventConstants.SPOT_DETAILS_FETCHED_KEY
    ) : SpotEvent(), LynkEvent
}
