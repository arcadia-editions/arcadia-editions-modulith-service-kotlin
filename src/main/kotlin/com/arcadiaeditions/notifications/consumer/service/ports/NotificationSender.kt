package com.arcadiaeditions.notifications.consumer.service.ports

/**
 * Outbound port to the notification channel (Q-10: EMAIL only, inline subject/body templates). A
 * stub adapter is the only bean for now.
 */
interface NotificationSender {

    fun send(request: SendRequest): SendResult
}

data class SendRequest(val recipient: String, val subject: String, val body: String)

enum class SendOutcome {
    SENT,
    FAILED,
}

data class SendResult(val outcome: SendOutcome, val providerReference: String? = null, val failureReason: String? = null)
