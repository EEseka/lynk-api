package com.eeseka.lynk.notification.domain.exception

class NotificationNotFoundException(notificationId: String) :
    RuntimeException("Notification $notificationId was not found.")