package com.eeseka.lynk.hangout

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.HangoutPhotoId
import com.eeseka.lynk.hangout.api.dto.HangoutPhotoDto
import com.eeseka.lynk.hangout.api.dto.HangoutPhotoUploadResponse
import com.eeseka.lynk.hangout.domain.exception.StorageException
import com.eeseka.lynk.hangout.domain.model.HangoutPhotoDownloadUrls
import com.eeseka.lynk.hangout.domain.model.HangoutPhotoStatus
import com.eeseka.lynk.hangout.domain.model.HangoutPhotoUploadCredentials
import com.eeseka.lynk.hangout.infra.database.entities.HangoutPhotoEntity
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutPhotoRepository
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutRepository
import com.eeseka.lynk.hangout.service.HangoutPhotoService
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.TestAccount
import com.eeseka.lynk.support.authenticatedAs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import tools.jackson.core.type.TypeReference
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class HangoutPhotoJourneyTest : IntegrationTest() {

    @Autowired
    private lateinit var hangoutPhotoRepository: HangoutPhotoRepository

    @Autowired
    private lateinit var hangoutRepository: HangoutRepository

    @Autowired
    private lateinit var hangoutPhotoService: HangoutPhotoService

    @Autowired
    private lateinit var hangoutService: HangoutService

    @BeforeEach
    fun storageAcceptsEverything() {
        given(supabaseHangoutStorageClient.generateSignedUploadUrls(any(), any())).willAnswer {
            HangoutPhotoUploadCredentials(
                photoId = it.getArgument(1),
                fullUploadUrl = "https://storage.test/full",
                thumbnailUploadUrl = "https://storage.test/thumb",
                headers = mapOf("Content-Type" to "image/jpeg"),
                expiresAt = Instant.now().plusSeconds(7200)
            )
        }
        given(supabaseHangoutStorageClient.hasUploadedFiles(any(), any())).willReturn(true)
        given(supabaseHangoutStorageClient.generateSignedDownloadUrls(any(), any())).willAnswer {
            it.getArgument<List<HangoutPhotoId>>(1).associateWith { photoId ->
                HangoutPhotoDownloadUrls(
                    fullUrl = "https://storage.test/$photoId/full",
                    thumbnailUrl = "https://storage.test/$photoId/thumb",
                    expiresAt = Instant.now().plusSeconds(3600)
                )
            }
        }
    }

    @Test
    fun `shows the people who went every finished photo, newest first`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = hangouts.completed(host, attendees = listOf(bola))
        val first = addPhoto(bola, hangoutId, caption = "Suya run")
        val second = addPhoto(host, hangoutId, caption = "Group picture")
        generateUploadUrls(bola, hangoutId, count = 1)

        val album = getPhotos(host, hangoutId)

        // The unfinished upload is left out
        assertEquals(listOf(second, first), album.map { it.id })
        assertEquals("Group picture", album.first().caption)
        assertEquals("Ada", album.first().uploader.displayName)
        assertEquals("https://storage.test/$second/thumb", album.first().thumbnailUrl)
    }

    @Test
    fun `pages through the album from where the last page ended`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = hangouts.completed(host, attendees = listOf(bola))
        val first = addPhoto(host, hangoutId)
        val second = addPhoto(host, hangoutId)

        val firstPage = getPhotos(host, hangoutId, pageSize = 1)
        val secondPage = getPhotos(host, hangoutId, pageSize = 1, before = firstPage.single().createdAt)

        assertEquals(second, firstPage.single().id)
        assertEquals(first, secondPage.single().id)
    }

    @Test
    fun `leaves out a photo whose files are missing from storage`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = hangouts.completed(host, attendees = listOf(bola))
        addPhoto(host, hangoutId)
        given(supabaseHangoutStorageClient.generateSignedDownloadUrls(any(), any())).willReturn(emptyMap())

        assertEquals(0, getPhotos(host, hangoutId).size)
    }

    @Test
    fun `keeps the album from people who did not go`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val stranger = signIn("chidi")
        val hangoutId = hangouts.completed(host, pendingInvitees = listOf(bola))

        mockMvc.get("/api/hangouts/$hangoutId/photos") { authenticatedAs(bola) }
            .andExpect { status { isForbidden() } }
        mockMvc.get("/api/hangouts/$hangoutId/photos") { authenticatedAs(stranger) }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `lets the uploader change a caption`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = hangouts.completed(host, attendees = listOf(bola))
        val photoId = addPhoto(host, hangoutId, caption = "Before")

        updateCaption(host, hangoutId, photoId, caption = "  After  ").andExpect { status { isNoContent() } }
        assertEquals("After", hangoutPhotoRepository.findById(photoId).get().caption)

        updateCaption(host, hangoutId, photoId, caption = "   ").andExpect { status { isNoContent() } }
        assertEquals(null, hangoutPhotoRepository.findById(photoId).get().caption)
    }

    @Test
    fun `only the uploader can change a caption, and only once the photo is finished`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = hangouts.completed(host, attendees = listOf(bola))
        val photoId = addPhoto(bola, hangoutId)
        val unfinished = generateUploadUrls(bola, hangoutId, count = 1).single().photoId

        updateCaption(host, hangoutId, photoId, caption = "Mine now").andExpect { status { isForbidden() } }
        updateCaption(bola, hangoutId, unfinished, caption = "Too soon").andExpect { status { isConflict() } }
        updateCaption(bola, hangoutId, photoId, caption = "a".repeat(201)).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `lets the uploader remove their photo and then removes its files`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = hangouts.completed(host, attendees = listOf(bola))
        val photoId = addPhoto(host, hangoutId)

        deletePhoto(host, hangoutId, photoId).andExpect { status { isNoContent() } }

        assertEquals(false, hangoutPhotoRepository.existsById(photoId))
        verify(supabaseHangoutStorageClient).deleteFiles(hangoutId, photoId)
    }

    @Test
    fun `lets the host remove anybody's photo but nobody else`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val chidi = signIn("chidi")
        val hangoutId = hangouts.completed(host, attendees = listOf(bola, chidi))
        val bolasPhoto = addPhoto(bola, hangoutId)

        deletePhoto(chidi, hangoutId, bolasPhoto).andExpect { status { isForbidden() } }
        deletePhoto(host, hangoutId, bolasPhoto).andExpect { status { isNoContent() } }
        deletePhoto(host, hangoutId, bolasPhoto).andExpect { status { isNotFound() } }
    }

    @Test
    fun `still removes the photo when storage fails to remove its files`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = hangouts.completed(host, attendees = listOf(bola))
        val photoId = addPhoto(host, hangoutId)
        willThrow(StorageException("down")).given(supabaseHangoutStorageClient).deleteFiles(any(), any())

        // The files are cleaned up after the delete is committed, so a storage failure only leaves them behind
        deletePhoto(host, hangoutId, photoId).andExpect { status { isNoContent() } }
        assertEquals(false, hangoutPhotoRepository.existsById(photoId))
    }

    @Test
    fun `hands out upload urls for each photo and adds it to the album once confirmed`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = hangouts.completed(host, attendees = listOf(bola))

        val uploads = generateUploadUrls(bola, hangoutId, count = 2)
        assertEquals(2, uploads.map { it.photoId }.distinct().size)
        assertEquals("https://storage.test/full", uploads.first().fullUploadUrl)

        confirm(bola, hangoutId, uploads.first().photoId, caption = "  Ada having the time of her life  ")
            .andExpect { status { isNoContent() } }

        val photo = hangoutPhotoRepository.findById(uploads.first().photoId).get()
        assertEquals(HangoutPhotoStatus.READY, photo.status)
        assertEquals("Ada having the time of her life", photo.caption)
        assertEquals(HangoutPhotoStatus.PENDING, hangoutPhotoRepository.findById(uploads.last().photoId).get().status)
    }

    @Test
    fun `lets the host add photos too`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = hangouts.completed(host, attendees = listOf(bola))

        postGenerateUploadUrls(host, hangoutId, count = 1).andExpect { status { isCreated() } }
    }

    @Test
    fun `refuses somebody who never accepted the invite`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = hangouts.completed(host, pendingInvitees = listOf(bola))

        postGenerateUploadUrls(bola, hangoutId, count = 1).andExpect { status { isForbidden() } }
    }

    @Test
    fun `refuses photos before the hangout is completed`() {
        val host = signIn("ada")
        val hangoutId = hangouts.scheduled(host).id

        postGenerateUploadUrls(host, hangoutId, count = 1).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("HANGOUT_ILLEGAL_STATE") }
        }
    }

    @Test
    fun `stops each person at twenty photos`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = hangouts.completed(host, attendees = listOf(bola))

        generateUploadUrls(bola, hangoutId, count = 10)
        generateUploadUrls(bola, hangoutId, count = 10)

        postGenerateUploadUrls(bola, hangoutId, count = 1).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("HANGOUT_PHOTO_LIMIT_REACHED") }
        }

        // The cap is per person, so Bola filling theirs leaves the host's untouched
        postGenerateUploadUrls(host, hangoutId, count = 10).andExpect { status { isCreated() } }
    }

    @Test
    fun `refuses more than ten photos in one request`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = hangouts.completed(host, attendees = listOf(bola))

        postGenerateUploadUrls(host, hangoutId, count = 11).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `gives the photos back when storage cannot create upload urls`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = hangouts.completed(host, attendees = listOf(bola))
        given(supabaseHangoutStorageClient.generateSignedUploadUrls(any(), any()))
            .willThrow(StorageException("Failed to create signed URL"))

        postGenerateUploadUrls(host, hangoutId, count = 3).andExpect {
            status { isInternalServerError() }
            jsonPath("$.code") { value("STORAGE_ERROR") }
        }

        assertEquals(0, hangoutPhotoRepository.count())
    }

    @Test
    fun `will not confirm a photo whose files are not all in storage`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = hangouts.completed(host, attendees = listOf(bola))
        val upload = generateUploadUrls(host, hangoutId, count = 1).single()
        given(supabaseHangoutStorageClient.hasUploadedFiles(any(), any())).willReturn(false)

        confirm(host, hangoutId, upload.photoId).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("HANGOUT_ILLEGAL_STATE") }
        }

        assertEquals(HangoutPhotoStatus.PENDING, hangoutPhotoRepository.findById(upload.photoId).get().status)
    }

    @Test
    fun `confirming the same photo twice is harmless`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = hangouts.completed(host, attendees = listOf(bola))
        val upload = generateUploadUrls(host, hangoutId, count = 1).single()

        confirm(host, hangoutId, upload.photoId, caption = "First").andExpect { status { isNoContent() } }
        confirm(host, hangoutId, upload.photoId, caption = "Second").andExpect { status { isNoContent() } }

        assertEquals("First", hangoutPhotoRepository.findById(upload.photoId).get().caption)
    }

    @Test
    fun `only the person who added a photo can confirm it`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = hangouts.completed(host, attendees = listOf(bola))
        val upload = generateUploadUrls(bola, hangoutId, count = 1).single()

        confirm(host, hangoutId, upload.photoId).andExpect { status { isForbidden() } }
    }

    @Test
    fun `does not find a photo through another hangout`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = hangouts.completed(host, attendees = listOf(bola))
        val otherHangoutId = hangouts.completed(host, attendees = listOf(bola))
        val upload = generateUploadUrls(host, hangoutId, count = 1).single()

        confirm(host, otherHangoutId, upload.photoId).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("HANGOUT_PHOTO_NOT_FOUND") }
        }
        confirm(host, hangoutId, UUID.randomUUID()).andExpect { status { isNotFound() } }
    }

    @Test
    fun `refuses a caption longer than two hundred characters`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = hangouts.completed(host, attendees = listOf(bola))
        val upload = generateUploadUrls(host, hangoutId, count = 1).single()

        confirm(host, hangoutId, upload.photoId, caption = "a".repeat(201))
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `clears uploads nobody finished within two hours, and their files`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = hangouts.completed(host, attendees = listOf(bola))
        val (abandoned, recent) = generateUploadUrls(host, hangoutId, count = 2).map { it.photoId }
        val finished = addPhoto(host, hangoutId)
        fixtures.agePhoto(abandoned, Instant.now().minus(Duration.ofHours(3)))
        fixtures.agePhoto(recent, Instant.now().minus(Duration.ofMinutes(90)))
        fixtures.agePhoto(finished, Instant.now().minus(Duration.ofHours(3)))

        hangoutPhotoService.deleteAbandonedUploads()

        assertEquals(false, hangoutPhotoRepository.existsById(abandoned))
        assertEquals(true, hangoutPhotoRepository.existsById(recent))
        assertEquals(true, hangoutPhotoRepository.existsById(finished))
        verify(supabaseHangoutStorageClient).deleteFiles(hangoutId, abandoned)
        verify(supabaseHangoutStorageClient, never()).deleteFiles(hangoutId, finished)
    }

    @Test
    fun `takes a person's photos with them when they delete their account`() {
        val host = signIn("ada")
        val bola = signIn("bola")
        val hangoutId = hangouts.completed(host, attendees = listOf(bola))
        val bolasPhoto = addPhoto(bola, hangoutId)
        val hostsPhoto = addPhoto(host, hangoutId)

        mockMvc.delete("/api/auth/account") { authenticatedAs(bola) }
            .andExpect { status { isOk() } }
        broker.deliverEvents()

        assertEquals(false, hangoutPhotoRepository.existsById(bolasPhoto))
        assertEquals(true, hangoutPhotoRepository.existsById(hostsPhoto))
        verify(supabaseHangoutStorageClient).deleteFiles(hangoutId, bolasPhoto)
    }

    @Test
    fun `refuses photos on a hangout nobody else went to`() {
        val host = signIn("ada")
        val hangoutId = hangouts.completed(host)

        postGenerateUploadUrls(host, hangoutId, count = 1).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("HANGOUT_ILLEGAL_STATE") }
        }
    }

    @Test
    fun `keeps a solo hangout that has photos rather than failing the whole cleanup`() {
        val host = signIn("ada")
        val hangoutId = hangouts.completed(host)
        // The app refuses photos on a solo hangout, so the row goes in directly
        hangoutPhotoRepository.save(HangoutPhotoEntity(hangoutId = hangoutId, uploader = fixtures.user()))
        fixtures.moveScheduledAt(hangoutId, Instant.now().minus(Duration.ofDays(40)))

        hangoutService.cleanupSoloUnpaidHangouts()

        assertEquals(true, hangoutRepository.existsById(hangoutId))
    }

    /** A finished photo, the way the app adds one: ask for upload URLs, then confirm. */
    private fun addPhoto(account: TestAccount, hangoutId: HangoutId, caption: String? = null): HangoutPhotoId {
        val photoId = generateUploadUrls(account, hangoutId, count = 1).single().photoId
        confirm(account, hangoutId, photoId, caption).andExpect { status { isNoContent() } }
        return photoId
    }

    private fun getPhotos(
        account: TestAccount,
        hangoutId: HangoutId,
        pageSize: Int? = null,
        before: Instant? = null
    ): List<HangoutPhotoDto> {
        val response = mockMvc.get("/api/hangouts/$hangoutId/photos") {
            authenticatedAs(account)
            pageSize?.let { param("pageSize", it.toString()) }
            before?.let { param("before", it.toString()) }
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        return objectMapper.readValue(response, object : TypeReference<List<HangoutPhotoDto>>() {})
    }

    private fun updateCaption(
        account: TestAccount,
        hangoutId: HangoutId,
        photoId: HangoutPhotoId,
        caption: String?
    ): ResultActionsDsl =
        mockMvc.patch("/api/hangouts/$hangoutId/photos/$photoId") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(account)
            content = objectMapper.writeValueAsString(mapOf("caption" to caption))
        }

    private fun deletePhoto(account: TestAccount, hangoutId: HangoutId, photoId: HangoutPhotoId): ResultActionsDsl =
        mockMvc.delete("/api/hangouts/$hangoutId/photos/$photoId") {
            authenticatedAs(account)
        }

    private fun signIn(name: String): TestAccount =
        accounts.signIn(email = "$name@lynk.test", displayName = name.replaceFirstChar { it.uppercase() }, username = name)

    private fun postGenerateUploadUrls(account: TestAccount, hangoutId: HangoutId, count: Int): ResultActionsDsl =
        mockMvc.post("/api/hangouts/$hangoutId/photos/generate-upload-urls") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(account)
            content = """{"count":$count}"""
        }

    private fun generateUploadUrls(account: TestAccount, hangoutId: HangoutId, count: Int): List<HangoutPhotoUploadResponse> {
        val response = postGenerateUploadUrls(account, hangoutId, count)
            .andExpect { status { isCreated() } }
            .andReturn().response.contentAsString

        return objectMapper.readValue(response, object : TypeReference<List<HangoutPhotoUploadResponse>>() {})
    }

    private fun confirm(
        account: TestAccount,
        hangoutId: HangoutId,
        photoId: HangoutPhotoId,
        caption: String? = null
    ): ResultActionsDsl =
        mockMvc.post("/api/hangouts/$hangoutId/photos/$photoId/confirm") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(account)
            content = objectMapper.writeValueAsString(mapOf("caption" to caption))
        }
}
