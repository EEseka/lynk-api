package com.eeseka.lynk.hangout.service

import com.eeseka.lynk.hangout.domain.event.HangoutPhotoDeletedEvent
import com.eeseka.lynk.hangout.infra.storage.SupabaseHangoutStorageClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class HangoutPhotoCleanupListener(
    private val supabaseHangoutStorageClient: SupabaseHangoutStorageClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onHangoutPhotoDeleted(event: HangoutPhotoDeletedEvent) {
        try {
            supabaseHangoutStorageClient.deleteFiles(hangoutId = event.hangoutId, photoId = event.photoId)
        } catch (e: Exception) {
            logger.warn("Failed to delete the files of photo ${event.photoId} in hangout ${event.hangoutId}", e)
        }
    }
}
