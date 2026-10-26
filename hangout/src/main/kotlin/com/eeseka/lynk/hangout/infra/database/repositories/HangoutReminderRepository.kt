package com.eeseka.lynk.hangout.infra.database.repositories

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.hangout.domain.model.HangoutReminderKind
import com.eeseka.lynk.hangout.infra.database.entities.HangoutReminderEntity
import org.springframework.data.jpa.repository.JpaRepository

interface HangoutReminderRepository : JpaRepository<HangoutReminderEntity, Long> {

    fun deleteByHangoutIdAndKindIn(hangoutId: HangoutId, kinds: List<HangoutReminderKind>)
}
