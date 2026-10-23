package com.eeseka.lynk.assistant.infra.messaging

import com.eeseka.lynk.assistant.service.AssistantMessageService
import com.eeseka.lynk.common.domain.events.spot.SpotEvent
import com.eeseka.lynk.common.infra.message_queue.MessageQueues
import com.eeseka.lynk.spot.domain.model.PriceLevel
import com.eeseka.lynk.spot.domain.model.SpotCategory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class AssistantSpotEventListener(
    private val assistantMessageService: AssistantMessageService
) {
    @RabbitListener(queues = [MessageQueues.ASSISTANT_SPOT_EVENTS])
    fun handleSpotEvent(event: SpotEvent) {
        when (event) {
            is SpotEvent.DetailsFetched -> {
                assistantMessageService.refreshSpotSnapshots(
                    spotId = event.spotId,
                    name = event.name,
                    coverPhotoUrl = event.coverPhotoUrl,
                    category = SpotCategory.valueOf(event.category),
                    priceLevel = event.priceLevel?.let { PriceLevel.valueOf(it) },
                    shortAddress = event.shortAddress,
                    latitude = event.latitude,
                    longitude = event.longitude
                )
            }
        }
    }
}
