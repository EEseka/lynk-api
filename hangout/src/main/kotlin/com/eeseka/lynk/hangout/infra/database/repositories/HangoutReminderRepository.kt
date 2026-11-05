package com.eeseka.lynk.hangout.infra.database.repositories

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.hangout.domain.model.HangoutReminderKind
import com.eeseka.lynk.hangout.infra.database.entities.HangoutReminderEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface HangoutReminderRepository : JpaRepository<HangoutReminderEntity, Long> {

    @Modifying
    @Query("DELETE FROM HangoutReminderEntity r WHERE r.hangoutId = :hangoutId AND r.kind IN :kinds")
    fun deleteByHangoutIdAndKindIn(hangoutId: HangoutId, kinds: List<HangoutReminderKind>)
}
