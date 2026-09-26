package com.arcadiaeditions.catalog.products.service.impl

import com.arcadiaeditions.catalog.products.domain.*
import com.arcadiaeditions.catalog.products.domain.events.*
import com.arcadiaeditions.catalog.products.events.*
import com.arcadiaeditions.catalog.products.repository.jpa.*
import com.arcadiaeditions.catalog.products.service.*
import com.arcadiaeditions.catalog.products.service.dtos.*
import com.arcadiaeditions.catalog.products.service.impl.mappers.*
import java.math.*
import java.time.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Service Implementation for managing [Product]. */
@Service
@Transactional
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.layered.ApplicationLayer
open class ProductCatalogServiceImpl(
    private val productRepository: ProductRepository,
    private val eventPublisher: EventPublisher,
) : ProductCatalogService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val productCatalogServiceMapper: ProductCatalogServiceMapper = ProductCatalogServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    override fun createProduct(input: CreateProductInput): Product {

        log.debug("[CRUD] Request to save Product: {}", input)
        var product = productCatalogServiceMapper.update(Product(), input)
        product = productRepository.save(product)
        // TODO: may need to reload the entity to fetch relationships 'mapped by id'
        return product
    }

    override fun updateProduct(id: Long, input: UpdateProductInput): Product {

        log.debug("Request updateProduct: {} {}", id, input)

        return productRepository
            .findByIdOrNull(id)
            ?.let { existingProduct ->
                ProductTransitions.ensureCanUpdateProduct(existingProduct)
                productCatalogServiceMapper.update(existingProduct, input)
                existingProduct.status = ProductStatus.DRAFT
                existingProduct
            }
            ?.let { productRepository.save(it) } ?: throw NoSuchElementException("Product not found with id: $id")
    }

    override fun publishProduct(id: Long): Product {

        log.debug("Request publishProduct: {}", id)

        val existingProduct =
            productRepository.findByIdOrNull(id) ?: throw NoSuchElementException("Product not found with id: $id")
        ProductTransitions.ensureCanPublishProduct(existingProduct)
        existingProduct.status = ProductStatus.PUBLISHED
        val product = productRepository.save(existingProduct)
        // emit events
        eventPublisher.onProductPublished(eventsMapper.asProductPublished(product))
        return product
    }

    override fun retireProduct(id: Long): Product {

        log.debug("Request retireProduct: {}", id)

        val existingProduct =
            productRepository.findByIdOrNull(id) ?: throw NoSuchElementException("Product not found with id: $id")
        ProductTransitions.ensureCanRetireProduct(existingProduct)
        existingProduct.status = ProductStatus.RETIRED
        val product = productRepository.save(existingProduct)
        // emit events
        eventPublisher.onProductRetired(eventsMapper.asProductRetired(product))
        return product
    }
}
