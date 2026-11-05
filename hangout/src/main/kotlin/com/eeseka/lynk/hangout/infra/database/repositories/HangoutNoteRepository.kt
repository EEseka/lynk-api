package com.eeseka.lynk.hangout.infra.database.repositories

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.HangoutNoteId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.infra.database.entities.HangoutNoteEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface HangoutNoteRepository : JpaRepository<HangoutNoteEntity, HangoutNoteId> {

    @Query("""
        SELECT n
        FROM HangoutNoteEntity n
        JOIN FETCH n.author
        WHERE n.hangoutId = :hangoutId
        AND n.createdAt < :before
        ORDER BY n.createdAt DESC
    """)
    fun findByHangoutIdAndCreatedAtBefore(
        hangoutId: HangoutId,
        before: Instant,
        pageable: Pageable
    ): Slice<HangoutNoteEntity>

    fun countByHangoutId(hangoutId: HangoutId): Long

    fun findByIdAndHangoutId(id: HangoutNoteId, hangoutId: HangoutId): HangoutNoteEntity?

    @Modifying
    @Query("""
        UPDATE HangoutNoteEntity n
        SET n.content = :content, n.updatedAt = :updatedAt
        WHERE n.id = :id
        AND n.hangoutId = :hangoutId
    """)
    fun updateContentByIdAndHangoutId(id: HangoutNoteId, hangoutId: HangoutId, content: String, updatedAt: Instant): Int

    @Modifying
    @Query("DELETE FROM HangoutNoteEntity n WHERE n.id = :id AND n.hangoutId = :hangoutId")
    fun deleteByIdAndHangoutId(id: HangoutNoteId, hangoutId: HangoutId): Int

    @Modifying
    @Query("DELETE FROM HangoutNoteEntity n WHERE n.author.userId = :authorId")
    fun deleteAllByAuthorId(authorId: UserId)
}
