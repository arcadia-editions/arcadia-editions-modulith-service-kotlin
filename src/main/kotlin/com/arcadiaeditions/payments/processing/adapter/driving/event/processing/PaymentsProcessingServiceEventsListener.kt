package com.arcadiaeditions.payments.processing.adapter.driving.event.processing

import com.arcadiaeditions.payments.processing.adapter.driving.event.processing.mapper.PaymentsProcessingServiceEventsListenerMapper
import com.arcadiaeditions.payments.processing.application.port.driving.*
import com.arcadiaeditions.payments.processing.domain.event.*
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Internal domain event listeners for PaymentsProcessingService.
 *
 * This class is generated once and can be customized safely.
 */
@Component
@org.jmolecules.architecture.hexagonal.PrimaryAdapter
class PaymentsProcessingServiceEventsListener(private val paymentsProcessingService: PaymentsProcessingService) {

    private val mapper: PaymentsProcessingServiceEventsListenerMapper =
        PaymentsProcessingServiceEventsListenerMapper.INSTANCE

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    @ApplicationModuleListener
    fun authorizePayment(event: PaymentRetried) {
        try {
            paymentsProcessingService.authorizePayment(mapper.asAuthorizePaymentInput(event))
        } catch (e: IllegalArgumentException) {
            // no Payment to retry (P-01 D): rejected for good, do not leave the publication incomplete to be resubmitted
            log.warn("PaymentRetried for order {} dropped: {}", event.orderId, e.message)
        }
    }

    @ApplicationModuleListener
    fun retryPayment(event: PaymentFailed) {
        paymentsProcessingService.retryPayment(mapper.asRetryPaymentInput(event))
    }

    @ApplicationModuleListener
    fun voidPayment(event: PaymentCaptureFailed) {
        paymentsProcessingService.voidPayment(mapper.asVoidPaymentInput(event))
    }
}
