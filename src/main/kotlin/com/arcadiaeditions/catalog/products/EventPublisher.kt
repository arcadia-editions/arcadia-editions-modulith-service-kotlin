package com.arcadiaeditions.catalog.products

import com.arcadiaeditions.catalog.products.domain.*
import com.arcadiaeditions.catalog.products.domain.events.*

interface EventPublisher {

    fun onProductPublished(event: ProductPublished)

    fun onProductRetired(event: ProductRetired)
}
