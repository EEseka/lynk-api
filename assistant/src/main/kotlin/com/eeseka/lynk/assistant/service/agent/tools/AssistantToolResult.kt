package com.eeseka.lynk.assistant.service.agent.tools

// What goes back to the model after it asked for a tool.
data class AssistantToolResult(
    val output: String,
    val isError: Boolean
)
