package com.arcadiaeditions.catalog.products.config

import com.arcadiaeditions.catalog.products.domain.*
import com.arcadiaeditions.catalog.products.domain.events.*
import com.arcadiaeditions.catalog.products.events.*
import com.arcadiaeditions.catalog.products.service.*
import com.arcadiaeditions.catalog.products.service.impl.*
import com.arcadiaeditions.config.TestDataLoader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/** Services InMemory Config. It can be used standalone or with @SpringBootTest. */
@Configuration
@Profile("in-memory")
open class ServicesInMemoryConfig : RepositoriesInMemoryConfig() {

    private val eventPublisher = InMemoryEventPublisher()
    protected val productCatalogService = ProductCatalogServiceImpl(productRepository(), eventPublisher)

    @Bean
    open fun <T : ProductCatalogService> productCatalogService(): T {
        return productCatalogService as T
    }

    companion object {
        var _products: List<Product>? = null
    }

    fun reloadTestData() {

        val testDataLoader = TestDataLoader(listOf(Product::class.java))
        val products = _products ?: testDataLoader.loadCollectionTestDataAsObjects(Product::class.java)
        productRepository().deleteAll()
        productRepository().saveAll(products)
        eventPublisher.getEvents().clear()
    }

    @Bean
    fun eventPublisher(): InMemoryEventPublisher {
        return eventPublisher
    }
}
