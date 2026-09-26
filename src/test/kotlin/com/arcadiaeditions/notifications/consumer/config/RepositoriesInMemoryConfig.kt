package com.arcadiaeditions.notifications.consumer.config

import com.arcadiaeditions.notifications.consumer.repository.jpa.*
import com.arcadiaeditions.notifications.consumer.repository.jpa.inmemory.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

// @Configuration
open class RepositoriesInMemoryConfig {

    protected val notificationRepository = NotificationRepositoryInMemory()

    @Bean
    @Primary
    fun notificationRepository(): NotificationRepositoryInMemory {
        return notificationRepository
    }
}
