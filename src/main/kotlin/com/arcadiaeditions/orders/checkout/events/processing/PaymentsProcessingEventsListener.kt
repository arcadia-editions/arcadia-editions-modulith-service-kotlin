package com.arcadiaeditions.orders.checkout.events.processing

import com.arcadiaeditions.orders.checkout.domain.events.*
import com.arcadiaeditions.orders.checkout.events.processing.mappers.PaymentsProcessingEventsListenerMapper
import com.arcadiaeditions.orders.checkout.service.*
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Internal domain event listeners for PaymentsProcessingZdl.
 *
 * This class is generated once and can be customized safely.
 */
@Component
@org.jmolecules.architecture.layered.InterfaceLayer
class PaymentsProcessingEventsListener(
    private val ordersCheckoutService: OrdersCheckoutService,
) {

    private val mapper: PaymentsProcessingEventsListenerMapper = PaymentsProcessingEventsListenerMapper.INSTANCE

    @ApplicationModuleListener
    fun onPaymentAuthorized(event: com.arcadiaeditions.payments.processing.domain.events.PaymentAuthorized) {
        ordersCheckoutService.confirmOrder(mapper.asConfirmOrderInput(event))
    }
}
