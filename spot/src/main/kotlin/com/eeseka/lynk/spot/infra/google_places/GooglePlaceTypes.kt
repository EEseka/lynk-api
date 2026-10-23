package com.eeseka.lynk.spot.infra.google_places

import com.eeseka.lynk.spot.domain.model.SpotCategory

/**
 * The one place that says which of Google's place types Lynk files under which category.
 *
 * Anything not listed is OTHER. Search still finds those places, and a hangout can still be held at
 * one, but trending and a Top 10 never suggest them on their own, because most places of that kind
 * are not somewhere to hang out: a guest house, an empty event hall, a petrol station. A hotel with
 * a bar or a restaurant carries that type too, so it lands in Lounge or Restaurant anyway.
 *
 * Google's full list: https://developers.google.com/maps/documentation/places/web-service/place-types
 */
object GooglePlaceTypes {

    private val restaurantTypes = setOf(
        "restaurant", "bistro", "steak_house", "diner", "deli", "food_court", "kebab_shop", "noodle_shop",
        "sandwich_shop", "salad_shop", "snack_bar"
    )

    private val cafeTypes = setOf(
        "cafe", "coffee_shop", "coffee_stand", "coffee_roastery", "tea_house", "cat_cafe", "dog_cafe",
        "bakery", "pastry_shop", "cake_shop", "bagel_shop", "donut_shop", "dessert_shop", "ice_cream_shop",
        "juice_shop", "acai_shop", "chocolate_shop"
    )

    private val loungeTypes = setOf(
        "bar", "pub", "irish_pub", "gastropub", "brewpub", "lounge_bar", "cocktail_bar", "wine_bar",
        "sports_bar", "hookah_bar", "bar_and_grill", "beer_garden", "brewery", "winery"
    )

    private val clubTypes = setOf("night_club", "dance_hall")

    private val activityTypes = setOf(
        // Outdoors
        "tourist_attraction", "park", "city_park", "national_park", "state_park", "botanical_garden", "garden",
        "picnic_ground", "barbecue_area", "hiking_area", "beach", "lake", "river", "island", "woods",
        "mountain_peak", "scenic_spot", "nature_preserve", "wildlife_park", "marina", "playground",
        "indoor_playground", "skateboard_park", "cycling_park",
        // A day out, not just a bed
        "resort_hotel",
        // Rides and games
        "amusement_park", "amusement_center", "water_park", "roller_coaster", "ferris_wheel", "zoo", "aquarium",
        "planetarium", "observation_deck", "bowling_alley", "video_arcade", "go_karting_venue",
        "paintball_center", "adventure_sports_center", "miniature_golf_course", "casino",
        // Shows
        "movie_theater", "karaoke", "comedy_club", "live_music_venue", "concert_hall", "opera_house",
        "philharmonic_hall", "amphitheatre", "performing_arts_theater",
        // Culture
        "art_gallery", "art_museum", "art_studio", "museum", "history_museum", "cultural_center",
        "cultural_landmark", "historical_landmark", "historical_place", "monument", "castle",
        // Sport to play or watch
        "golf_course", "indoor_golf_course", "ice_skating_rink", "swimming_pool", "tennis_court", "stadium",
        "arena", "sports_complex", "sports_activity_location",
        // Shopping as an outing
        "shopping_mall"
    )

    /**
     * A main type from these groups outweighs any counter the place has, so Shoprite's bakery or the
     * ice cream stand at Lagos House doesn't make either a café. Lodging and Services are left out:
     * a hotel's bar and a members' club's restaurant are real reasons to go.
     */
    private val neverHangoutMainTypes = setOf(
        // Automotive
        "car_dealer", "car_rental", "car_repair", "car_wash", "ebike_charging_station",
        "electric_vehicle_charging_station", "gas_station", "parking", "parking_garage", "parking_lot",
        "rest_stop", "tire_shop", "truck_dealer",
        // Business
        "business_center", "corporate_office", "coworking_space", "farm", "manufacturer", "ranch", "supplier",
        "television_studio",
        // Education
        "academic_department", "educational_institution", "library", "preschool", "primary_school",
        "research_institute", "school", "secondary_school", "university",
        // Facilities
        "public_bath", "public_bathroom", "stable",
        // Finance
        "accounting", "atm", "bank",
        // Government
        "city_hall", "courthouse", "embassy", "fire_station", "government_office", "local_government_office",
        "neighborhood_police_station", "police", "post_office",
        // Health and Wellness
        "chiropractor", "dental_clinic", "dentist", "doctor", "drugstore", "general_hospital", "hospital",
        "massage", "massage_spa", "medical_center", "medical_clinic", "medical_lab", "pharmacy",
        "physiotherapist", "sauna", "skin_care_clinic", "spa", "tanning_studio", "wellness_center", "yoga_studio",
        // Housing
        "apartment_building", "apartment_complex", "condominium_complex", "housing_complex",
        // Places of Worship
        "buddhist_temple", "church", "hindu_temple", "mosque", "shinto_shrine", "synagogue",
        // Shopping, bar the mall
        "asian_grocery_store", "auto_parts_store", "bicycle_store", "book_store", "building_materials_store",
        "butcher_shop", "cell_phone_store", "clothing_store", "convenience_store", "cosmetics_store",
        "department_store", "discount_store", "discount_supermarket", "electronics_store", "farmers_market",
        "flea_market", "food_store", "furniture_store", "garden_center", "general_store", "gift_shop",
        "grocery_store", "hardware_store", "health_food_store", "home_goods_store", "home_improvement_store",
        "hypermarket", "jewelry_store", "liquor_store", "market", "pet_store", "shoe_store",
        "sporting_goods_store", "sportswear_store", "store", "supermarket", "tea_store", "thrift_store",
        "toy_store", "warehouse_store", "wholesaler", "womens_clothing_store",
        // Transportation
        "airport", "airstrip", "bike_sharing_station", "bridge", "bus_station", "bus_stop", "ferry_service",
        "ferry_terminal", "heliport", "international_airport", "light_rail_station", "park_and_ride",
        "subway_station", "taxi_service", "taxi_stand", "toll_station", "train_station", "train_ticket_office",
        "tram_stop", "transit_depot", "transit_station", "transit_stop", "transportation_service", "truck_stop"
    )

    private val categoryByType: Map<String, SpotCategory> = buildMap {
        restaurantTypes.forEach { put(it, SpotCategory.RESTAURANT) }
        cafeTypes.forEach { put(it, SpotCategory.CAFE) }
        loungeTypes.forEach { put(it, SpotCategory.LOUNGE) }
        clubTypes.forEach { put(it, SpotCategory.CLUB) }
        activityTypes.forEach { put(it, SpotCategory.ACTIVITY) }
    }

    /**
     * What trending asks Google for. Nearby Search takes at most 50 types, fewer than the category sets
     * above list, so these are the broad types plus the kinds no broad type catches.
     */
    val trendingTypes: List<String> = listOf(
        "restaurant", "cafe", "coffee_shop", "bakery", "ice_cream_shop", "dessert_shop", "juice_shop",
        "bar", "pub", "lounge_bar", "bar_and_grill", "beer_garden", "wine_bar", "cocktail_bar", "sports_bar",
        "hookah_bar", "night_club", "dance_hall", "tourist_attraction", "park", "botanical_garden", "garden",
        "beach", "lake", "scenic_spot", "playground", "amusement_park", "amusement_center", "water_park",
        "zoo", "aquarium", "bowling_alley", "video_arcade", "go_karting_venue", "paintball_center",
        "adventure_sports_center", "miniature_golf_course", "casino", "movie_theater", "karaoke", "comedy_club",
        "live_music_venue", "concert_hall", "performing_arts_theater", "art_gallery", "museum", "art_studio",
        "cultural_center", "stadium", "shopping_mall"
    )

    /**
     * The category of the first of a place's types that Lynk knows, its main type checked first.
     * Google has over 100 cuisine types, all ending in "_restaurant", so those are matched by name
     * rather than listed.
     */
    fun categoryOf(primaryType: String?, types: List<String>?): SpotCategory {
        if (primaryType?.lowercase() in neverHangoutMainTypes) return SpotCategory.OTHER

        val allTypes = (listOfNotNull(primaryType) + types.orEmpty()).map { it.lowercase() }

        return allTypes.firstNotNullOfOrNull { type ->
            categoryByType[type] ?: SpotCategory.RESTAURANT.takeIf { type.endsWith("_restaurant") }
        } ?: SpotCategory.OTHER
    }
}
