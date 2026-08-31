package com.eeseka.lynk.spot

import com.eeseka.lynk.spot.domain.model.PriceLevel
import com.eeseka.lynk.spot.domain.model.Spot
import com.eeseka.lynk.spot.domain.model.SpotCategory
import com.eeseka.lynk.spot.infra.database.repositories.SavedSpotRepository
import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.TestAccount
import com.eeseka.lynk.support.authenticatedAs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals

/**
 * Spots come from Google, and the only thing Lynk keeps is which of them somebody saved. These
 * tests hold Google still and check the part that is ours: the saved flag, the saved list, and what
 * happens when the same spot is saved twice.
 */
class SpotJourneyTest : IntegrationTest() {

    private companion object {
        // A Google place id, which is what the path variable carries - no slashes in it.
        const val SPOT_ID = "ChIJterrakulture"
        const val OTHER_SPOT_ID = "ChIJnokbyalara"
        const val LATITUDE = 6.4281
        const val LONGITUDE = 3.4219
    }

    @Autowired
    private lateinit var savedSpotRepository: SavedSpotRepository

    private lateinit var user: TestAccount

    @BeforeEach
    fun signInAndStubGoogle() {
        user = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")

        given(googlePlacesClient.getSpotById(eq(SPOT_ID))).willReturn(spot(SPOT_ID, "Terra Kulture"))
        given(googlePlacesClient.getSpotById(eq(OTHER_SPOT_ID))).willReturn(spot(OTHER_SPOT_ID, "Nok by Alara"))
    }

    @Test
    fun `hands back what Google found`() {
        given(
            googlePlacesClient.searchSpots(
                latitude = eq(LATITUDE),
                longitude = eq(LONGITUDE),
                query = eq("jollof"),
                category = eq(SpotCategory.RESTAURANT),
                priceLevel = eq(PriceLevel.MODERATE),
                radiusInMeters = eq(5000),
                nextPageToken = eq("page-2")
            )
        ).willReturn(listOf(spot(SPOT_ID, "Terra Kulture")) to "page-3")

        mockMvc.get("/api/spots/search") {
            authenticatedAs(user)
            param("latitude", LATITUDE.toString())
            param("longitude", LONGITUDE.toString())
            param("query", "jollof")
            param("category", SpotCategory.RESTAURANT.name)
            param("priceLevel", PriceLevel.MODERATE.name)
            param("radiusInMeters", "5000")
            param("nextPageToken", "page-2")
        }.andExpect {
            status { isOk() }
            jsonPath("$.spots.length()") { value(1) }
            jsonPath("$.spots[0].id") { value(SPOT_ID) }
            jsonPath("$.spots[0].isSaved") { value(false) }
            jsonPath("$.nextPageToken") { value("page-3") }
        }
    }

    /**
     * A bad query parameter is the caller's mistake, and the client reads the status to know whose
     * mistake it was. Answering 500 would tell it we broke.
     */
    @Test
    fun `refuses a search off the edge of the world`() {
        mockMvc.get("/api/spots/search") {
            authenticatedAs(user)
            param("latitude", "91.0")
            param("longitude", LONGITUDE.toString())
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_ERROR") }
        }
    }

    @Test
    fun `refuses to be asked for more spots than it will give`() {
        mockMvc.get("/api/spots/trending") {
            authenticatedAs(user)
            param("latitude", LATITUDE.toString())
            param("longitude", LONGITUDE.toString())
            param("limit", "500")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_ERROR") }
        }
    }

    @Test
    fun `saves a spot and shows it saved from then on`() {
        saveSpot(SPOT_ID).andExpect { status { isCreated() } }

        mockMvc.get("/api/spots/$SPOT_ID") {
            authenticatedAs(user)
        }.andExpect {
            status { isOk() }
            jsonPath("$.isSaved") { value(true) }
        }

        mockMvc.get("/api/spots/saved") {
            authenticatedAs(user)
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].id") { value(SPOT_ID) }
        }
    }

    /**
     * Two taps on the save button race each other, and the unique constraint is what settles it. The
     * second one must be a quiet no-op rather than an error in somebody's face.
     */
    @Test
    fun `saves the same spot twice without complaining`() {
        saveSpot(SPOT_ID).andExpect { status { isCreated() } }
        saveSpot(SPOT_ID).andExpect { status { isCreated() } }

        assertEquals(1, savedSpotRepository.count())
    }

    @Test
    fun `forgets a spot that was unsaved`() {
        saveSpot(SPOT_ID).andExpect { status { isCreated() } }

        mockMvc.delete("/api/spots/$SPOT_ID/save") {
            authenticatedAs(user)
        }.andExpect { status { isNoContent() } }

        assertEquals(0, savedSpotRepository.count())
        mockMvc.get("/api/spots/saved") {
            authenticatedAs(user)
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `keeps one person's saved spots to themselves`() {
        val other = accounts.signIn(email = "bola@lynk.test", displayName = "Bola", username = "bola")
        saveSpot(SPOT_ID).andExpect { status { isCreated() } }

        mockMvc.get("/api/spots/saved") {
            authenticatedAs(other)
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }

        mockMvc.get("/api/spots/$SPOT_ID") {
            authenticatedAs(other)
        }.andExpect {
            status { isOk() }
            jsonPath("$.isSaved") { value(false) }
        }
    }

    @Test
    fun `searches somebody's saved spots by name`() {
        saveSpot(SPOT_ID).andExpect { status { isCreated() } }
        saveSpot(OTHER_SPOT_ID).andExpect { status { isCreated() } }

        mockMvc.get("/api/spots/saved") {
            authenticatedAs(user)
            param("query", "terra")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].id") { value(SPOT_ID) }
        }
    }

    @Test
    fun `says so when Google has never heard of the place`() {
        given(googlePlacesClient.getSpotById(eq("ChIJnotaplace"))).willReturn(null)

        mockMvc.get("/api/spots/ChIJnotaplace") {
            authenticatedAs(user)
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `marks the saved ones in what is trending`() {
        saveSpot(SPOT_ID).andExpect { status { isCreated() } }
        given(
            googlePlacesClient.getTrendingSpots(
                latitude = eq(LATITUDE),
                longitude = eq(LONGITUDE),
                limit = eq(20)
            )
        ).willReturn(listOf(spot(SPOT_ID, "Terra Kulture"), spot(OTHER_SPOT_ID, "Nok by Alara")))

        mockMvc.get("/api/spots/trending") {
            authenticatedAs(user)
            param("latitude", LATITUDE.toString())
            param("longitude", LONGITUDE.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].isSaved") { value(true) }
            jsonPath("$[1].isSaved") { value(false) }
        }
    }

    private fun saveSpot(spotId: String) = mockMvc.post("/api/spots/$spotId/save") {
        authenticatedAs(user)
    }

    private fun spot(id: String, name: String) = Spot(
        id = id,
        name = name,
        description = "Somewhere to eat",
        photoUrls = listOf("https://places.test/$id.jpg"),
        category = SpotCategory.RESTAURANT,
        tags = listOf("jollof"),
        priceLevel = PriceLevel.MODERATE,
        rating = 4.5,
        reviewCount = 120,
        isOpenNow = true,
        shortAddress = "Victoria Island, Lagos",
        latitude = LATITUDE,
        longitude = LONGITUDE,
        websiteUrl = null,
        googleMapsUrl = null,
        isSaved = false
    )
}
