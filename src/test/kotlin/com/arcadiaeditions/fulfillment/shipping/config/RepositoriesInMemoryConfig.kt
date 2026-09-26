package com.arcadiaeditions.fulfillment.shipping.config

import com.arcadiaeditions.fulfillment.shipping.repository.jpa.*
import com.arcadiaeditions.fulfillment.shipping.repository.jpa.inmemory.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

// @Configuration
open class RepositoriesInMemoryConfig {

    protected val fulfillmentRepository = FulfillmentRepositoryInMemory()

    @Bean
    @Primary
    fun fulfillmentRepository(): FulfillmentRepositoryInMemory {
        return fulfillmentRepository
    }
}
