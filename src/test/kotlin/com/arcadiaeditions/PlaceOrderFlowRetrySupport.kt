package com.arcadiaeditions

import com.arcadiaeditions.payments.processing.adapter.driven.processor.FakePaymentProcessor
import com.arcadiaeditions.payments.processing.application.port.driven.jpa.PaymentRepository
import com.arcadiaeditions.payments.processing.application.port.driven.processor.AuthorizationRequest
import com.arcadiaeditions.payments.processing.application.port.driven.processor.CaptureRequest
import com.arcadiaeditions.payments.processing.application.port.driven.processor.PaymentProcessor
import com.arcadiaeditions.payments.processing.application.port.driven.processor.PaymentProcessorResult
import com.arcadiaeditions.payments.processing.application.port.driven.processor.VoidRequest
import com.arcadiaeditions.payments.processing.application.service.BackoffSleeper
import com.arcadiaeditions.payments.processing.domain.PaymentStatus
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import org.awaitility.Awaitility.await
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.modulith.events.core.EventPublicationRepository
import org.springframework.modulith.events.core.TargetEventPublication
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * T-59: what the retry loop did, in order: each processor authorization and each backoff wait, with the Payment as it
 * was committed at that moment (read on another thread, so no transaction of the caller is joined).
 */
class RetryLoopRecorder(private val paymentRepository: ObjectProvider<PaymentRepository>) {

    sealed interface Step

    /** One processor authorization: the attempt asked for, and the committed Payment during the call. */
    data class Authorize(val attemptCount: Int, val status: PaymentStatus?, val committedAttemptCount: Int?) : Step

    /** One backoff wait asked by reauthorizePayment, and the committed Payment while it waits. */
    data class Backoff(val wait: Duration, val status: PaymentStatus?, val committedAttemptCount: Int?) : Step

    val steps: MutableList<Step> = CopyOnWriteArrayList()

    /** The order the loop runs for: set by a test, or by the first authorization. */
    @Volatile var orderId: String? = null

    fun reset() {
        steps.clear()
        orderId = null
    }

    fun authorizing(request: AuthorizationRequest) {
        if (orderId == null) orderId = request.orderId
        val (status, attempts) = committed(request.orderId)
        steps.add(Authorize(request.attemptCount, status, attempts))
    }

    fun backoff(wait: Duration) {
        val (status, attempts) = orderId?.let { committed(it) } ?: (null to null)
        steps.add(Backoff(wait, status, attempts))
    }

    private fun committed(orderId: String): Pair<PaymentStatus?, Int?> =
        CompletableFuture.supplyAsync {
                val payment = paymentRepository.getObject().findByOrderId(orderId)
                payment?.status to payment?.attemptCount?.toInt()
            }
            .get()
}

/** Delegates to FakePaymentProcessor, recording every authorization in [RetryLoopRecorder]. */
class RecordingPaymentProcessor(private val delegate: FakePaymentProcessor, private val recorder: RetryLoopRecorder) :
    PaymentProcessor {

    override fun authorize(request: AuthorizationRequest): PaymentProcessorResult {
        recorder.authorizing(request)
        return delegate.authorize(request)
    }

    override fun capture(request: CaptureRequest): PaymentProcessorResult = delegate.capture(request)

    override fun void(request: VoidRequest): PaymentProcessorResult = delegate.void(request)
}

/**
 * The backoffSleeper of PaymentsProcessingServiceImpl (T-57): records the wait instead of sleeping, so a test does not
 * wait 5 s + 15 s.
 */
class RecordingBackoffSleeper(private val recorder: RetryLoopRecorder) : BackoffSleeper {
    override fun sleep(wait: Duration) = recorder.backoff(wait)
}

/** Recorder, recording processor (replaces FakePaymentProcessor as the PaymentProcessor) and recording backoff. */
@TestConfiguration(proxyBeanMethods = false)
class RetryLoopTestConfiguration {

    @Bean fun retryLoopRecorder(paymentRepository: ObjectProvider<PaymentRepository>) = RetryLoopRecorder(paymentRepository)

    @Bean
    @Primary
    fun recordingPaymentProcessor(delegate: FakePaymentProcessor, recorder: RetryLoopRecorder) =
        RecordingPaymentProcessor(delegate, recorder)

    /** Injected into the optional `backoffSleeper: BackoffSleeper` constructor parameter of the payments service. */
    @Bean fun recordingBackoffSleeper(recorder: RetryLoopRecorder) = RecordingBackoffSleeper(recorder)

    @Bean
    fun retryLoopEvents(
        repository: EventPublicationRepository,
        publisher: ApplicationEventPublisher,
        transactionManager: PlatformTransactionManager,
    ) = RetryLoopEvents(repository, publisher, transactionManager)
}

/**
 * Reads the event publication registry: which events the flow published (per listener target), and redelivers an event
 * the way the outbox does, waiting until every listener has handled it. event_publication outlives the context (and
 * the fixed Clock stamps every publication alike), so publications are told apart by their identifier.
 */
class RetryLoopEvents(
    private val repository: EventPublicationRepository,
    private val publisher: ApplicationEventPublisher,
    transactionManager: PlatformTransactionManager,
) {
    private val transaction = TransactionTemplate(transactionManager)

    private fun all(): List<TargetEventPublication> =
        repository.findCompletedPublications() + repository.findIncompletePublications()

    /** Identifiers of every publication stored so far. */
    fun mark(): Set<UUID> = all().map { it.identifier }.toSet()

    /** Events of [type] published after [mark] to a listener whose target contains [target], in no set order (a fixed Clock stamps them alike). */
    fun <T : Any> since(mark: Set<UUID>, type: Class<T>, target: String, matching: (T) -> Boolean = { true }): List<T> =
        all()
            .filter { it.identifier !in mark && type.isInstance(it.event) && it.targetIdentifier.value.contains(target) }
            .map { type.cast(it.event) }
            .filter(matching)

    /** Publishes [event] in a transaction (as a redelivery) and waits until each of its listeners has completed it. */
    fun redeliver(event: Any, timeout: Duration) {
        val before = mark()
        transaction.executeWithoutResult { publisher.publishEvent(event) }
        await().atMost(timeout).until {
            val incomplete = repository.findIncompletePublications().filter { it.identifier !in before && it.event == event }
            val completed = repository.findCompletedPublications().filter { it.identifier !in before && it.event == event }
            incomplete.isEmpty() && completed.isNotEmpty()
        }
    }
}

/** Listener targets of the retry loop, as the event publication registry names them. */
object RetryLoopTargets {
    const val RETRY_PAYMENT = "PaymentsProcessingServiceEventsListener.retryPayment"
    const val REAUTHORIZE_PAYMENT = "PaymentsProcessingServiceEventsListener.reauthorizePayment"
    const val RELEASE_ON_DECLINED = "PaymentsProcessingEventsListener.onPaymentDeclined"
    const val RELEASE_ON_RETRY_EXHAUSTED = "PaymentsProcessingEventsListener.onPaymentRetryExhausted"
    /** The cancelOrder listener, on StockReleased. */
    const val CANCEL_ORDER = "CatalogInventoryEventsListener.onStockReleased"
    /** Any listener. */
    const val ANY = ""
}

/** Every stored field of a Payment that a no-op must leave as it was (version included: not even saved again). */
fun com.arcadiaeditions.payments.processing.domain.Payment.snapshot(): List<Any?> =
    listOf(
        version, status, attemptCount, providerReference, authorizedAt, declinedAt, failedAt, capturedAt, voidedAt,
        voidFailedAt, failureReason,
    )
