package com.eeseka.lynk.assistant.infra.anthropic

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

object ClaudeModels {
    // Koog 1.3.0 stops at Haiku 4.5
    // No Temperature because Haiku 5.5 rejects sampling settings with a 400
    val Haiku_5_5 = LLModel(
        provider = LLMProvider.Anthropic,
        id = "claude-haiku-5-5",
        capabilities = listOf(
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Completion,
            LLMCapability.Thinking,
            LLMCapability.PromptCaching
        ),
        contextLength = 1_000_000,
        maxOutputTokens = 64_000
    )
}
