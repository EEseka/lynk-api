package com.eeseka.lynk.assistant.service.agent

import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import com.eeseka.lynk.assistant.domain.exception.AssistantRefusedException
import com.eeseka.lynk.assistant.domain.model.AssistantAnswer
import com.eeseka.lynk.assistant.domain.model.AssistantStatusKind
import com.eeseka.lynk.assistant.domain.model.AssistantStreamEvent
import com.eeseka.lynk.assistant.domain.model.AssistantUsage
import com.eeseka.lynk.assistant.infra.anthropic.ClaudeModels
import com.eeseka.lynk.assistant.service.agent.tools.AssistantToolName
import com.eeseka.lynk.assistant.service.agent.tools.AssistantToolService
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

@Component
class AssistantAgent(
    private val promptExecutor: PromptExecutor,
    private val assistantToolService: AssistantToolService
) {
    companion object {
        private const val MAX_MODEL_CALLS = 8
        private const val REFUSAL_STOP_REASON = "refusal"
        private const val CACHE_CREATION_INPUT_TOKENS_KEY = "cacheCreationInputTokens"
        private const val CACHE_READ_INPUT_TOKENS_KEY = "cacheReadInputTokens"
    }

    // TODO: Koog 1.3.0's streaming drops the thinking signature that the round after a tool call must send back, so each round waits for the whole response; once Koog fixes it, stream each round live again
    suspend fun answer(
        prompt: Prompt,
        turn: AssistantTurn,
        onEvent: (AssistantStreamEvent) -> Unit
    ): AssistantAnswer {
        var conversation = prompt
        // Words written before a search or a details lookup are the model talking itself through it, so only what comes after the last lookup can be the reply
        var reply = ""
        var usage = AssistantUsage(inputTokens = 0, cacheCreationInputTokens = 0, cacheReadInputTokens = 0, outputTokens = 0, searchCount = 0)

        repeat(MAX_MODEL_CALLS) {
            val response = promptExecutor.execute(
                prompt = conversation,
                model = ClaudeModels.Sonnet_5_5,
                tools = assistantToolService.toolDescriptors
            )
            usage = usage.plus(response.metaInfo)

            if (response.finishReason == REFUSAL_STOP_REASON) throw AssistantRefusedException()

            val roundText = response.parts.filterIsInstance<MessagePart.Text>().joinToString("") { it.text }
            val toolCalls = response.parts.filterIsInstance<MessagePart.Tool.Call>()
            val isLookingUp = toolCalls.any { AssistantToolName.fromWireName(it.tool) != AssistantToolName.RECOMMEND_SPOT }
            reply = when {
                isLookingUp -> ""
                roundText.isBlank() -> reply
                else -> roundText
            }

            if (toolCalls.isEmpty()) {
                return finish(reply, usage, turn, onEvent)
            }

            val toolResults = toolCalls.map { call ->
                statusFor(call.tool)?.let { onEvent(AssistantStreamEvent.Status(it)) }
                call to assistantToolService.runTool(call.tool, call.args, turn)
            }

            conversation = prompt(conversation.withMessages { it + response }) {
                user {
                    toolResults.forEach { (call, result) ->
                        toolResult(id = call.id, tool = call.tool, output = result.output, isError = result.isError)
                    }
                }
            }
        }

        return finish(reply, usage, turn, onEvent)
    }

    private fun finish(
        reply: String,
        usage: AssistantUsage,
        turn: AssistantTurn,
        onEvent: (AssistantStreamEvent) -> Unit
    ): AssistantAnswer {
        if (reply.isNotEmpty()) onEvent(AssistantStreamEvent.Text(reply))
        return AssistantAnswer(content = reply, usage = usage.copy(searchCount = turn.searchCount))
    }

    // Koog counts only the uncached input tokens and keeps the cache writes and reads in the metadata
    private fun AssistantUsage.plus(metaInfo: ResponseMetaInfo): AssistantUsage {
        fun metadataTokens(key: String) = metaInfo.metadata?.get(key)?.jsonPrimitive?.intOrNull ?: 0

        return copy(
            inputTokens = inputTokens + (metaInfo.inputTokensCount ?: 0),
            cacheCreationInputTokens = cacheCreationInputTokens + metadataTokens(CACHE_CREATION_INPUT_TOKENS_KEY),
            cacheReadInputTokens = cacheReadInputTokens + metadataTokens(CACHE_READ_INPUT_TOKENS_KEY),
            outputTokens = outputTokens + (metaInfo.outputTokensCount ?: 0)
        )
    }

    private fun statusFor(toolName: String): AssistantStatusKind? = when (AssistantToolName.fromWireName(toolName)) {
        AssistantToolName.SEARCH_SPOTS -> AssistantStatusKind.SEARCHING
        AssistantToolName.GET_SPOT_DETAILS -> AssistantStatusKind.READING_DETAILS
        AssistantToolName.RECOMMEND_SPOT, null -> null
    }
}
