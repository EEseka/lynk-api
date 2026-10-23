package com.eeseka.lynk.assistant.api.dto.sse

import com.eeseka.lynk.assistant.domain.model.AssistantStatusKind

data class AssistantStatusDto(
    val kind: AssistantStatusKind
)
