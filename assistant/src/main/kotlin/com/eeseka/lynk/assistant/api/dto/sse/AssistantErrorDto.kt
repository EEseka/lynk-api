package com.eeseka.lynk.assistant.api.dto.sse

import com.eeseka.lynk.assistant.domain.model.AssistantFailureReason

data class AssistantErrorDto(
    val reason: AssistantFailureReason
)
