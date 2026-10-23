package com.eeseka.lynk.assistant.api.sse

import com.eeseka.lynk.assistant.api.dto.sse.AssistantErrorDto
import com.eeseka.lynk.assistant.api.dto.sse.AssistantSpotsDto
import com.eeseka.lynk.assistant.api.dto.sse.AssistantSseEventType
import com.eeseka.lynk.assistant.api.dto.sse.AssistantStartedDto
import com.eeseka.lynk.assistant.api.dto.sse.AssistantStatusDto
import com.eeseka.lynk.assistant.api.dto.sse.AssistantTextDto
import com.eeseka.lynk.assistant.api.mappers.toAssistantRecommendationDto
import com.eeseka.lynk.assistant.domain.model.AssistantStreamEvent
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Component
class AssistantStreamHandler {

    companion object {
        private const val STREAM_TIMEOUT_MS = 90_000L
        private const val PING_INTERVAL_MS = 15_000L
    }

    private val openStreams = ConcurrentHashMap.newKeySet<SseEmitter>()

    fun open(start: (onEvent: (AssistantStreamEvent) -> Unit, onFinished: () -> Unit) -> Unit): SseEmitter {
        val emitter = SseEmitter(STREAM_TIMEOUT_MS)
        openStreams.add(emitter)
        emitter.onCompletion { openStreams.remove(emitter) }
        emitter.onTimeout { openStreams.remove(emitter) }
        emitter.onError { openStreams.remove(emitter) }

        try {
            start(
                { event -> send(emitter, event.toSseEvent()) },
                { emitter.complete() }
            )
        } catch (e: Exception) {
            openStreams.remove(emitter)
            throw e
        }

        return emitter
    }

    @Scheduled(fixedDelay = PING_INTERVAL_MS)
    fun pingStreams() {
        openStreams.forEach { send(it, SseEmitter.event().comment("ping")) }
    }

    private fun send(emitter: SseEmitter, event: SseEmitter.SseEventBuilder) {
        synchronized(emitter) {
            try {
                emitter.send(event)
            } catch (_: IOException) {
                openStreams.remove(emitter)
            } catch (_: IllegalStateException) {
                openStreams.remove(emitter)
            }
        }
    }

    private fun AssistantStreamEvent.toSseEvent(): SseEmitter.SseEventBuilder {
        return when (this) {
            is AssistantStreamEvent.Started -> sseEvent(
                type = AssistantSseEventType.STARTED,
                data = AssistantStartedDto(messageId = messageId, discoverChatId = discoverChatId)
            )

            is AssistantStreamEvent.Status -> sseEvent(AssistantSseEventType.STATUS, AssistantStatusDto(kind))

            is AssistantStreamEvent.Text -> sseEvent(AssistantSseEventType.TEXT, AssistantTextDto(text))

            is AssistantStreamEvent.Spots -> sseEvent(
                type = AssistantSseEventType.SPOTS,
                data = AssistantSpotsDto(recommendations = recommendations.map { it.toAssistantRecommendationDto() })
            )

            AssistantStreamEvent.Done -> sseEvent(AssistantSseEventType.DONE, emptyMap<String, Any>())

            is AssistantStreamEvent.Error -> sseEvent(AssistantSseEventType.ERROR, AssistantErrorDto(reason))
        }
    }

    private fun sseEvent(type: AssistantSseEventType, data: Any): SseEmitter.SseEventBuilder {
        return SseEmitter.event()
            .name(type.name)
            .data(data, MediaType.APPLICATION_JSON)
    }
}
