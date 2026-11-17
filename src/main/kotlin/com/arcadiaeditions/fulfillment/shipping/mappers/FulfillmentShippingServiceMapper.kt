package com.arcadiaeditions.fulfillment.shipping.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.fulfillment.shipping.domain.*
import com.arcadiaeditions.fulfillment.shipping.dtos.*
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface FulfillmentShippingServiceMapper {

    companion object {
        val INSTANCE: FulfillmentShippingServiceMapper = Mappers.getMapper(FulfillmentShippingServiceMapper::class.java)
    }

    // input mappings
    // ScheduleFulfillmentInputnull-Fulfillment scheduleFulfillment

    fun asFulfillment(input: ScheduleFulfillmentInput): Fulfillment

    @Mapping(target = "id", ignore = true)
    fun update(@MappingTarget entity: Fulfillment, input: ScheduleFulfillmentInput): Fulfillment
    // output mappings

    // CancelFulfillmentInput-Fulfillment cancelFulfillment
    fun asFulfillment(input: CancelFulfillmentInput): Fulfillment

    @Mapping(target = "id", ignore = true)
    fun update(@MappingTarget entity: Fulfillment, input: CancelFulfillmentInput): Fulfillment

    // RecordPaymentCapturedInput-Fulfillment recordPaymentCaptured
    fun asFulfillment(input: RecordPaymentCapturedInput): Fulfillment

    @Mapping(target = "id", ignore = true)
    fun update(@MappingTarget entity: Fulfillment, input: RecordPaymentCapturedInput): Fulfillment
}
