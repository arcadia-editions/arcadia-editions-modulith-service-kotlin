package com.arcadiaeditions.catalog.products.service.impl.mappers

import com.arcadiaeditions.catalog.products.domain.*
import com.arcadiaeditions.catalog.products.service.dtos.*
import com.arcadiaeditions.common.mappers.BaseMapper
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface EventsMapper {

    companion object {
        val INSTANCE: EventsMapper = Mappers.getMapper(EventsMapper::class.java)
    }

    fun asProductPublished(product: Product): com.arcadiaeditions.catalog.products.domain.events.ProductPublished

    fun asProductRetired(product: Product): com.arcadiaeditions.catalog.products.domain.events.ProductRetired
}
