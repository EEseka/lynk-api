package com.eeseka.lynk.assistant.service.agent.tools

enum class AssistantToolName(val wireName: String) {
    SEARCH_SPOTS("search_spots"),
    GET_SPOT_DETAILS("get_spot_details"),
    RECOMMEND_SPOT("recommend_spot");

    companion object {
        fun fromWireName(wireName: String): AssistantToolName? = entries.find { it.wireName == wireName }
    }
}
