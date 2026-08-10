package com.eeseka.lynk.user.service

import com.eeseka.lynk.user.domain.events.ProfilePictureReplacedEvent
import com.eeseka.lynk.user.infra.storage.SupabaseUserStorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ProfilePictureCleanupListener(
    private val supabaseUserStorageService: SupabaseUserStorageService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onProfilePictureReplaced(event: ProfilePictureReplacedEvent) {
        try {
            supabaseUserStorageService.deleteFile(event.oldPhotoUrl)
        } catch (e: Exception) {
            logger.warn("Failed to delete old profile picture for user ${event.userId}", e)
        }
    }
}
