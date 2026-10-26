package com.eeseka.lynk.support

import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.agents.core.tools.ToolDescriptor
import kotlinx.coroutines.flow.Flow
import org.springframework.boot.test.context.TestComponent
import org.springframework.context.annotation.Primary
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Stands in for Anthropic.
 *
 * Each test scripts what the model answers, one response per call, in the order the calls happen.
 * A Mockito mock cannot do this: Koog marks execute as synthetic for Java callers, and Mockito
 * skips synthetic methods, so the mock never answers.
 */
@Primary
@TestComponent
class TestModel : PromptExecutor() {

    private val scriptedCalls = ConcurrentLinkedQueue<suspend () -> Message.Assistant>()

    /** Every prompt the model was sent, in order, so a test can read what the model was told. */
    val prompts = ConcurrentLinkedQueue<Prompt>()

    fun script(vararg calls: suspend () -> Message.Assistant) {
        scriptedCalls.clear()
        scriptedCalls.addAll(calls)
    }

    /** Called between tests, so one test's leftover calls never answer the next. */
    fun reset() {
        scriptedCalls.clear()
        prompts.clear()
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
        prompts.add(prompt)
        val call = scriptedCalls.poll() ?: error("The model was called more times than the test scripted")
        return call()
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> =
        error("The assistant never streams")

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        error("The assistant never moderates")

    override fun close() = Unit
}
