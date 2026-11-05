package com.eeseka.lynk.hangout.service

import com.eeseka.lynk.common.domain.events.hangout.HangoutEvent
import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.HangoutNoteId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.common.infra.message_queue.EventPublisher
import com.eeseka.lynk.hangout.domain.HangoutConstants.MIN_ATTENDEES_FOR_GROUP
import com.eeseka.lynk.hangout.domain.HangoutConstants.STARTING_SOON_MINUTES
import com.eeseka.lynk.hangout.domain.event.HangoutNotesChangedEvent
import com.eeseka.lynk.hangout.domain.exception.HangoutAccessDeniedException
import com.eeseka.lynk.hangout.domain.exception.HangoutIllegalStateException
import com.eeseka.lynk.hangout.domain.exception.HangoutNotFoundException
import com.eeseka.lynk.hangout.domain.exception.HangoutNoteNotFoundException
import com.eeseka.lynk.hangout.domain.model.HangoutNote
import com.eeseka.lynk.hangout.domain.model.HangoutNoteAudience
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.infra.database.entities.HangoutEntity
import com.eeseka.lynk.hangout.infra.database.entities.HangoutNoteEntity
import com.eeseka.lynk.hangout.infra.database.mappers.toHangoutNote
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutNoteRepository
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutParticipantRepository
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class HangoutNoteService(
    private val hangoutRepository: HangoutRepository,
    private val hangoutNoteRepository: HangoutNoteRepository,
    private val hangoutParticipantRepository: HangoutParticipantRepository,
    private val eventPublisher: EventPublisher,
    private val applicationEventPublisher: ApplicationEventPublisher
) {

    fun getNotes(
        userId: UserId,
        hangoutId: HangoutId,
        before: Instant?,
        pageSize: Int
    ): List<HangoutNote> {
        val rsvpStatus = hangoutParticipantRepository.findRsvpStatusByHangoutIdAndUserId(hangoutId, userId)
            ?: throw HangoutNotFoundException(hangoutId.toString())
        if (rsvpStatus != RsvpStatus.ATTENDING) {
            throw HangoutAccessDeniedException("Only people going can see the notes.")
        }

        return hangoutNoteRepository.findByHangoutIdAndCreatedAtBefore(
            hangoutId = hangoutId,
            before = before ?: Instant.now(),
            pageable = PageRequest.of(0, pageSize)
        ).content.map { it.toHangoutNote() }
    }

    fun getNoteCount(userId: UserId, hangoutId: HangoutId): Long {
        val rsvpStatus = hangoutParticipantRepository.findRsvpStatusByHangoutIdAndUserId(hangoutId, userId)
            ?: throw HangoutNotFoundException(hangoutId.toString())
        if (rsvpStatus != RsvpStatus.ATTENDING) {
            throw HangoutAccessDeniedException("Only people going can see the notes.")
        }

        return hangoutNoteRepository.countByHangoutId(hangoutId)
    }

    @Transactional
    fun addNote(
        userId: UserId,
        hangoutId: HangoutId,
        content: String,
        audience: HangoutNoteAudience
    ): HangoutNote {
        val hangout = hangoutRepository.findHangoutById(hangoutId, userId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        val author = hangout.participants
            .firstOrNull { it.hangoutUser.userId == userId && it.rsvpStatus == RsvpStatus.ATTENDING }
            ?.hangoutUser
            ?: throw HangoutAccessDeniedException("Only people going can leave notes.")

        assertNotesOpen(hangout)
        if (hangout.participantCount < MIN_ATTENDEES_FOR_GROUP) {
            throw HangoutIllegalStateException("Nobody else is going to this hangout.")
        }

        val note = hangoutNoteRepository.save(
            HangoutNoteEntity(
                hangoutId = hangoutId,
                author = author,
                content = content.trim()
            )
        ).toHangoutNote()

        val attendeeIds = hangout.participants
            .filter { it.rsvpStatus == RsvpStatus.ATTENDING }
            .map { it.hangoutUser.userId }
            .toSet()
        // The host's notes are for everyone; anyone else can tell just the host
        val recipientIds = if (userId == hangout.hostId || audience == HangoutNoteAudience.EVERYONE) {
            attendeeIds - userId
        } else {
            setOf(hangout.hostId)
        }

        eventPublisher.publish(
            HangoutEvent.NoteAdded(
                hangoutId = hangoutId,
                hangoutName = hangout.name,
                recipientIds = recipientIds,
                authorDisplayName = author.displayName,
                content = note.content
            )
        )

        applicationEventPublisher.publishEvent(
            HangoutNotesChangedEvent(hangoutId = hangoutId)
        )

        return note
    }

    @Transactional
    fun editNote(
        userId: UserId,
        hangoutId: HangoutId,
        noteId: HangoutNoteId,
        content: String
    ) {
        val hangout = hangoutRepository.findHangoutById(hangoutId, userId)
            ?: throw HangoutNotFoundException(hangoutId.toString())

        assertNotesOpen(hangout)
        if (hangout.participantCount < MIN_ATTENDEES_FOR_GROUP) {
            throw HangoutIllegalStateException("Nobody else is going to this hangout.")
        }

        val note = hangoutNoteRepository.findByIdAndHangoutId(noteId, hangoutId)
            ?: throw HangoutNoteNotFoundException(noteId.toString())

        if (note.author.userId != userId) {
            throw HangoutAccessDeniedException("Only the person who wrote this note can change it.")
        }

        val updatedRows = hangoutNoteRepository.updateContentByIdAndHangoutId(
            id = noteId,
            hangoutId = hangoutId,
            content = content.trim(),
            updatedAt = Instant.now()
        )
        if (updatedRows == 0) {
            throw HangoutNoteNotFoundException(noteId.toString())
        }

        applicationEventPublisher.publishEvent(
            HangoutNotesChangedEvent(hangoutId = hangoutId)
        )
    }

    @Transactional
    fun deleteNote(userId: UserId, hangoutId: HangoutId, noteId: HangoutNoteId) {
        val note = hangoutNoteRepository.findByIdAndHangoutId(noteId, hangoutId)
            ?: throw HangoutNoteNotFoundException(noteId.toString())

        val isAuthor = note.author.userId == userId
        val isHost = hangoutRepository.findHostIdById(hangoutId) == userId
        if (!isAuthor && !isHost) {
            throw HangoutAccessDeniedException("Only the person who wrote this note or the host can delete it.")
        }

        val deletedRows = hangoutNoteRepository.deleteByIdAndHangoutId(noteId, hangoutId)
        if (deletedRows == 0) {
            throw HangoutNoteNotFoundException(noteId.toString())
        }

        applicationEventPublisher.publishEvent(
            HangoutNotesChangedEvent(hangoutId = hangoutId)
        )
    }

    @Transactional
    fun deleteAllNotesOfAuthor(userId: UserId) {
        hangoutNoteRepository.deleteAllByAuthorId(userId)
    }

    private fun assertNotesOpen(hangout: HangoutEntity) {
        val isUnderWay = hangout.status == HangoutStatus.ONGOING
        val isStartingSoon = hangout.status == HangoutStatus.SCHEDULED &&
                Instant.now().isAfter(hangout.scheduledAt.minus(Duration.ofMinutes(STARTING_SOON_MINUTES)))
        if (!isUnderWay && !isStartingSoon) {
            throw HangoutIllegalStateException("Notes open $STARTING_SOON_MINUTES minutes before a hangout and close when it ends.")
        }
    }
}
