package com.eeseka.lynk.assistant.service.agent

import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toMessageResponse
import com.eeseka.lynk.assistant.domain.exception.AssistantRefusedException
import com.eeseka.lynk.assistant.domain.model.AssistantAnswer
import com.eeseka.lynk.assistant.domain.model.AssistantStatusKind
import com.eeseka.lynk.assistant.domain.model.AssistantStreamEvent
import com.eeseka.lynk.assistant.infra.anthropic.ClaudeModels
import com.eeseka.lynk.assistant.service.agent.tools.AssistantToolName
import com.eeseka.lynk.assistant.service.agent.tools.AssistantToolService
import org.springframework.stereotype.Component

@Component
class AssistantAgent(
    private val promptExecutor: PromptExecutor,
    private val assistantToolService: AssistantToolService
) {
    companion object {
        private const val MAX_MODEL_CALLS = 8
        private const val REFUSAL_STOP_REASON = "refusal"
    }

    // TODO: once Koog streams thinking (past 1.3.0), the model plans in thinking blocks instead of text, so each round's text can stream live again and the thinking can show in the app as its own section
    suspend fun answer(
        prompt: Prompt,
        turn: AssistantTurn,
        onEvent: (AssistantStreamEvent) -> Unit
    ): AssistantAnswer {
        var conversation = prompt
        // Words written before a search or a details lookup are the model talking itself through it, so only what comes after the last lookup can be the reply
        var reply = ""
        var inputTokens = 0
        var outputTokens = 0

        repeat(MAX_MODEL_CALLS) {
            val frames = mutableListOf<StreamFrame>()
            promptExecutor.executeStreaming(
                prompt = conversation,
                model = ClaudeModels.Haiku_5_5,
                tools = assistantToolService.toolDescriptors
            ).collect { frame -> frames.add(frame) }

            val response = frames.toMessageResponse()
            inputTokens += response.metaInfo.inputTokensCount ?: 0
            outputTokens += response.metaInfo.outputTokensCount ?: 0

            if (response.finishReason == REFUSAL_STOP_REASON) throw AssistantRefusedException()

            val roundText = frames.filterIsInstance<StreamFrame.TextDelta>().joinToString("") { it.text }
            val toolCalls = response.parts.filterIsInstance<MessagePart.Tool.Call>()
            val isLookingUp = toolCalls.any { AssistantToolName.fromWireName(it.tool) != AssistantToolName.RECOMMEND_SPOT }
            reply = when {
                isLookingUp -> ""
                roundText.isBlank() -> reply
                else -> roundText
            }

            if (toolCalls.isEmpty()) {
                return finish(reply, inputTokens, outputTokens, onEvent)
            }

            val toolResults = toolCalls.map { call ->
                statusFor(call.tool)?.let { onEvent(AssistantStreamEvent.Status(it)) }
                call to assistantToolService.runTool(call.tool, call.args, turn)
            }

            // Asked again after its cards are added, the model writes the same reply a second time
            val isAllCardsAdded = !isLookingUp && toolResults.none { (_, result) -> result.isError }
            if (isAllCardsAdded && roundText.isNotBlank()) {
                return finish(reply, inputTokens, outputTokens, onEvent)
            }

            conversation = prompt(conversation.withMessages { it + response }) {
                user {
                    toolResults.forEach { (call, result) ->
                        toolResult(id = call.id, tool = call.tool, output = result.output, isError = result.isError)
                    }
                }
            }
        }

        return finish(reply, inputTokens, outputTokens, onEvent)
    }

    private fun finish(
        reply: String,
        inputTokens: Int,
        outputTokens: Int,
        onEvent: (AssistantStreamEvent) -> Unit
    ): AssistantAnswer {
        if (reply.isNotEmpty()) onEvent(AssistantStreamEvent.Text(reply))
        return AssistantAnswer(content = reply, inputTokens = inputTokens, outputTokens = outputTokens)
    }

    private fun statusFor(toolName: String): AssistantStatusKind? = when (AssistantToolName.fromWireName(toolName)) {
        AssistantToolName.SEARCH_SPOTS -> AssistantStatusKind.SEARCHING
        AssistantToolName.GET_SPOT_DETAILS -> AssistantStatusKind.READING_DETAILS
        AssistantToolName.RECOMMEND_SPOT, null -> null
    }
}
