package com.eeseka.lynk.assistant.infra.database.entities

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class AssistantUsageEntity(
    @Column(nullable = true)
    var inputTokens: Int,

    @Column(nullable = true)
    var cacheCreationInputTokens: Int,

    @Column(nullable = true)
    var cacheReadInputTokens: Int,

    @Column(nullable = true)
    var outputTokens: Int,

    @Column(nullable = true)
    var searchCount: Int
)
