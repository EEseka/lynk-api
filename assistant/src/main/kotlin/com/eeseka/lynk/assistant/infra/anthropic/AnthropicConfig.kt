package com.eeseka.lynk.assistant.infra.anthropic

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AnthropicConfig(
    @param:Value("\${anthropic.api-key}") private val apiKey: String
) {
    @Bean
    fun anthropicPromptExecutor(): PromptExecutor {
        return MultiLLMPromptExecutor(
            LLMProvider.Anthropic to AnthropicLLMClient(
                apiKey = apiKey,
                settings = AnthropicClientSettings(
                    modelVersionsMap = mapOf(ClaudeModels.Sonnet_5_5 to ClaudeModels.Sonnet_5_5.id)
                )
            )
        )
    }
}
