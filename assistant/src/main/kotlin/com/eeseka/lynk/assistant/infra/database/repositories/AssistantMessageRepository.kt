package com.eeseka.lynk.assistant.infra.database.repositories

import com.eeseka.lynk.assistant.domain.model.AssistantMessageStatus
import com.eeseka.lynk.assistant.infra.database.entities.AssistantMessageEntity
import com.eeseka.lynk.common.domain.type.AssistantMessageId
import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface AssistantMessageRepository : JpaRepository<AssistantMessageEntity, AssistantMessageId> {

    // One person's chat in one hangout, the newest first, cursored on createdAt.
    // A question and its answer are saved together and can share a timestamp; the answer still sorts above it
    @Query("""
        SELECT m
        FROM AssistantMessageEntity m
        WHERE m.hangoutId = :hangoutId
        AND m.userId = :userId
        AND m.createdAt < :before
        ORDER BY m.createdAt DESC, m.role ASC
    """)
    fun findByHangoutIdAndUserIdAndCreatedAtBefore(
        hangoutId: HangoutId,
        userId: UserId,
        before: Instant,
        pageable: Pageable
    ): Slice<AssistantMessageEntity>

    fun countByHangoutIdAndUserId(hangoutId: HangoutId, userId: UserId): Long

    fun existsByHangoutIdAndUserIdAndStatus(
        hangoutId: HangoutId,
        userId: UserId,
        status: AssistantMessageStatus
    ): Boolean

    // Answers that never finished, because the server stopped while writing them.
    fun findByStatusAndCreatedAtBefore(status: AssistantMessageStatus, before: Instant): List<AssistantMessageEntity>

    @Modifying
    @Query("""
        DELETE FROM AssistantMessageEntity m
        WHERE m.hangoutId = :hangoutId
        AND m.userId = :userId
    """)
    fun deleteByHangoutIdAndUserId(hangoutId: HangoutId, userId: UserId)

    @Modifying
    @Query("DELETE FROM AssistantMessageEntity m WHERE m.userId = :userId")
    fun deleteByUserId(userId: UserId)

    @Modifying
    @Query("DELETE FROM AssistantMessageEntity m WHERE m.hangoutId IN :hangoutIds")
    fun deleteByHangoutIdIn(hangoutIds: Collection<HangoutId>)
}
