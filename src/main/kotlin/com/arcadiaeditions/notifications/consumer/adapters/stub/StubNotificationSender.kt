package com.arcadiaeditions.notifications.consumer.adapters.stub

import com.arcadiaeditions.notifications.consumer.service.ports.NotificationSender
import com.arcadiaeditions.notifications.consumer.service.ports.SendOutcome
import com.arcadiaeditions.notifications.consumer.service.ports.SendRequest
import com.arcadiaeditions.notifications.consumer.service.ports.SendResult
import java.util.UUID
import org.springframework.stereotype.Component

/**
 * Deterministic default [NotificationSender] (Q-10). No real e-mail provider is wired yet, so this
 * stub is the only bean.
 *
 * Outcome is controlled two ways, checked in this order:
 * 1. `forcedOutcome`, settable by tests, always wins when non-null.
 * 2. `SendRequest.recipient == "undeliverable@example.com"` → [SendOutcome.FAILED]. Any other
 *    recipient → [SendOutcome.SENT].
 */
@Component
class StubNotificationSender : NotificationSender {

    var forcedOutcome: SendOutcome? = null

    override fun send(request: SendRequest): SendResult {
        val outcome =
            forcedOutcome ?: if (request.recipient == UNDELIVERABLE_RECIPIENT) SendOutcome.FAILED
            else SendOutcome.SENT
        return when (outcome) {
            SendOutcome.SENT -> SendResult(outcome, providerReference = "stub-msg-${UUID.randomUUID()}")
            SendOutcome.FAILED -> SendResult(outcome, failureReason = "Recipient rejected the message")
        }
    }

    companion object {
        const val UNDELIVERABLE_RECIPIENT = "undeliverable@example.com"
    }
}
