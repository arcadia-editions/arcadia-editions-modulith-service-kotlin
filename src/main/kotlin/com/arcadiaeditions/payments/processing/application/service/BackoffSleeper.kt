package com.arcadiaeditions.payments.processing.application.service

import java.time.Duration

/**
 * Waits the backoff before a reauthorization (ZFL header: 5 s before attempt 2, 15 s before attempt 3). A named port,
 * so only a bean of this type replaces the default (tests record the wait instead of sleeping).
 */
fun interface BackoffSleeper {
    fun sleep(wait: Duration)

    companion object {
        /** Sleeps the calling thread. */
        val THREAD_SLEEP: BackoffSleeper = BackoffSleeper { Thread.sleep(it) }
    }
}
