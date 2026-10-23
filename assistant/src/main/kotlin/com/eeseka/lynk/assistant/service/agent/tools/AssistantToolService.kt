package com.eeseka.lynk.assistant.service.agent.tools

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import com.eeseka.lynk.assistant.domain.model.AssistantRecommendation
import com.eeseka.lynk.assistant.service.agent.AssistantTurn
import com.eeseka.lynk.assistant.service.agent.tools.dto.inbound.GetSpotDetailsArgsDto
import com.eeseka.lynk.assistant.service.agent.tools.dto.inbound.RecommendSpotArgsDto
import com.eeseka.lynk.assistant.service.agent.tools.dto.inbound.SearchSpotsArgsDto
import com.eeseka.lynk.assistant.service.agent.tools.dto.outbound.ToolErrorDto
import com.eeseka.lynk.assistant.service.agent.tools.mappers.toSpotCandidateDto
import com.eeseka.lynk.assistant.service.agent.tools.mappers.toSpotDetailsDto
import com.eeseka.lynk.spot.domain.TopSpotsRanking
import com.eeseka.lynk.spot.domain.exception.SpotNotFoundException
import com.eeseka.lynk.spot.domain.model.PriceLevel
import com.eeseka.lynk.spot.domain.model.Spot
import com.eeseka.lynk.spot.domain.model.SpotCategory
import com.eeseka.lynk.spot.service.SpotService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper

@Service
class AssistantToolService(
    private val spotService: SpotService,
    private val objectMapper: ObjectMapper
) {
    companion object {
        const val MAX_SEARCHES_PER_ANSWER = 3
        const val MAX_RECOMMENDATIONS_PER_ANSWER = 5
        private const val MAX_REASON_LENGTH = 300
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    val toolDescriptors: List<ToolDescriptor> = listOf(
        ToolDescriptor(
            name = AssistantToolName.SEARCH_SPOTS.wireName,
            description = "Search Google Places for spots to hang out, around the group or the area being looked at. " +
                    "Returns up to 10 places, best rated first. You can search at most $MAX_SEARCHES_PER_ANSWER times per answer, so make each one count.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "query",
                    description = "What to look for, in a few words, e.g. \"rooftop bar with live music\". " +
                            "Add the area when you know it, e.g. \"quiet cafe in Yaba\".",
                    type = ToolParameterType.String
                )
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "category",
                    description = "Only places of this kind.",
                    type = ToolParameterType.Enum(
                        SpotCategory.entries.filter { it != SpotCategory.OTHER }.map { it.name }.toTypedArray()
                    )
                ),
                ToolParameterDescriptor(
                    name = "priceLevel",
                    description = "Only places at this price tier.",
                    type = ToolParameterType.Enum(PriceLevel.entries.map { it.name }.toTypedArray())
                )
            )
        ),
        ToolDescriptor(
            name = AssistantToolName.GET_SPOT_DETAILS.wireName,
            description = "Opening hours for the week, amenities, parking and payment options for one place. " +
                    "Only for places from your searches, the ballot or earlier cards.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "spotId",
                    description = "The spotId exactly as a search, the ballot or an earlier card gave it.",
                    type = ToolParameterType.String
                )
            )
        ),
        ToolDescriptor(
            name = AssistantToolName.RECOMMEND_SPOT.wireName,
            description = "Show one place to the user as a card they can open, save or suggest. " +
                    "Call it once per place you recommend, at most $MAX_RECOMMENDATIONS_PER_ANSWER times per answer. " +
                    "Only for places from your searches, the ballot or earlier cards.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "spotId",
                    description = "The spotId exactly as a search, the ballot or an earlier card gave it.",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "reason",
                    description = "One short sentence on why this place fits what they asked for, in the user's language, under $MAX_REASON_LENGTH characters.",
                    type = ToolParameterType.String
                )
            )
        )
    )

    fun runTool(toolName: String, argsJson: String, turn: AssistantTurn): AssistantToolResult {
        return try {
            when (AssistantToolName.fromWireName(toolName)) {
                AssistantToolName.SEARCH_SPOTS -> searchSpots(
                    args = objectMapper.readValue(argsJson, SearchSpotsArgsDto::class.java),
                    turn = turn
                )

                AssistantToolName.GET_SPOT_DETAILS -> getSpotDetails(
                    args = objectMapper.readValue(argsJson, GetSpotDetailsArgsDto::class.java),
                    turn = turn
                )

                AssistantToolName.RECOMMEND_SPOT -> recommendSpot(
                    args = objectMapper.readValue(argsJson, RecommendSpotArgsDto::class.java),
                    turn = turn
                )

                null -> error("There is no tool called $toolName.")
            }
        } catch (_: JacksonException) {
            error("Those arguments could not be read. Check them against the tool's parameters.")
        } catch (e: RestClientException) {
            logger.warn("Google Places failed during an assistant tool call", e)
            error("Google Places is not answering right now. Answer with what you already have.")
        }
    }

    private fun searchSpots(args: SearchSpotsArgsDto, turn: AssistantTurn): AssistantToolResult {
        if (turn.searchCount >= MAX_SEARCHES_PER_ANSWER) {
            return error("You have used all $MAX_SEARCHES_PER_ANSWER searches for this answer. Answer with the places you already have.")
        }
        turn.searchCount++

        val found = spotService.searchSpots(
            latitude = turn.searchLatitude,
            longitude = turn.searchLongitude,
            query = args.query,
            category = args.category,
            priceLevel = args.priceLevel,
            nextPageToken = null,
            userId = turn.userId
        ).spots

        val hangoutSpots = TopSpotsRanking.pick(found.filter { it.category != SpotCategory.OTHER })
        hangoutSpots.forEach { turn.seenSpots[it.id] = it }

        return ok(hangoutSpots.map { it.toSpotCandidateDto(turn) })
    }

    private fun getSpotDetails(args: GetSpotDetailsArgsDto, turn: AssistantTurn): AssistantToolResult {
        val spot = findAllowedSpot(args.spotId, turn)
            ?: return error("Only look up places from your searches, the ballot or earlier cards.")

        return ok(spot.toSpotDetailsDto(turn))
    }

    private fun recommendSpot(args: RecommendSpotArgsDto, turn: AssistantTurn): AssistantToolResult {
        if (turn.recommendations.size >= MAX_RECOMMENDATIONS_PER_ANSWER) {
            return error("This answer already has $MAX_RECOMMENDATIONS_PER_ANSWER cards, the most it can show.")
        }
        if (turn.recommendations.any { it.spot.id == args.spotId }) {
            return error("That place is already a card in this answer.")
        }
        val reason = args.reason.trim()
        if (reason.isEmpty()) {
            return error("Give a reason the user can read on the card.")
        }
        if (reason.length > MAX_REASON_LENGTH) {
            return error("Keep the reason under $MAX_REASON_LENGTH characters and add the card again.")
        }
        val spot = findAllowedSpot(args.spotId, turn)
            ?: return error("Only recommend places from your searches, the ballot or earlier cards.")

        turn.recommendations.add(AssistantRecommendation(spot = spot, reason = reason))
        return ok("Card ${turn.recommendations.size} of at most $MAX_RECOMMENDATIONS_PER_ANSWER added.")
    }

    // A place this answer saw, or one already on the ballot or an earlier card, fetched through the details cache
    private fun findAllowedSpot(spotId: String, turn: AssistantTurn): Spot? {
        turn.seenSpots[spotId]?.let { return it }
        if (spotId !in turn.ballotSpotIds && spotId !in turn.earlierRecommendedSpotIds) return null

        return try {
            spotService.getSpotById(spotId, turn.userId).also { turn.seenSpots[spotId] = it }
        } catch (_: SpotNotFoundException) {
            null
        }
    }

    private fun ok(output: Any) = AssistantToolResult(
        output = objectMapper.writeValueAsString(output),
        isError = false
    )

    private fun error(message: String) = AssistantToolResult(
        output = objectMapper.writeValueAsString(ToolErrorDto(message)),
        isError = true
    )
}
