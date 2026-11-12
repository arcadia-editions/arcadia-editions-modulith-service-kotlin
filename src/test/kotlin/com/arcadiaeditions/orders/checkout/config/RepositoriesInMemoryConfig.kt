package com.arcadiaeditions.orders.checkout.config

import com.arcadiaeditions.orders.checkout.core.outbound.jpa.*
import com.arcadiaeditions.orders.checkout.infrastructure.jpa.inmemory.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

// @Configuration
open class RepositoriesInMemoryConfig {

    protected val orderRepository = OrderRepositoryInMemory()

    @Bean
    @Primary
    fun orderRepository(): OrderRepositoryInMemory {
        return orderRepository
    }
}
