package com.eeseka.lynk.hangout

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.hangout.api.dto.HangoutDto
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.TestAccount
import com.eeseka.lynk.support.authenticatedAs
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.*
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HangoutJourneyTest : IntegrationTest() {

    @Test
    fun `creates a hangout with its host already in it`() {
        val host = signIn("ada")

        val hangout = createHangout(host)

        assertEquals(host.userId, hangout.hostId)
        assertEquals(1, hangout.participantCount)
        // No spot was chosen, so it opens in voting rather than scheduled.
        assertEquals(HangoutStatus.VOTING, hangout.status)

        val hostParticipant = hangout.participants.single()
        assertEquals(host.userId, hostParticipant.user.userId)
        assertEquals(RsvpStatus.ATTENDING, hostParticipant.rsvpStatus)
        assertTrue(hostParticipant.hasPaid, "the host owes themselves nothing")
    }

    /**
     * The other modules only hear about a person when they finish their profile, so an account that
     * skipped it has no name to put on a hangout.
     */
    @Test
    fun `will not let somebody host before they have a profile`() {
        val nameless = accounts.signIn(email = "nameless@lynk.test", withProfileCompletion = false)

        postHangout(nameless).andExpect { status { isForbidden() } }
    }

    @Test
    fun `refuses a hangout in the past`() {
        val host = signIn("ada")

        postHangout(host, scheduledAt = Instant.now().minus(Duration.ofDays(1)))
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `finds somebody to invite by their username`() {
        val host = signIn("ada")
        val friend = signIn("bola")

        mockMvc.get("/api/participants") {
            authenticatedAs(host)
            param("query", "bola")
        }.andExpect {
            status { isOk() }
            jsonPath("$.userId") { value(friend.userId.toString()) }
        }
    }

    @Test
    fun `invites somebody and leaves them pending until they answer`() {
        val host = signIn("ada")
        val friend = signIn("bola")
        val hangout = createHangout(host)

        invite(host, hangout.id, friend).andExpect {
            status { isCreated() }
            jsonPath("$.rsvpStatus") { value(RsvpStatus.PENDING.name) }
        }

        // A pending invite holds a seat but is not yet an attendee.
        assertEquals(1, getHangout(host, hangout.id).participantCount)
    }

    @Test
    fun `lets only the host invite`() {
        val host = signIn("ada")
        val friend = signIn("bola")
        val stranger = signIn("chidi")
        val hangout = createHangout(host)
        invite(host, hangout.id, friend).andExpect { status { isCreated() } }
        accept(friend, hangout.id)

        invite(friend, hangout.id, stranger).andExpect { status { isForbidden() } }
    }

    @Test
    fun `refuses to invite the same person twice`() {
        val host = signIn("ada")
        val friend = signIn("bola")
        val hangout = createHangout(host)
        invite(host, hangout.id, friend).andExpect { status { isCreated() } }

        invite(host, hangout.id, friend).andExpect { status { isConflict() } }
    }

    /** A pending invite is a taken seat, so a hangout for two is full once one is out. */
    @Test
    fun `refuses an invite that would overfill the hangout`() {
        val host = signIn("ada")
        val friend = signIn("bola")
        val stranger = signIn("chidi")
        val hangout = createHangout(host, maxAttendees = 2)
        invite(host, hangout.id, friend).andExpect { status { isCreated() } }

        invite(host, hangout.id, stranger).andExpect { status { isConflict() } }
    }

    @Test
    fun `counts somebody once they accept`() {
        val host = signIn("ada")
        val friend = signIn("bola")
        val hangout = createHangout(host)
        invite(host, hangout.id, friend).andExpect { status { isCreated() } }

        accept(friend, hangout.id).andExpect {
            status { isOk() }
            jsonPath("$.rsvpStatus") { value(RsvpStatus.ATTENDING.name) }
        }

        assertEquals(2, getHangout(host, hangout.id).participantCount)
    }

    @Test
    fun `refuses to answer the same invite twice`() {
        val host = signIn("ada")
        val friend = signIn("bola")
        val hangout = createHangout(host)
        invite(host, hangout.id, friend).andExpect { status { isCreated() } }
        accept(friend, hangout.id).andExpect { status { isOk() } }

        accept(friend, hangout.id).andExpect { status { isConflict() } }
    }

    @Test
    fun `frees the seat when somebody declines`() {
        val host = signIn("ada")
        val friend = signIn("bola")
        val stranger = signIn("chidi")
        val hangout = createHangout(host, maxAttendees = 2)
        invite(host, hangout.id, friend).andExpect { status { isCreated() } }
        rsvp(friend, hangout.id, RsvpStatus.DECLINED).andExpect { status { isOk() } }

        invite(host, hangout.id, stranger).andExpect { status { isCreated() } }
    }

    @Test
    fun `lets an attendee leave`() {
        val host = signIn("ada")
        val friend = signIn("bola")
        val hangout = createHangout(host)
        invite(host, hangout.id, friend).andExpect { status { isCreated() } }
        accept(friend, hangout.id)

        mockMvc.delete("/api/hangouts/${hangout.id}/leave") {
            authenticatedAs(friend)
        }.andExpect { status { isNoContent() } }

        val after = getHangout(host, hangout.id)
        assertEquals(1, after.participantCount)
        assertEquals(
            RsvpStatus.DECLINED,
            after.participants.single { it.user.userId == friend.userId }.rsvpStatus
        )
    }

    /** The host leaving would abandon everyone else's plans; cancelling says so out loud. */
    @Test
    fun `will not let the host walk out of their own hangout`() {
        val host = signIn("ada")
        val hangout = createHangout(host)

        mockMvc.delete("/api/hangouts/${hangout.id}/leave") {
            authenticatedAs(host)
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `lets the host cancel`() {
        val host = signIn("ada")
        val friend = signIn("bola")
        val hangout = createHangout(host)
        invite(host, hangout.id, friend).andExpect { status { isCreated() } }

        mockMvc.delete("/api/hangouts/${hangout.id}") {
            authenticatedAs(host)
        }.andExpect { status { isNoContent() } }

        assertEquals(HangoutStatus.CANCELLED, getHangout(host, hangout.id).status)
    }

    @Test
    fun `lets nobody but the host cancel`() {
        val host = signIn("ada")
        val friend = signIn("bola")
        val hangout = createHangout(host)
        invite(host, hangout.id, friend).andExpect { status { isCreated() } }
        accept(friend, hangout.id)

        mockMvc.delete("/api/hangouts/${hangout.id}") {
            authenticatedAs(friend)
        }.andExpect { status { isForbidden() } }
    }

    /** Only a hangout that is actually happening can be finished. */
    @Test
    fun `refuses to complete a hangout that has not started`() {
        val host = signIn("ada")
        val hangout = createHangout(host)

        mockMvc.patch("/api/hangouts/${hangout.id}/complete") {
            authenticatedAs(host)
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `lists the hangouts somebody is part of`() {
        val host = signIn("ada")
        val friend = signIn("bola")
        val hangout = createHangout(host)
        invite(host, hangout.id, friend).andExpect { status { isCreated() } }
        accept(friend, hangout.id)

        mockMvc.get("/api/hangouts") {
            authenticatedAs(friend)
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].id") { value(hangout.id.toString()) }
        }
    }

    @Test
    fun `keeps a hangout from somebody who was never asked`() {
        val host = signIn("ada")
        val stranger = signIn("chidi")
        val hangout = createHangout(host)

        mockMvc.get("/api/hangouts/${hangout.id}") {
            authenticatedAs(stranger)
        }.andExpect { status { isNotFound() } }
    }

    private fun signIn(username: String): TestAccount = accounts.signIn(
        email = "$username@lynk.test",
        displayName = username.replaceFirstChar { it.uppercase() },
        username = username
    )

    private fun createHangout(host: TestAccount, maxAttendees: Int? = null): HangoutDto {
        val response = postHangout(host, maxAttendees = maxAttendees)
            .andExpect { status { isCreated() } }
            .andReturn().response.contentAsString

        return objectMapper.readValue(response, HangoutDto::class.java)
    }

    private fun postHangout(
        host: TestAccount,
        scheduledAt: Instant = Instant.now().plus(Duration.ofDays(3)),
        maxAttendees: Int? = null
    ): ResultActionsDsl = mockMvc.post("/api/hangouts") {
        contentType = MediaType.APPLICATION_JSON
        authenticatedAs(host)
        content = """
            {
              "name": "Sunday jollof",
              "description": "Rice and arguments",
              "vibe": "FOOD",
              "scheduledAt": "$scheduledAt",
              "maxAttendees": $maxAttendees,
              "spotId": null
            }
        """.trimIndent()
    }

    private fun getHangout(caller: TestAccount, hangoutId: HangoutId): HangoutDto {
        val response = mockMvc.get("/api/hangouts/$hangoutId") {
            authenticatedAs(caller)
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        return objectMapper.readValue(response, HangoutDto::class.java)
    }

    private fun invite(host: TestAccount, hangoutId: HangoutId, invitee: TestAccount) =
        mockMvc.post("/api/hangouts/$hangoutId/participants") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(host)
            content = """{"userId":"${invitee.userId}"}"""
        }

    private fun accept(account: TestAccount, hangoutId: HangoutId) =
        rsvp(account, hangoutId, RsvpStatus.ATTENDING)

    private fun rsvp(account: TestAccount, hangoutId: HangoutId, status: RsvpStatus) =
        mockMvc.patch("/api/hangouts/$hangoutId/rsvp") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(account)
            content = """{"rsvpStatus":"${status.name}"}"""
        }
}