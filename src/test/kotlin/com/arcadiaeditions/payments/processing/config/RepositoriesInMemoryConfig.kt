package com.arcadiaeditions.payments.processing.config

import com.arcadiaeditions.payments.processing.repository.jpa.*
import com.arcadiaeditions.payments.processing.repository.jpa.inmemory.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

// @Configuration
open class RepositoriesInMemoryConfig {

    protected val paymentRepository = PaymentRepositoryInMemory()

    @Bean
    @Primary
    fun paymentRepository(): PaymentRepositoryInMemory {
        return paymentRepository
    }
}
