package com.eeseka.lynk.notification.domain.model

import com.eeseka.lynk.common.domain.type.HangoutId
import java.util.UUID

data class PushNotification(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val recipients: List<DeviceToken>,
    val message: String,
    val hangoutId: HangoutId,
    val data: Map<String, String>
)