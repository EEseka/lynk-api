package com.eeseka.lynk.support

import com.eeseka.lynk.common.domain.events.hangout.HangoutEvent
import com.eeseka.lynk.common.domain.events.user.UserEvent
import com.eeseka.lynk.hangout.infra.messaging.HangoutUserEventListener
import com.eeseka.lynk.notification.infra.messaging.NotificationHangoutEventListener
import com.eeseka.lynk.notification.infra.messaging.NotificationUserEventListener
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.then
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.anyString
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.boot.test.context.TestComponent

/**
 * Stands in for RabbitMQ between the modules.
 *
 * The modules only learn about each other through the broker: the hangout module has no idea a
 * person exists until a `UserEvent.ProfileCompleted` reaches it, and a test that skipped that would
 * be arranging a projection the application never built. There is no broker in a test run, so this
 * takes what was published to the mocked template and hands it to the listener that would have
 * received it — the real publishing and the real mapping, with only the wire missing.
 */
@TestComponent
class TestBroker(
    private val rabbitTemplate: RabbitTemplate,
    private val hangoutUserEventListener: HangoutUserEventListener,
    private val notificationUserEventListener: NotificationUserEventListener,
    private val notificationHangoutEventListener: NotificationHangoutEventListener
) {

    private var delivered = 0

    /**
     * Delivers everything published since the last delivery, to every listener subscribed to it —
     * a user event goes to both the hangout module and the notification module, exactly as two
     * queues bound to the same exchange would.
     */
    fun deliverEvents() {
        val published = publishedEvents()

        published.drop(delivered).forEach { event ->
            when (event) {
                is UserEvent -> {
                    hangoutUserEventListener.handleUserEvent(event)
                    notificationUserEventListener.handleUserEvent(event)
                }

                is HangoutEvent -> notificationHangoutEventListener.handleHangoutEvent(event)
            }
        }

        delivered = published.size
    }

    /** Called between tests: the mock is reset, so what it remembers starts again from nothing. */
    fun reset() {
        delivered = 0
    }

    private fun publishedEvents(): List<Any> {
        val captor = ArgumentCaptor.forClass(Any::class.java)

        then(rabbitTemplate).should(atLeast(0)).convertAndSend(
            anyString(),
            anyString(),
            captor.capture()
        )

        return captor.allValues
    }
}
