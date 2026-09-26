package com.arcadiaeditions.orders.checkout.web.mappers

import com.arcadiaeditions.common.mappers.*
import com.arcadiaeditions.orders.checkout.domain.*
import com.arcadiaeditions.orders.checkout.service.dtos.*
import com.arcadiaeditions.orders.checkout.web.dtos.*

import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.factory.Mappers
import java.math.*
import java.time.*
import java.util.*
import org.springframework.data.domain.Page

@Mapper(uses = [BaseMapper::class])
interface OrdersCheckoutDTOsMapper {

    companion object {
        val INSTANCE: OrdersCheckoutDTOsMapper = Mappers.getMapper(OrdersCheckoutDTOsMapper::class.java)
    }

    // request mappings
    fun asStartOrderCheckoutInput(dto: StartOrderCheckoutInputDTO): StartOrderCheckoutInput

    // response mappings
    
    fun asOrderDTO(entity: Order): OrderDTO
    

}
