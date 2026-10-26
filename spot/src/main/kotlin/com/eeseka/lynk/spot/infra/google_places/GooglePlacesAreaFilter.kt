package com.eeseka.lynk.spot.infra.google_places

import com.eeseka.lynk.spot.infra.google_places.dto.GooglePlace

// The parts of an address that name an area someone could be "in": neighborhood up to state
private val AREA_COMPONENT_TYPES = setOf(
    "neighborhood", "sublocality", "sublocality_level_1", "locality", "postal_town",
    "administrative_area_level_3", "administrative_area_level_2", "administrative_area_level_1"
)

private val AREA_NAME_TYPES = listOf("locality", "postal_town", "administrative_area_level_1")

// The name most of these places share, so every phone calls the same area the same thing
internal fun List<GooglePlace>.areaName(): String? {
    for (type in AREA_NAME_TYPES) {
        val names = mapNotNull { place ->
            place.addressComponents.orEmpty()
                .firstOrNull { type in it.types.orEmpty() }
                ?.longText?.takeIf { it.isNotBlank() }
        }
        // A tie goes to the name Google ranked first
        names.groupingBy { it }.eachCount().maxByOrNull { it.value }?.let { return it.key }
    }
    return null
}

// By address, not distance, because an area can be anything from a neighborhood to a whole state.
// No match at all most likely means this search and trending spell the area differently, so nothing is dropped
internal fun List<GooglePlace>.inArea(areaName: String): List<GooglePlace> {
    val area = areaName.comparable()
    if (area.isEmpty()) return this

    val inArea = filter { place ->
        place.addressComponents.orEmpty().any { component ->
            component.types.orEmpty().any { it in AREA_COMPONENT_TYPES } &&
                    component.longText.orEmpty().comparable().contains(area)
        }
    }
    return inArea.ifEmpty { this }
}

// "Ijebu-Ode" and "ijebu ode" are the same place
private fun String.comparable(): String = lowercase().filter { it.isLetterOrDigit() }
