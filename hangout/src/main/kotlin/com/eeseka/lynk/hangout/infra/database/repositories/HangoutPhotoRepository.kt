package com.eeseka.lynk.hangout.infra.database.repositories

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.HangoutPhotoId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.model.HangoutPhotoStatus
import com.eeseka.lynk.hangout.infra.database.entities.HangoutPhotoEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface HangoutPhotoRepository : JpaRepository<HangoutPhotoEntity, HangoutPhotoId> {

    @Query("""
        SELECT p
        FROM HangoutPhotoEntity p
        JOIN FETCH p.uploader
        WHERE p.id = :id
        AND p.hangoutId = :hangoutId
    """)
    fun findByIdAndHangoutId(id: HangoutPhotoId, hangoutId: HangoutId): HangoutPhotoEntity?

    @Modifying
    @Query("DELETE FROM HangoutPhotoEntity p WHERE p.id IN :ids")
    fun deleteAllByIdIn(ids: Collection<HangoutPhotoId>)

    @Query("SELECT p.status FROM HangoutPhotoEntity p WHERE p.id = :id")
    fun findStatusById(id: HangoutPhotoId): HangoutPhotoStatus?

    // The per-person cap: PENDING rows count too, since each one holds a slot
    fun countByHangoutIdAndUploaderUserId(hangoutId: HangoutId, uploaderId: UserId): Int

    // Only a PENDING row turns READY, so a second confirmation, or one that lost to the sweep, changes 0 rows
    @Modifying
    @Query("""
        UPDATE HangoutPhotoEntity p
        SET p.status = :readyStatus, p.caption = :caption, p.confirmedAt = :confirmedAt
        WHERE p.id = :id
        AND p.status = :pendingStatus
    """)
    fun markReadyByIdAndStatus(
        id: HangoutPhotoId,
        pendingStatus: HangoutPhotoStatus,
        readyStatus: HangoutPhotoStatus,
        caption: String?,
        confirmedAt: Instant
    ): Int
}
