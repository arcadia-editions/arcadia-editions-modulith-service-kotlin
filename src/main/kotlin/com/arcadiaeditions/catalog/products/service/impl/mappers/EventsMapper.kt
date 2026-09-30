package com.arcadiaeditions.catalog.products.service.impl.mappers

import com.arcadiaeditions.catalog.products.domain.*
import com.arcadiaeditions.catalog.products.service.dtos.*
import com.arcadiaeditions.common.mappers.BaseMapper
import java.time.Instant
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface EventsMapper {

    companion object {
        val INSTANCE: EventsMapper = Mappers.getMapper(EventsMapper::class.java)
    }

    // Auto-mapped version (pre-existing); kept for compatibility but not used
    // The unmapped warnings are suppressed since the new overloads handle proper mapping.
    @Mapping(target = "productId", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    fun asProductPublished(product: Product): com.arcadiaeditions.catalog.products.domain.events.ProductPublished

    // Auto-mapped version (pre-existing); kept for compatibility but not used
    // The unmapped warnings are suppressed since the new overloads handle proper mapping.
    @Mapping(target = "productId", ignore = true)
    @Mapping(target = "retiredAt", ignore = true)
    fun asProductRetired(product: Product): com.arcadiaeditions.catalog.products.domain.events.ProductRetired

    // G-CP-01: ProductPublished with productId converted to String and publishedAt from clock timestamp.
    // The entity's `id` (Long) is converted to String for the event's `productId` field.
    fun asProductPublished(
        product: Product,
        publishedAt: Instant?,
    ): com.arcadiaeditions.catalog.products.domain.events.ProductPublished {
        return com.arcadiaeditions.catalog.products.domain.events.ProductPublished(
            productId = product.id?.toString(),
            sku = product.sku,
            name = product.name,
            price = product.price,
            currency = product.currency,
            editionSize = product.editionSize,
            trackingMode = product.trackingMode,
            launchAt = product.launchAt,
            publishedAt = publishedAt,
        )
    }

    // G-CP-01: ProductRetired with productId converted to String and retiredAt from clock timestamp.
    // The entity's `id` (Long) is converted to String for the event's `productId` field.
    fun asProductRetired(
        product: Product,
        retiredAt: Instant?,
    ): com.arcadiaeditions.catalog.products.domain.events.ProductRetired {
        return com.arcadiaeditions.catalog.products.domain.events.ProductRetired(
            productId = product.id?.toString(),
            sku = product.sku,
            retiredAt = retiredAt,
        )
    }
}
