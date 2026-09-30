package com.arcadiaeditions.notifications.consumer.events.processing

import com.arcadiaeditions.notifications.consumer.domain.events.*
import com.arcadiaeditions.notifications.consumer.events.processing.mappers.PaymentsProcessingEventsListenerMapper
import com.arcadiaeditions.notifications.consumer.service.*
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
    private val notificationsConsumerService: NotificationsConsumerService,
) {

    private val mapper: PaymentsProcessingEventsListenerMapper = PaymentsProcessingEventsListenerMapper.INSTANCE

    @ApplicationModuleListener
    fun onPaymentCaptured(event: com.arcadiaeditions.payments.processing.domain.events.PaymentCaptured) {
        notificationsConsumerService.sendOrderConfirmation(mapper.asSendOrderConfirmationInput(event))
    }
}
