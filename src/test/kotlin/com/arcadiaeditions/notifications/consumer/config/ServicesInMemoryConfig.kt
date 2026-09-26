package com.arcadiaeditions.notifications.consumer.config

import com.arcadiaeditions.config.TestDataLoader
import com.arcadiaeditions.notifications.consumer.domain.*
import com.arcadiaeditions.notifications.consumer.domain.events.*
import com.arcadiaeditions.notifications.consumer.events.*
import com.arcadiaeditions.notifications.consumer.service.*
import com.arcadiaeditions.notifications.consumer.service.impl.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/** Services InMemory Config. It can be used standalone or with @SpringBootTest. */
@Configuration
@Profile("in-memory")
open class ServicesInMemoryConfig : RepositoriesInMemoryConfig() {

    private val eventPublisher = InMemoryEventPublisher()
    protected val notificationsConsumerService =
        NotificationsConsumerServiceImpl(notificationRepository(), eventPublisher)

    @Bean
    open fun <T : NotificationsConsumerService> notificationsConsumerService(): T {
        return notificationsConsumerService as T
    }

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
