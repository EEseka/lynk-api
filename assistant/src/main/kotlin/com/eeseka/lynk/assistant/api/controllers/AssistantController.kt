package com.eeseka.lynk.assistant.api.controllers

import com.eeseka.lynk.assistant.api.dto.AssistantMessageCountDto
import com.eeseka.lynk.assistant.api.dto.AssistantMessageDto
import com.eeseka.lynk.assistant.api.dto.AssistantUsageDto
import com.eeseka.lynk.assistant.api.dto.SendAssistantMessageRequest
import com.eeseka.lynk.assistant.api.mappers.toAssistantMessageDto
import com.eeseka.lynk.assistant.api.mappers.toAssistantUsageDto
import com.eeseka.lynk.assistant.api.sse.AssistantStreamHandler
import com.eeseka.lynk.assistant.service.AssistantService
import com.eeseka.lynk.common.api.config.UserRateLimit
import com.eeseka.lynk.common.api.util.requestUserId
import com.eeseka.lynk.common.domain.type.HangoutId
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant
import java.util.concurrent.TimeUnit

@Validated
@RestController
@RequestMapping("/api/assistant")
class AssistantController(
    private val assistantService: AssistantService,
    private val assistantStreamHandler: AssistantStreamHandler
) {
    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
        private const val MAX_PAGE_SIZE = 50
    }

    @UserRateLimit(
        requests = AssistantService.MESSAGES_PER_DAY,
        duration = 1L,
        unit = TimeUnit.DAYS
    )
    @PostMapping("/messages", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun sendMessage(
        @Valid @RequestBody body: SendAssistantMessageRequest
    ): SseEmitter {
        return assistantStreamHandler.open { onEvent, onFinished ->
            assistantService.startAnswer(
                userId = requestUserId,
                hangoutId = body.hangoutId,
                discoverChatId = body.discoverChatId,
                latitude = body.latitude,
                longitude = body.longitude,
                timeZone = body.timeZone,
                question = body.content.trim(),
                onEvent = onEvent,
                onFinished = onFinished
            )
        }
    }

    @GetMapping("/hangouts/{hangoutId}/messages")
    fun getMessages(
        @PathVariable hangoutId: HangoutId,

        @RequestParam(required = false)
        before: Instant? = null,

        @RequestParam(required = false)
        @Min(1) @Max(MAX_PAGE_SIZE.toLong())
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): List<AssistantMessageDto> {
        return assistantService.getMessages(
            userId = requestUserId,
            hangoutId = hangoutId,
            before = before,
            pageSize = pageSize
        ).map { it.toAssistantMessageDto() }
    }

    @GetMapping("/hangouts/{hangoutId}/messages/count")
    fun getMessageCount(
        @PathVariable hangoutId: HangoutId
    ): AssistantMessageCountDto {
        return AssistantMessageCountDto(
            assistantService.getMessageCount(
                userId = requestUserId,
                hangoutId = hangoutId
            )
        )
    }

    @DeleteMapping("/hangouts/{hangoutId}/messages")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun clearChat(
        @PathVariable hangoutId: HangoutId
    ) {
        assistantService.clearChat(
            userId = requestUserId,
            hangoutId = hangoutId
        )
    }

    @GetMapping("/usage")
    fun getUsage(): AssistantUsageDto {
        return assistantService.getUsage(requestUserId).toAssistantUsageDto(limit = AssistantService.MESSAGES_PER_DAY)
    }
}
