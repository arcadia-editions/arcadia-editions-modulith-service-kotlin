package com.arcadiaeditions.catalog.products.domain.events

import com.arcadiaeditions.catalog.products.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
@org.springframework.modulith.NamedInterface("events.ProductPublished")
@org.jmolecules.event.annotation.DomainEvent
@org.jmolecules.architecture.layered.DomainLayer
data class ProductPublished(
    var productId: String? = null,
    var sku: String? = null,
    var name: String? = null,
    var price: BigDecimal? = null,
    var currency: String? = null,
    var editionSize: Integer? = null,
    var trackingMode: InventoryTrackingMode? = null,
    var launchAt: Instant? = null,
    var publishedAt: Instant? = null,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
