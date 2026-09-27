package com.eeseka.lynk.hangout.service

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.HangoutPhotoId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.exception.HangoutIllegalStateException
import com.eeseka.lynk.hangout.domain.model.HangoutPhotoStatus
import com.eeseka.lynk.hangout.domain.model.HangoutPhotoUploadCredentials
import com.eeseka.lynk.hangout.infra.storage.SupabaseHangoutStorageClient
import org.springframework.stereotype.Service

@Service
class HangoutPhotoUploadService(
    private val hangoutPhotoService: HangoutPhotoService,
    private val supabaseHangoutStorageClient: SupabaseHangoutStorageClient
) {

    fun generateUploadCredentials(
        userId: UserId,
        hangoutId: HangoutId,
        count: Int
    ): List<HangoutPhotoUploadCredentials> {
        val photoIds = hangoutPhotoService.reservePhotos(userId = userId, hangoutId = hangoutId, count = count)

        return try {
            photoIds.map { photoId ->
                supabaseHangoutStorageClient.generateSignedUploadUrls(hangoutId = hangoutId, photoId = photoId)
            }
        } catch (e: Exception) {
            hangoutPhotoService.releasePhotos(photoIds)
            throw e
        }
    }

    fun confirmUpload(userId: UserId, hangoutId: HangoutId, photoId: HangoutPhotoId, caption: String?) {
        val photo = hangoutPhotoService.getOwnPhoto(userId = userId, hangoutId = hangoutId, photoId = photoId)
        if (photo.status == HangoutPhotoStatus.READY) return

        val hasUploadedFiles = supabaseHangoutStorageClient.hasUploadedFiles(hangoutId = hangoutId, photoId = photoId)
        if (!hasUploadedFiles) {
            throw HangoutIllegalStateException("The photo has not finished uploading.")
        }

        val cleanCaption = caption?.trim()?.takeIf { it.isNotEmpty() }
        val wasMarkedReady = hangoutPhotoService.markPhotoStatusReady(photoId = photoId, caption = cleanCaption)
        if (wasMarkedReady) return

        // Nothing changed: either another confirm got there first, or the upload expired and was swept
        val currentStatus = hangoutPhotoService.findPhotoStatus(photoId)
        if (currentStatus != HangoutPhotoStatus.READY) {
            throw HangoutIllegalStateException("This upload expired. Please add the photo again.")
        }
    }
}
