package com.arcadiaeditions.payments.processing.service.impl.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.payments.processing.domain.*
import com.arcadiaeditions.payments.processing.service.dtos.*
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface PaymentsProcessingServiceMapper {

    companion object {
        val INSTANCE: PaymentsProcessingServiceMapper = Mappers.getMapper(PaymentsProcessingServiceMapper::class.java)
    }

    // input mappings
    // AuthorizePaymentInputnull-Payment authorizePayment

    fun asPayment(input: AuthorizePaymentInput): Payment

    @Mapping(target = "id", ignore = true)
    fun update(@MappingTarget entity: Payment, input: AuthorizePaymentInput): Payment

    // VoidPaymentInputnull-Payment voidPayment

    fun asPayment(input: VoidPaymentInput): Payment

    @Mapping(target = "id", ignore = true) fun update(@MappingTarget entity: Payment, input: VoidPaymentInput): Payment

    // RetryPaymentInputnull-Payment retryPayment

    fun asPayment(input: RetryPaymentInput): Payment

    @Mapping(target = "id", ignore = true) fun update(@MappingTarget entity: Payment, input: RetryPaymentInput): Payment

    // CapturePaymentInputnull-Payment capturePayment

    fun asPayment(input: CapturePaymentInput): Payment

    @Mapping(target = "id", ignore = true)
    fun update(@MappingTarget entity: Payment, input: CapturePaymentInput): Payment
    // output mappings
}
