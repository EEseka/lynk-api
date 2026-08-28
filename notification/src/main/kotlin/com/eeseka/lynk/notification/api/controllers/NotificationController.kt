package com.eeseka.lynk.notification.api.controllers

import com.eeseka.lynk.common.api.util.requestUserId
import com.eeseka.lynk.common.domain.type.NotificationId
import com.eeseka.lynk.notification.api.dto.NotificationDto
import com.eeseka.lynk.notification.api.dto.UnreadCountDto
import com.eeseka.lynk.notification.api.mappers.toNotificationDto
import com.eeseka.lynk.notification.service.NotificationService
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@Validated
@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val notificationService: NotificationService
) {
    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
        private const val MAX_PAGE_SIZE = 50
    }

    @GetMapping
    fun getNotifications(
        @RequestParam(required = false)
        before: Instant? = null,

        @RequestParam(required = false)
        @Min(1) @Max(MAX_PAGE_SIZE.toLong())
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): List<NotificationDto> {
        return notificationService.getNotifications(
            userId = requestUserId,
            before = before,
            pageSize = pageSize
        ).map { it.toNotificationDto() }
    }

    @GetMapping("/unread-count")
    fun getUnreadCount(): UnreadCountDto {
        return UnreadCountDto(notificationService.getUnreadCount(requestUserId))
    }

    @PatchMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markAsRead(
        @PathVariable notificationId: NotificationId
    ) {
        notificationService.markAsRead(
            userId = requestUserId,
            notificationId = notificationId
        )
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markAllAsRead() {
        notificationService.markAllAsRead(requestUserId)
    }
}