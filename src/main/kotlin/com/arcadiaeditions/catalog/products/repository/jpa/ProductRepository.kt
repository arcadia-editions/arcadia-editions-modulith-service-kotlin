package com.arcadiaeditions.catalog.products.repository.jpa

import com.arcadiaeditions.catalog.products.domain.*
import java.math.*
import java.time.*
import java.util.*
import org.springframework.data.jpa.repository.*
import org.springframework.stereotype.Repository

/** Spring Data JPA repository for the Product entity. */
@Suppress("unused")
@Repository
@org.jmolecules.ddd.annotation.Repository
@org.jmolecules.architecture.layered.InfrastructureLayer
interface ProductRepository : JpaRepository<Product, Long> {}
