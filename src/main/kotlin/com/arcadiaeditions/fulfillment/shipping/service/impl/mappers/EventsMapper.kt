package com.arcadiaeditions.fulfillment.shipping.service.impl.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.fulfillment.shipping.domain.*
import com.arcadiaeditions.fulfillment.shipping.service.dtos.*
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

    /**
     * G-FS-03: [com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentFailed.reason] has
     * no matching field on [Fulfillment] by name - it's sourced from `failureReason`, which
     * `scheduleFulfillment` sets on the FAILED branch.
     */
    @Mapping(target = "reason", source = "failureReason")
    fun asFulfillmentFailed(
        fulfillment: Fulfillment
    ): com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentFailed

    fun asFulfillmentReadyToShip(
        fulfillment: Fulfillment
    ): com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentReadyToShip
}
