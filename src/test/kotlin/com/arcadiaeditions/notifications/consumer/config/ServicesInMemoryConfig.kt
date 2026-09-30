package com.arcadiaeditions.notifications.consumer.config

import com.arcadiaeditions.config.TestDataLoader
import com.arcadiaeditions.notifications.consumer.domain.*
import com.arcadiaeditions.notifications.consumer.domain.events.*
import com.arcadiaeditions.notifications.consumer.events.*
import com.arcadiaeditions.notifications.consumer.adapters.stub.StubNotificationSender
import com.arcadiaeditions.notifications.consumer.service.*
import com.arcadiaeditions.notifications.consumer.service.impl.*
import com.arcadiaeditions.notifications.consumer.service.ports.NotificationSender
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/** Services InMemory Config. It can be used standalone or with @SpringBootTest. */
@Configuration
@Profile("in-memory")
open class ServicesInMemoryConfig : RepositoriesInMemoryConfig() {

    private val eventPublisher = InMemoryEventPublisher()

    // X-06: fixed clock so tests can assert on exact timestamps.
    protected val clock: Clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)

    // Deterministic stub port (Q-10); tests can force outcomes via its `forcedOutcome` property.
    protected val notificationSender = StubNotificationSender()

    protected val notificationsConsumerService =
        NotificationsConsumerServiceImpl(notificationRepository(), eventPublisher, clock, notificationSender)

    @Bean
    open fun <T : NotificationsConsumerService> notificationsConsumerService(): T {
        return notificationsConsumerService as T
    }

    @Bean open fun clock(): Clock = clock

    @Bean open fun notificationSender(): NotificationSender = notificationSender

    companion object {
        var _notifications: List<Notification>? = null
    }

    fun reloadTestData() {

        val testDataLoader = TestDataLoader(listOf(Notification::class.java))
        val notifications = _notifications ?: testDataLoader.loadCollectionTestDataAsObjects(Notification::class.java)
        notificationRepository().deleteAll()
        notificationRepository().saveAll(notifications)
        eventPublisher.getEvents().clear()
    }

    @Bean
    fun eventPublisher(): InMemoryEventPublisher {
        return eventPublisher
    }
}
