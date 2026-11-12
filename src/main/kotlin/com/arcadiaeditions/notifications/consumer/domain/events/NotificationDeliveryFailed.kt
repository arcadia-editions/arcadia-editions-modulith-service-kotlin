package com.arcadiaeditions.notifications.consumer.domain.events

import com.arcadiaeditions.notifications.consumer.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
@org.springframework.modulith.NamedInterface("events.NotificationDeliveryFailed")
@org.jmolecules.event.annotation.DomainEvent
@org.jmolecules.architecture.layered.DomainLayer
data class NotificationDeliveryFailed(
    var notificationId: String? = null,
    var orderId: String? = null,
    var customerId: String? = null,
    var skus: MutableList<String>? = mutableListOf(),
    var type: NotificationType? = null,
    var channel: NotificationChannel? = null,
    var recipient: String? = null,
    var reason: String? = null,
    var failedAt: Instant? = null,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }

    fun addSkus(skus: String): NotificationDeliveryFailed {
        this.skus?.add(skus)
        return this
    }
}
