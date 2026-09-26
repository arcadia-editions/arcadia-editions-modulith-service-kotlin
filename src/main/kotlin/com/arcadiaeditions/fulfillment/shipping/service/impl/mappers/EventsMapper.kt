package com.arcadiaeditions.fulfillment.shipping.service.impl.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.fulfillment.shipping.domain.*
import com.arcadiaeditions.fulfillment.shipping.service.dtos.*
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface EventsMapper {

    companion object {
        val INSTANCE: EventsMapper = Mappers.getMapper(EventsMapper::class.java)
    }

    fun asFulfillmentScheduled(
        fulfillment: Fulfillment
    ): com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentScheduled

    fun asFulfillmentFailed(
        fulfillment: Fulfillment
    ): com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentFailed

    fun asFulfillmentReadyToShip(
        fulfillment: Fulfillment
    ): com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentReadyToShip
}
