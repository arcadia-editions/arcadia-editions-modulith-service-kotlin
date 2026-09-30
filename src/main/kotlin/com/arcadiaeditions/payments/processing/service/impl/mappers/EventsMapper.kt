package com.arcadiaeditions.payments.processing.service.impl.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.payments.processing.domain.*
import com.arcadiaeditions.payments.processing.domain.events.*
import com.arcadiaeditions.payments.processing.service.dtos.*
import java.time.Instant
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface EventsMapper {

    companion object {
        val INSTANCE: EventsMapper = Mappers.getMapper(EventsMapper::class.java)
    }

    fun asPaymentAuthorized(payment: Payment): PaymentAuthorized

    // G-PP-06: `reason` has no matching field on Payment - it's sourced from `failureReason`, which
    // authorizePayment/reauthorize set on the DECLINED/FAILED branches.
    @Mapping(target = "reason", source = "failureReason")
    fun asPaymentDeclined(payment: Payment): PaymentDeclined

    @Mapping(target = "reason", source = "failureReason")
    fun asPaymentFailed(payment: Payment): PaymentFailed

    fun asPaymentCaptured(payment: Payment): PaymentCaptured

    // PaymentRetried.retriedAt, PaymentRetryExhausted.{reason,exhaustedAt},
    // PaymentCaptureFailed.{reason,failedAt} and PaymentVoided.reason don't live on the Payment
    // entity (retry/exhaustion have no dedicated timestamp columns; a capture failure must not
    // reuse the entity's own authorization `failureReason`/`failedAt`; void's `reason` is caller
    // input, e.g. from FulfillmentFailed, never persisted on Payment) - composed manually from the
    // entity plus the extra value(s), same pattern as catalog-inventory's EventsMapper (G-CI-05).

    fun asPaymentRetried(payment: Payment, retriedAt: Instant): PaymentRetried {
        return PaymentRetried(
            paymentId = payment.paymentId,
            orderId = payment.orderId,
            customerId = payment.customerId,
            attemptCount = payment.attemptCount,
            retriedAt = retriedAt,
        )
    }

    fun asPaymentRetryExhausted(payment: Payment, reason: String?, exhaustedAt: Instant): PaymentRetryExhausted {
        return PaymentRetryExhausted(
            paymentId = payment.paymentId,
            orderId = payment.orderId,
            customerId = payment.customerId,
            attemptCount = payment.attemptCount,
            reason = reason,
            exhaustedAt = exhaustedAt,
        )
    }

    fun asPaymentCaptureFailed(payment: Payment, reason: String?, failedAt: Instant): PaymentCaptureFailed {
        return PaymentCaptureFailed(
            paymentId = payment.paymentId,
            orderId = payment.orderId,
            customerId = payment.customerId,
            reason = reason,
            failedAt = failedAt,
        )
    }

    fun asPaymentVoided(payment: Payment, reason: String?): PaymentVoided {
        return PaymentVoided(
            paymentId = payment.paymentId,
            orderId = payment.orderId,
            customerId = payment.customerId,
            providerReference = payment.providerReference,
            reason = reason,
            voidedAt = payment.voidedAt,
        )
    }
}
