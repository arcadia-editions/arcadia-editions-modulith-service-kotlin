package com.arcadiaeditions.catalog.products.config

import com.arcadiaeditions.catalog.products.repository.jpa.*
import com.arcadiaeditions.catalog.products.repository.jpa.inmemory.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

// @Configuration
open class RepositoriesInMemoryConfig {

    protected val productRepository = ProductRepositoryInMemory()

    @Bean
    @Primary
    fun productRepository(): ProductRepositoryInMemory {
        return productRepository
    }
}
