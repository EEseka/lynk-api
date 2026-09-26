package com.eeseka.lynk.hangout.service

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.HangoutPhotoId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.HangoutConstants.MAX_PHOTOS_PER_UPLOADER
import com.eeseka.lynk.hangout.domain.exception.HangoutAccessDeniedException
import com.eeseka.lynk.hangout.domain.exception.HangoutIllegalStateException
import com.eeseka.lynk.hangout.domain.exception.HangoutNotFoundException
import com.eeseka.lynk.hangout.domain.exception.HangoutPhotoLimitReachedException
import com.eeseka.lynk.hangout.domain.exception.HangoutPhotoNotFoundException
import com.eeseka.lynk.hangout.domain.model.HangoutPhoto
import com.eeseka.lynk.hangout.domain.model.HangoutPhotoStatus
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.infra.database.entities.HangoutPhotoEntity
import com.eeseka.lynk.hangout.infra.database.mappers.toHangoutPhoto
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutPhotoRepository
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class HangoutPhotoService(
    private val hangoutRepository: HangoutRepository,
    private val hangoutPhotoRepository: HangoutPhotoRepository
) {

    // Two requests at once queue on the hangout lock, so the second one counts the first one's
    // PENDING rows and cannot take the uploader past the cap.
    @Transactional
    fun reservePhotos(userId: UserId, hangoutId: HangoutId, count: Int): List<HangoutPhotoId> {
        hangoutRepository.lockById(hangoutId)

        val hangout = hangoutRepository.findHangoutById(hangoutId, userId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        val uploader = hangout.participants
            .firstOrNull { it.hangoutUser.userId == userId && it.rsvpStatus == RsvpStatus.ATTENDING }
            ?.hangoutUser
            ?: throw HangoutAccessDeniedException("Only people who went can add photos.")

        if (hangout.status != HangoutStatus.COMPLETED) {
            throw HangoutIllegalStateException("Photos can be added once the hangout is completed.")
        }

        val alreadyAdded = hangoutPhotoRepository.countByHangoutIdAndUploaderUserId(hangoutId, userId)
        if (alreadyAdded + count > MAX_PHOTOS_PER_UPLOADER) {
            throw HangoutPhotoLimitReachedException(
                "You can add up to $MAX_PHOTOS_PER_UPLOADER photos to a hangout; you have $alreadyAdded."
            )
        }

        return hangoutPhotoRepository.saveAll(
            List(count) {
                HangoutPhotoEntity(
                    hangoutId = hangoutId,
                    uploader = uploader
                )
            }
        ).map { it.id!! }
    }

    // Hands back slots whose upload URLs could not be created, so they stop counting toward the cap
    @Transactional
    fun releasePhotos(photoIds: Collection<HangoutPhotoId>) {
        hangoutPhotoRepository.deleteAllByIdIn(photoIds)
    }

    fun getOwnPhoto(userId: UserId, hangoutId: HangoutId, photoId: HangoutPhotoId): HangoutPhoto {
        val photo = hangoutPhotoRepository.findByIdAndHangoutId(photoId, hangoutId)?.toHangoutPhoto()
            ?: throw HangoutPhotoNotFoundException(photoId.toString())

        if (photo.uploader.userId != userId) {
            throw HangoutAccessDeniedException("Only the person who added this photo can do that.")
        }

        return photo
    }

    @Transactional
    fun markReady(photoId: HangoutPhotoId, caption: String?): Boolean {
        return hangoutPhotoRepository.markReadyByIdAndStatus(
            id = photoId,
            pendingStatus = HangoutPhotoStatus.PENDING,
            readyStatus = HangoutPhotoStatus.READY,
            caption = caption,
            confirmedAt = Instant.now()
        ) == 1
    }

    fun findPhotoStatus(photoId: HangoutPhotoId): HangoutPhotoStatus? {
        return hangoutPhotoRepository.findStatusById(photoId)
    }
}
