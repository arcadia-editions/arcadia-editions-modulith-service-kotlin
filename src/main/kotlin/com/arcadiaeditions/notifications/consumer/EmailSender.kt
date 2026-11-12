package com.arcadiaeditions.notifications.consumer

/** Outbound port to the e-mail provider. T-16 and T-20 reuse it. */
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.hexagonal.SecondaryPort
interface EmailSender {

    /** Never throws for a provider outcome: see [EmailResult]. */
    fun send(message: EmailMessage): EmailResult
}

data class EmailMessage(val to: String, val subject: String, val body: String)

data class EmailResult(val sent: Boolean, val reason: String? = null) {
    companion object {
        fun sent() = EmailResult(true)

        fun refused(reason: String) = EmailResult(false, reason)
    }
}
