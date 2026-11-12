package com.arcadiaeditions.payments.processing.adapter.driving.event.checkout

import com.arcadiaeditions.payments.processing.adapter.driving.event.checkout.mapper.OrdersCheckoutEventsListenerMapper
import com.arcadiaeditions.payments.processing.application.port.driving.*
import com.arcadiaeditions.payments.processing.domain.event.*
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Internal domain event listeners for OrdersCheckoutZdl.
 *
 * This class is generated once and can be customized safely.
 */
@Component
@org.jmolecules.architecture.hexagonal.PrimaryAdapter
class OrdersCheckoutEventsListener(private val paymentsProcessingService: PaymentsProcessingService) {

    private val mapper: OrdersCheckoutEventsListenerMapper = OrdersCheckoutEventsListenerMapper.INSTANCE

    @ApplicationModuleListener
    fun onOrderCreated(event: com.arcadiaeditions.orders.checkout.core.domain.events.OrderCreated) {
        paymentsProcessingService.authorizePayment(mapper.asAuthorizePaymentInput(event))
    }
}
