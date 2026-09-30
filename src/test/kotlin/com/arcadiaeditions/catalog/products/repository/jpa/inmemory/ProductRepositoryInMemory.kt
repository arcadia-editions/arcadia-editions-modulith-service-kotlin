package com.arcadiaeditions.catalog.products.repository.jpa.inmemory

import com.arcadiaeditions.catalog.products.domain.*
import com.arcadiaeditions.catalog.products.repository.jpa.ProductRepository
import java.math.*
import java.time.*
import java.util.*

class ProductRepositoryInMemory : InMemoryJpaRepository<Product, Long>(), ProductRepository {
    override fun findBySku(sku: String): Product? = findByUniqueField("sku", sku)
}
