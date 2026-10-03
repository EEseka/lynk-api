package com.eeseka.lynk.hangout.service

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.HangoutPhotoId
import com.eeseka.lynk.common.domain.events.hangout.HangoutEvent
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.common.infra.message_queue.EventPublisher
import com.eeseka.lynk.hangout.domain.HangoutConstants.MAX_PHOTOS_PER_UPLOADER
import com.eeseka.lynk.hangout.domain.HangoutConstants.MIN_ATTENDEES_FOR_PHOTOS
import com.eeseka.lynk.hangout.domain.event.HangoutPhotoDeletedEvent
import com.eeseka.lynk.hangout.domain.event.HangoutPhotosAddedEvent
import com.eeseka.lynk.hangout.domain.exception.HangoutAccessDeniedException
import com.eeseka.lynk.hangout.domain.exception.HangoutIllegalStateException
import com.eeseka.lynk.hangout.domain.exception.HangoutNotFoundException
import com.eeseka.lynk.hangout.domain.exception.HangoutPhotoLimitReachedException
import com.eeseka.lynk.hangout.domain.exception.HangoutPhotoNotFoundException
import com.eeseka.lynk.hangout.domain.model.HangoutPhoto
import com.eeseka.lynk.hangout.domain.model.HangoutPhotoDownloadUrls
import com.eeseka.lynk.hangout.domain.model.HangoutPhotoStats
import com.eeseka.lynk.hangout.domain.model.HangoutPhotoStatus
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.infra.database.entities.HangoutPhotoEntity
import com.eeseka.lynk.hangout.infra.database.mappers.toHangoutPhoto
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutParticipantRepository
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutPhotoRepository
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutRepository
import com.eeseka.lynk.hangout.infra.storage.SupabaseHangoutStorageClient
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class HangoutPhotoService(
    private val hangoutRepository: HangoutRepository,
    private val hangoutPhotoRepository: HangoutPhotoRepository,
    private val hangoutParticipantRepository: HangoutParticipantRepository,
    private val supabaseHangoutStorageClient: SupabaseHangoutStorageClient,
    private val eventPublisher: EventPublisher,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // Racing requests queue on the hangout lock, so the second counts the first one's PENDING rows
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

        val attendingCount = hangout.participants.count { it.rsvpStatus == RsvpStatus.ATTENDING }
        if (attendingCount < MIN_ATTENDEES_FOR_PHOTOS) {
            throw HangoutIllegalStateException("Photos are for hangouts someone else went to as well.")
        }

        val alreadyAddedPhotos = hangoutPhotoRepository.countByHangoutIdAndUploaderUserId(hangoutId, userId)
        if (alreadyAddedPhotos + count > MAX_PHOTOS_PER_UPLOADER) {
            throw HangoutPhotoLimitReachedException(
                "You can add up to $MAX_PHOTOS_PER_UPLOADER photos to a hangout; you have $alreadyAddedPhotos."
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
    fun releasePhotos(photoIds: List<HangoutPhotoId>) {
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
    fun markPhotoStatusReady(photoId: HangoutPhotoId): Boolean {
        val updatedRows = hangoutPhotoRepository.markReadyByIdAndStatus(
            id = photoId,
            pendingStatus = HangoutPhotoStatus.PENDING,
            readyStatus = HangoutPhotoStatus.READY,
            confirmedAt = Instant.now()
        )
        return updatedRows == 1
    }

    fun findPhotoStatus(photoId: HangoutPhotoId): HangoutPhotoStatus? {
        return hangoutPhotoRepository.findStatusById(photoId)
    }

    fun getPhotos(
        userId: UserId,
        hangoutId: HangoutId,
        before: Instant?,
        pageSize: Int
    ): List<Pair<HangoutPhoto, HangoutPhotoDownloadUrls>> {
        val rsvpStatus = hangoutParticipantRepository.findRsvpStatusByHangoutIdAndUserId(hangoutId, userId)
            ?: throw HangoutNotFoundException(hangoutId.toString())
        if (rsvpStatus != RsvpStatus.ATTENDING) {
            throw HangoutAccessDeniedException("Only people who went can see the photos.")
        }

        val photos = hangoutPhotoRepository.findByHangoutIdAndStatusAndCreatedAtBefore(
            hangoutId = hangoutId,
            status = HangoutPhotoStatus.READY,
            before = before ?: Instant.now(),
            pageable = PageRequest.of(0, pageSize)
        ).content.map { it.toHangoutPhoto() }

        val downloadUrls = supabaseHangoutStorageClient.generateSignedDownloadUrls(
            hangoutId = hangoutId,
            photoIds = photos.map { it.id }
        )

        return photos.mapNotNull { photo ->
            downloadUrls[photo.id]?.let { urls -> photo to urls }
        }
    }

    fun getPhotoStats(userId: UserId, hangoutId: HangoutId): HangoutPhotoStats {
        val rsvpStatus = hangoutParticipantRepository.findRsvpStatusByHangoutIdAndUserId(hangoutId, userId)
            ?: throw HangoutNotFoundException(hangoutId.toString())
        if (rsvpStatus != RsvpStatus.ATTENDING) {
            throw HangoutAccessDeniedException("Only people who went can see the photos.")
        }

        return HangoutPhotoStats(
            photoCount = hangoutPhotoRepository.countByHangoutIdAndStatus(
                hangoutId = hangoutId,
                status = HangoutPhotoStatus.READY
            ),
            myPhotoCount = hangoutPhotoRepository.countByHangoutIdAndUploaderUserId(
                hangoutId = hangoutId,
                uploaderId = userId
            )
        )
    }

    @Transactional
    fun updateCaption(userId: UserId, hangoutId: HangoutId, photoId: HangoutPhotoId, caption: String?) {
        val photo = hangoutPhotoRepository.findByIdAndHangoutId(photoId, hangoutId)
            ?: throw HangoutPhotoNotFoundException(photoId.toString())

        if (photo.uploader.userId != userId) {
            throw HangoutAccessDeniedException("Only the person who added this photo can change its caption.")
        }
        if (photo.status != HangoutPhotoStatus.READY) {
            throw HangoutIllegalStateException("The photo has not finished uploading.")
        }

        val cleanCaption = caption?.trim()?.takeIf { it.isNotEmpty() }
        val updatedRows = hangoutPhotoRepository.updateCaptionById(photoId, cleanCaption)
        if (updatedRows == 0) {
            throw HangoutPhotoNotFoundException(photoId.toString())
        }
    }

    // The uploader removes their own photo; the host can remove anyone's
    @Transactional
    fun deletePhoto(userId: UserId, hangoutId: HangoutId, photoId: HangoutPhotoId) {
        val photo = hangoutPhotoRepository.findByIdAndHangoutId(photoId, hangoutId)
            ?: throw HangoutPhotoNotFoundException(photoId.toString())

        val isUploader = photo.uploader.userId == userId
        val isHost = hangoutRepository.findHostIdById(hangoutId) == userId
        if (!isUploader && !isHost) {
            throw HangoutAccessDeniedException("Only the person who added this photo or the host can remove it.")
        }

        val deletedRows = hangoutPhotoRepository.deleteByIdAndHangoutId(photoId, hangoutId)
        if (deletedRows == 0) {
            throw HangoutPhotoNotFoundException(photoId.toString())
        }

        applicationEventPublisher.publishEvent(
            HangoutPhotoDeletedEvent(
                hangoutId = hangoutId,
                photoId = photoId
            )
        )
    }

    // An account going takes its photos with it
    @Transactional
    fun deleteAllPhotosOfUploader(userId: UserId) {
        val photos = hangoutPhotoRepository.findAllByUploaderUserId(userId)
        if (photos.isEmpty()) return

        deletePhotosAndFiles(photos)

        logger.info("Deleted {} photos added by deleted user {}", photos.size, userId)
    }

    @Scheduled(fixedDelay = 15 * 60 * 1000)
    @Transactional
    fun deleteAbandonedUploads() {
        val cutoff = Instant.now().minus(2, ChronoUnit.HOURS)
        val abandoned = hangoutPhotoRepository.lockAllByStatusAndCreatedAtBefore(
            status = HangoutPhotoStatus.PENDING,
            cutoff = cutoff
        )
        if (abandoned.isEmpty()) return

        deletePhotosAndFiles(abandoned)

        logger.info("Deleted {} photo uploads that were never finished", abandoned.size)
    }

    // One push per person per album, however many photos they've added since the last run
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    @Transactional
    fun announceNewPhotos() {
        val unannounced = hangoutPhotoRepository.findByStatusAndAnnouncedAtIsNull(HangoutPhotoStatus.READY)
        if (unannounced.isEmpty()) return

        hangoutPhotoRepository.markAnnounced(
            ids = unannounced.map { it.id!! },
            announcedAt = Instant.now()
        )

        val hangoutsById = hangoutRepository
            .findAllById(unannounced.map { it.hangoutId }.toSet())
            .associateBy { it.id!! }

        unannounced.groupBy { it.hangoutId }.forEach { (hangoutId, photos) ->
            val hangout = hangoutsById[hangoutId] ?: return@forEach
            val attendeeIds = hangout.participants
                .filter { it.rsvpStatus == RsvpStatus.ATTENDING }
                .map { it.hangoutUser.userId }
                .toSet()

            photos.groupBy { it.uploader.userId }.forEach { (uploaderId, uploaderPhotos) ->
                // Push: the others went too, and this is their album
                eventPublisher.publish(
                    HangoutEvent.PhotosAdded(
                        hangoutId = hangoutId,
                        hangoutName = hangout.name,
                        recipientIds = attendeeIds - uploaderId,
                        uploaderDisplayName = uploaderPhotos.first().uploader.displayName,
                        photoCount = uploaderPhotos.size
                    )
                )
            }

            // Refresh the live lobby
            applicationEventPublisher.publishEvent(
                HangoutPhotosAddedEvent(
                    hangoutId = hangoutId,
                    uploaderIds = photos.map { it.uploader.userId }.toSet()
                )
            )
        }

        logger.info("Announced {} new photos", unannounced.size)
    }

    private fun deletePhotosAndFiles(photos: List<HangoutPhotoEntity>) {
        hangoutPhotoRepository.deleteAllByIdIn(photos.map { it.id!! })

        photos.forEach { photo ->
            applicationEventPublisher.publishEvent(
                HangoutPhotoDeletedEvent(
                    hangoutId = photo.hangoutId,
                    photoId = photo.id!!
                )
            )
        }
    }
}
