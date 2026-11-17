package com.arcadiaeditions.fulfillment.shipping.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.fulfillment.shipping.domain.*
import com.arcadiaeditions.fulfillment.shipping.dtos.*
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface EventsMapper {

    companion object {
        val INSTANCE: EventsMapper = Mappers.getMapper(EventsMapper::class.java)
    }

    fun asFulfillmentScheduled(
        fulfillment: Fulfillment
    ): com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentScheduled

    @Mapping(target = "reason", source = "failureReason")
    fun asFulfillmentFailed(
        fulfillment: Fulfillment
    ): com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentFailed

    fun asFulfillmentReadyToShip(
        fulfillment: Fulfillment
    ): com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentReadyToShip

    @Mapping(target = "reason", source = "cancellationReason")
    fun asFulfillmentCancelled(
        fulfillment: Fulfillment
    ): com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentCancelled
}
