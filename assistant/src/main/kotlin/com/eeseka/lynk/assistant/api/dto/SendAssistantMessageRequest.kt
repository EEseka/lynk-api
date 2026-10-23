package com.eeseka.lynk.assistant.api.dto

import com.eeseka.lynk.assistant.domain.AssistantConstants.MAX_MESSAGE_LENGTH
import com.eeseka.lynk.common.domain.type.DiscoverChatId
import com.eeseka.lynk.common.domain.type.HangoutId
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SendAssistantMessageRequest(
    val hangoutId: HangoutId? = null,
    val discoverChatId: DiscoverChatId? = null,
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val latitude: Double? = null,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val longitude: Double? = null,
    val timeZone: String? = null,
    @field:NotBlank
    @field:Size(max = MAX_MESSAGE_LENGTH)
    val content: String
)
