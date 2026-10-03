package com.eeseka.lynk.hangout.infra.storage

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.HangoutPhotoId
import com.eeseka.lynk.hangout.domain.exception.StorageException
import com.eeseka.lynk.hangout.domain.model.HangoutPhotoDownloadUrls
import com.eeseka.lynk.hangout.domain.model.HangoutPhotoUploadCredentials
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.time.Instant

@Component
class SupabaseHangoutStorageClient(
    @param:Value("\${supabase.url}") private val supabaseUrl: String,
    private val supabaseRestClient: RestClient,
) {
    companion object {
        private const val BUCKET_NAME = "hangout_photos"

        private const val FULL_FILE_NAME = "full.jpg"
        private const val THUMBNAIL_FILE_NAME = "thumb.jpg"

        // Supabase ignores expiresIn on upload URLs and always issues them for 2 hours
        private const val UPLOAD_URL_EXPIRY_SECONDS = 7200L
        private const val DOWNLOAD_URL_EXPIRY_SECONDS = 3600L
    }

    fun generateSignedUploadUrls(hangoutId: HangoutId, photoId: HangoutPhotoId): HangoutPhotoUploadCredentials {
        val folder = "$BUCKET_NAME/$hangoutId/$photoId"

        return HangoutPhotoUploadCredentials(
            photoId = photoId,
            fullUploadUrl = createSignedUrl("$folder/$FULL_FILE_NAME"),
            thumbnailUploadUrl = createSignedUrl("$folder/$THUMBNAIL_FILE_NAME"),
            headers = mapOf(
                "Content-Type" to "image/jpeg"
            ),
            expiresAt = Instant.now().plusSeconds(UPLOAD_URL_EXPIRY_SECONDS)
        )
    }

    fun hasUploadedFiles(hangoutId: HangoutId, photoId: HangoutPhotoId): Boolean {
        val json = """
            { "prefix": "$hangoutId/$photoId/" }
        """.trimIndent()

        val files = supabaseRestClient
            .post()
            .uri("/storage/v1/object/list/$BUCKET_NAME")
            .header("Content-Type", "application/json")
            .body(json)
            .retrieve()
            .body<List<ListedFile>>()
            ?: throw StorageException("Failed to list photo files")

        val names = files.map { it.name }
        return FULL_FILE_NAME in names && THUMBNAIL_FILE_NAME in names
    }

    // One call signs every file on an album page, rather than two calls per photo
    fun generateSignedDownloadUrls(
        hangoutId: HangoutId,
        photoIds: List<HangoutPhotoId>
    ): Map<HangoutPhotoId, HangoutPhotoDownloadUrls> {
        if (photoIds.isEmpty()) return emptyMap()

        val expiresAt = Instant.now().plusSeconds(DOWNLOAD_URL_EXPIRY_SECONDS)
        val body = mapOf(
            "expiresIn" to DOWNLOAD_URL_EXPIRY_SECONDS,
            "paths" to photoIds.flatMap { photoId ->
                listOf("$hangoutId/$photoId/$FULL_FILE_NAME", "$hangoutId/$photoId/$THUMBNAIL_FILE_NAME")
            }
        )

        val signedFiles = supabaseRestClient
            .post()
            .uri("/storage/v1/object/sign/$BUCKET_NAME")
            .header("Content-Type", "application/json")
            .body(body)
            .retrieve()
            .body<List<SignedDownloadResponse>>()
            ?: throw StorageException("Failed to create signed download URLs")

        // A file that is missing comes back with no URL; its photo is left out rather than half shown
        val urlsByPath = signedFiles
            .filter { it.signedURL != null }
            .associate { it.path to "$supabaseUrl/storage/v1${it.signedURL}" }

        return photoIds.mapNotNull { photoId ->
            val fullUrl = urlsByPath["$hangoutId/$photoId/$FULL_FILE_NAME"] ?: return@mapNotNull null
            val thumbnailUrl = urlsByPath["$hangoutId/$photoId/$THUMBNAIL_FILE_NAME"] ?: return@mapNotNull null
            photoId to HangoutPhotoDownloadUrls(
                fullUrl = fullUrl,
                thumbnailUrl = thumbnailUrl,
                expiresAt = expiresAt
            )
        }.toMap()
    }

    fun deleteFiles(hangoutId: HangoutId, photoId: HangoutPhotoId) {
        val body = mapOf(
            "prefixes" to listOf("$hangoutId/$photoId/$FULL_FILE_NAME", "$hangoutId/$photoId/$THUMBNAIL_FILE_NAME")
        )

        supabaseRestClient
            .method(HttpMethod.DELETE)
            .uri("/storage/v1/object/$BUCKET_NAME")
            .header("Content-Type", "application/json")
            .body(body)
            .retrieve()
            .toBodilessEntity()
    }

    private fun createSignedUrl(path: String): String {
        val response = supabaseRestClient
            .post()
            .uri("/storage/v1/object/upload/sign/$path")
            .retrieve()
            .body<SignedUploadResponse>()
            ?: throw StorageException("Failed to create signed URL")

        return "$supabaseUrl/storage/v1${response.url}"
    }

    private data class SignedUploadResponse(val url: String)

    private data class ListedFile(val name: String)

    private data class SignedDownloadResponse(val path: String, val signedURL: String?)
}
