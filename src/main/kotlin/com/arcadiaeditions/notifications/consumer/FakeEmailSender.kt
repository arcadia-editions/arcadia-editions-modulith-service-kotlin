package com.arcadiaeditions.notifications.consumer

import java.util.concurrent.CopyOnWriteArrayList
import org.springframework.stereotype.Component

/**
 * Deterministic stand-in for the e-mail provider: accepts every message unless [refusalReason] is set. Keeps what it
 * sent in [sent]. A real adapter is a separate task.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["arcadia.fake-adapters.enabled"], havingValue = "true")
@org.jmolecules.architecture.hexagonal.SecondaryAdapter
class FakeEmailSender : EmailSender {

    @Volatile var refusalReason: String? = null

    /** The last [MAX_SENT] messages only, so a long-running app does not grow without bound. */
    val sent: MutableList<EmailMessage> = CopyOnWriteArrayList()

    companion object {
        const val MAX_SENT = 100
    }

    override fun send(message: EmailMessage): EmailResult {
        refusalReason?.let {
            return EmailResult.refused(it)
        }
        sent.add(message)
        while (sent.size > MAX_SENT) sent.removeAt(0)
        return EmailResult.sent()
    }
}
