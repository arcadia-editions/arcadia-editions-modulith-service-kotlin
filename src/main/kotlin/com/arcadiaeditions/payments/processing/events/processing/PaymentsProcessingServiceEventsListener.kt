package com.arcadiaeditions.payments.processing.events.processing

import com.arcadiaeditions.payments.processing.domain.events.*
import com.arcadiaeditions.payments.processing.events.processing.mappers.PaymentsProcessingServiceEventsListenerMapper
import com.arcadiaeditions.payments.processing.service.*
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Internal domain event listeners for PaymentsProcessingService.
 *
 * This class is generated once and can be customized safely.
 */
@Component
@org.jmolecules.architecture.layered.InterfaceLayer
class PaymentsProcessingServiceEventsListener(
    private val paymentsProcessingService: PaymentsProcessingService,
) {

    private val mapper: PaymentsProcessingServiceEventsListenerMapper = PaymentsProcessingServiceEventsListenerMapper.INSTANCE

    @ApplicationModuleListener
    fun retryPayment(event: PaymentFailed) {
        paymentsProcessingService.retryPayment(mapper.asRetryPaymentInput(event))
    }

    @ApplicationModuleListener
    fun voidPayment(event: PaymentCaptureFailed) {
        paymentsProcessingService.voidPayment(mapper.asVoidPaymentInput(event))
    }
}
