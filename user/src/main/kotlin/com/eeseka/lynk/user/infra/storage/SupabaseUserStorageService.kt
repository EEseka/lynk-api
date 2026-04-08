package com.eeseka.lynk.user.infra.storage

import com.eeseka.lynk.common.domain.exception.StorageException
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.user.domain.exception.InvalidProfilePictureException
import com.eeseka.lynk.user.domain.model.ProfilePictureUploadCredentials
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.time.Instant
import java.util.*

@Service
class SupabaseUserStorageService(
    @param:Value("\${supabase.url}") private val supabaseUrl: String,
    private val supabaseRestClient: RestClient,
) {
    companion object {
        private const val BUCKET_NAME = "profile_pictures"

        private val allowedMimeTypes = mapOf(
            "image/jpeg" to "jpg",
            "image/jpg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
        )
    }

    fun generateSignedUploadUrl(userId: UserId, mimeType: String): ProfilePictureUploadCredentials {
        val extension = allowedMimeTypes[mimeType]
            ?: throw InvalidProfilePictureException("Invalid mime type $mimeType")

        val fileName = "${userId}_${UUID.randomUUID()}.$extension"
        val path = "$BUCKET_NAME/$fileName"

        val publicUrl = "$supabaseUrl/storage/v1/object/public/$path"

        return ProfilePictureUploadCredentials(
            uploadUrl = createSignedUrl(
                path = path,
                expiresInSeconds = 300
            ),
            publicUrl = publicUrl,
            headers = mapOf(
                "Content-Type" to mimeType
            ),
            expiresAt = Instant.now().plusSeconds(300)
        )
    }

    fun deleteFile(url: String) {
        val path = if (url.contains("/object/public/")) {
            url.substringAfter("/object/public/")
        } else throw StorageException("Invalid file URL format")

        val deleteUrl = "/storage/v1/object/$path"

        val response = supabaseRestClient
            .delete()
            .uri(deleteUrl)
            .retrieve()
            .toBodilessEntity()

        if (response.statusCode.isError) {
            throw StorageException("Unable to delete file: ${response.statusCode.value()}")
        }
    }

    private fun createSignedUrl(path: String, expiresInSeconds: Int): String {
        val json = """
            { "expiresIn": $expiresInSeconds }
        """.trimIndent()

        val response = supabaseRestClient
            .post()
            .uri("/storage/v1/object/upload/sign/$path")
            .header("Content-Type", "application/json")
            .body(json)
            .retrieve()
            .body<SignedUploadResponse>()
            ?: throw StorageException("Failed to create signed URL")

        return "$supabaseUrl/storage/v1${response.url}"
    }

    private data class SignedUploadResponse(val url: String)
}