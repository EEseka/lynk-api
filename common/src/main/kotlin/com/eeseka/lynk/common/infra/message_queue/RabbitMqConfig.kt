package com.eeseka.lynk.common.infra.message_queue

import com.eeseka.lynk.common.domain.events.LynkEvent
import com.eeseka.lynk.common.domain.events.hangout.HangoutEvent
import com.eeseka.lynk.common.domain.events.hangout.HangoutEventConstants
import com.eeseka.lynk.common.domain.events.user.UserEvent
import com.eeseka.lynk.common.domain.events.user.UserEventConstants
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.EnableTransactionManagement
import tools.jackson.databind.DefaultTyping
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import tools.jackson.module.kotlin.kotlinModule

@Configuration
@EnableTransactionManagement
class RabbitMqConfig {

    @Bean
    fun messageConverter(): JacksonJsonMessageConverter {
        val polymorphicTypeValidator = BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType(LynkEvent::class.java)
            .allowIfSubType("java.util.") // Allow Java lists
            .allowIfSubType("kotlin.collections.") // Kotlin collections
            .build()

        val objectMapper = JsonMapper.builder()
            .addModule(kotlinModule())
            .polymorphicTypeValidator(polymorphicTypeValidator)
            .activateDefaultTyping(polymorphicTypeValidator, DefaultTyping.NON_FINAL)
            .build()

        return JacksonJsonMessageConverter(
            objectMapper,
            UserEvent::class.java.packageName,
            HangoutEvent::class.java.packageName
        ).apply {
            typePrecedence = JacksonJavaTypeMapper.TypePrecedence.TYPE_ID
        }
    }

    @Bean
    fun rabbitTemplate(
        connectionFactory: ConnectionFactory,
        messageConverter: JacksonJsonMessageConverter,
    ): RabbitTemplate {
        return RabbitTemplate(connectionFactory).apply {
            this.messageConverter = messageConverter
        }
    }

    @Bean
    fun userExchange() = TopicExchange(
        UserEventConstants.USER_EXCHANGE,
        true,
        false
    )

    @Bean
    fun hangoutExchange() = TopicExchange(
        HangoutEventConstants.HANGOUT_EXCHANGE,
        true,
        false
    )

    @Bean
    fun notificationUserEventsQueue() = Queue(
        MessageQueues.NOTIFICATION_USER_EVENTS,
        true
    )

    @Bean
    fun notificationHangoutEventsQueue() = Queue(
        MessageQueues.NOTIFICATION_HANGOUT_EVENTS,
        true
    )

    @Bean
    fun hangoutUserEventsQueue() = Queue(
        MessageQueues.HANGOUT_USER_EVENTS,
        true
    )

    @Bean
    fun spotUserEventsQueue() = Queue(
        MessageQueues.SPOT_USER_EVENTS,
        true
    )

    @Bean
    fun paymentHangoutEventsQueue() = Queue(
        MessageQueues.PAYMENT_HANGOUT_EVENTS,
        true
    )

    @Bean
    fun notificationUserEventsBinding(
        notificationUserEventsQueue: Queue,
        userExchange: TopicExchange,
    ): Binding {
        return BindingBuilder
            .bind(notificationUserEventsQueue)
            .to(userExchange)
            .with("user.*")
    }

    @Bean
    fun notificationHangoutEventsBinding(
        notificationHangoutEventsQueue: Queue,
        hangoutExchange: TopicExchange,
    ): Binding {
        return BindingBuilder
            .bind(notificationHangoutEventsQueue)
            .to(hangoutExchange)
            .with("hangout.*")
    }

    @Bean
    fun hangoutUserEventsBinding(
        hangoutUserEventsQueue: Queue,
        userExchange: TopicExchange,
    ): Binding {
        return BindingBuilder
            .bind(hangoutUserEventsQueue)
            .to(userExchange)
            .with("user.*")
    }

    @Bean
    fun spotUserEventsBinding(
        spotUserEventsQueue: Queue,
        userExchange: TopicExchange,
    ): Binding {
        return BindingBuilder
            .bind(spotUserEventsQueue)
            .to(userExchange)
            .with("user.*")
    }

    @Bean
    fun paymentHangoutEventsBinding(
        paymentHangoutEventsQueue: Queue,
        hangoutExchange: TopicExchange,
    ): Binding {
        return BindingBuilder
            .bind(paymentHangoutEventsQueue)
            .to(hangoutExchange)
            .with("hangout.*")
    }
}