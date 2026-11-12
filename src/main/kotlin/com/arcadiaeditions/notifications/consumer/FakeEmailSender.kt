package com.arcadiaeditions.notifications.consumer

import java.util.concurrent.CopyOnWriteArrayList
import org.springframework.stereotype.Component

/**
 * Deterministic stand-in for the e-mail provider: accepts every message unless [refusalReason] is set. Keeps what it
 * sent in [sent]. A real adapter is a separate task.
 */
@Component
@org.jmolecules.architecture.hexagonal.SecondaryAdapter
class FakeEmailSender : EmailSender {

    @Volatile var refusalReason: String? = null

    val sent: MutableList<EmailMessage> = CopyOnWriteArrayList()

    override fun send(message: EmailMessage): EmailResult {
        refusalReason?.let {
            return EmailResult.refused(it)
        }
        sent.add(message)
        return EmailResult.sent()
    }
}
