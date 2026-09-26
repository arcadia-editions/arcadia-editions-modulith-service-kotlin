package com.arcadiaeditions.payments.processing.events.checkout

import com.arcadiaeditions.payments.processing.domain.events.*
import com.arcadiaeditions.payments.processing.events.checkout.mappers.OrdersCheckoutEventsListenerMapper
import com.arcadiaeditions.payments.processing.service.*
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Internal domain event listeners for OrdersCheckoutZdl.
 *
 * This class is generated once and can be customized safely.
 */
@Component
@org.jmolecules.architecture.layered.InterfaceLayer
class OrdersCheckoutEventsListener(
    private val paymentsProcessingService: PaymentsProcessingService,
    private val mapper: OrdersCheckoutEventsListenerMapper,
) {

    @ApplicationModuleListener
    fun onOrderCreated(event: com.arcadiaeditions.orders.checkout.domain.events.OrderCreated) {
        paymentsProcessingService.authorizePayment(mapper.asAuthorizePaymentInput(event))
    }
}
