package com.arcadiaeditions.orders.checkout.adapters.events.processing

import com.arcadiaeditions.orders.checkout.adapters.events.processing.mappers.PaymentsProcessingEventsListenerMapper
import com.arcadiaeditions.orders.checkout.core.domain.events.*
import com.arcadiaeditions.orders.checkout.core.inbound.*
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Internal domain event listeners for PaymentsProcessingZdl.
 *
 * This class is generated once and can be customized safely.
 */
@Component
@org.jmolecules.architecture.hexagonal.PrimaryAdapter
class PaymentsProcessingEventsListener(private val ordersCheckoutService: OrdersCheckoutService) {

    private val mapper: PaymentsProcessingEventsListenerMapper = PaymentsProcessingEventsListenerMapper.INSTANCE

    @ApplicationModuleListener
    fun onPaymentAuthorized(event: com.arcadiaeditions.payments.processing.domain.event.PaymentAuthorized) {
        ordersCheckoutService.confirmOrder(mapper.asConfirmOrderInput(event))
    }
}
