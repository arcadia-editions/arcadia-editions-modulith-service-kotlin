package com.arcadiaeditions.catalog.products.service.impl.mappers

import com.arcadiaeditions.catalog.products.domain.*
import com.arcadiaeditions.catalog.products.service.dtos.*
import com.arcadiaeditions.common.mappers.BaseMapper
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface ProductCatalogServiceMapper {

    companion object {
        val INSTANCE: ProductCatalogServiceMapper = Mappers.getMapper(ProductCatalogServiceMapper::class.java)
    }

    // input mappings
    // UpdateProductInputnull-Product updateProduct

    fun asProduct(input: UpdateProductInput): Product

    @Mapping(target = "id", ignore = true)
    fun update(@MappingTarget entity: Product, input: UpdateProductInput): Product

    // CreateProductInputnull-Product createProduct

    fun asProduct(input: CreateProductInput): Product

    @Mapping(target = "id", ignore = true)
    fun update(@MappingTarget entity: Product, input: CreateProductInput): Product
    // output mappings
}
