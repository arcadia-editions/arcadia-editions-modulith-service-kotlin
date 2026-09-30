package com.arcadiaeditions.payments.processing.service.impl

import com.arcadiaeditions.payments.processing.domain.*
import com.arcadiaeditions.payments.processing.domain.events.*
import com.arcadiaeditions.payments.processing.events.*
import com.arcadiaeditions.payments.processing.repository.jpa.*
import com.arcadiaeditions.payments.processing.service.*
import com.arcadiaeditions.payments.processing.service.dtos.*
import com.arcadiaeditions.payments.processing.service.impl.mappers.*
import com.arcadiaeditions.payments.processing.service.ports.*
import java.math.*
import java.time.*
import java.util.UUID
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Service Implementation for managing [Payment]. */
@Service
@Transactional
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.layered.ApplicationLayer
open class PaymentsProcessingServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val eventPublisher: EventPublisher,
    private val clock: Clock,
    private val paymentProvider: PaymentProvider,
) : PaymentsProcessingService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val paymentsProcessingServiceMapper: PaymentsProcessingServiceMapper =
        PaymentsProcessingServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    companion object {
        /** Q-05: max 3 payment attempts, no backoff. */
        const val MAX_ATTEMPTS: Int = 3
    }

    /**
     * See [com.arcadiaeditions.catalog.inventory.service.impl.InventoryServiceImpl.toBoxed]: ZenWave
     * generates numeric fields as boxed `java.lang.Integer`, which Kotlin treats as a distinct type
     * from `kotlin.Int`.
     */
    private fun Int.toBoxed(): Integer = this as Integer

    /**
     * G-PP-02: `authorizePayment` is a create (`@transition(to: AUTHORIZATION_REQUESTED)`).
     * Idempotency is by `orderId` (unique on [Payment]): a payment already exists for this order
     * means `OrderCreated` was redelivered and this command already ran - return the existing
     * result without calling the provider or emitting again. Otherwise generate `paymentId`, set
     * `attemptCount = 1`, call [PaymentProvider.authorize] and emit exactly one of
     * PaymentAuthorized/PaymentDeclined/PaymentFailed depending on the outcome.
     */
    override fun authorizePayment(input: AuthorizePaymentInput): Payment {

        log.debug("Request authorizePayment: {}", input)

        val existing = paymentRepository.findByOrderId(input.orderId)
        if (existing != null) {
            log.debug("Idempotent redelivery of authorizePayment for order {}", input.orderId)
            return existing
        }

        val payment = paymentsProcessingServiceMapper.update(Payment(), input)
        payment.paymentId = "PAY-${UUID.randomUUID()}"
        payment.status = PaymentStatus.AUTHORIZATION_REQUESTED
        payment.attemptCount = 1.toBoxed()

        val now = Instant.now(clock)
        val result =
            paymentProvider.authorize(
                AuthorizeRequest(
                    orderId = input.orderId,
                    paymentMethodId = input.paymentMethodId,
                    amount = input.amount,
                    currency = input.currency,
                )
            )
        return applyAuthorizeResult(payment, result, now)
    }

    /**
     * G-PP-03/Q-05: load by `paymentId`. Any redelivery once the payment is no longer FAILED
     * (already retried, exhausted, or resolved by a later step) is an idempotent no-op (Q-13) -
     * unlike the other commands there's no single "already done" target status to compare against,
     * since "not FAILED anymore" covers every state retryPayment could legitimately be re-delivered
     * into. If `attemptCount < 3`, bump it, move to AUTHORIZATION_REQUESTED, emit PaymentRetried,
     * then immediately re-authorize the same payment (see [reauthorize]). Otherwise (3rd attempt
     * already spent): the ZDL's `retryPayment` `@transition` only declares `to:
     * AUTHORIZATION_REQUESTED` - it has no target status for the RETRY_EXHAUSTED outcome, which is
     * a model gap (flagged in the gap report as a new finding rather than edited into the generated
     * `PaymentTransitions`). We set `status = RETRY_EXHAUSTED` directly and emit
     * PaymentRetryExhausted.
     */
    override fun retryPayment(input: RetryPaymentInput): Payment {

        log.debug("Request retryPayment: {}", input)

        val payment =
            paymentRepository.findByPaymentId(input.paymentId)
                ?: throw NoSuchElementException("Payment not found: ${input.paymentId}")

        if (payment.status != PaymentStatus.FAILED) {
            log.debug(
                "Idempotent redelivery of retryPayment for payment {}: status is {}",
                input.paymentId,
                payment.status,
            )
            return payment
        }
        PaymentTransitions.ensureCanRetryPayment(payment)

        val now = Instant.now(clock)
        val currentAttempt = payment.attemptCount!!.toInt()

        if (currentAttempt >= MAX_ATTEMPTS) {
            payment.status = PaymentStatus.RETRY_EXHAUSTED
            val saved = paymentRepository.save(payment)
            eventPublisher.onPaymentRetryExhausted(eventsMapper.asPaymentRetryExhausted(saved, saved.failureReason, now))
            return saved
        }

        payment.attemptCount = (currentAttempt + 1).toBoxed()
        payment.status = PaymentStatus.AUTHORIZATION_REQUESTED
        val retried = paymentRepository.save(payment)
        eventPublisher.onPaymentRetried(eventsMapper.asPaymentRetried(retried, now))

        // The ZFL models the next step as a separate `when PaymentRetried do authorizePayment`,
        // deferred in the ZDL (authorize is a create-transition, not reusable "from" FAILED) with a
        // hint to wire it as an in-process listener on PaymentRetried. We instead call it directly,
        // synchronously, right here:
        //  1. This module's in-memory test harness (ServicesInMemoryConfig/PaymentsProcessingServiceTest)
        //     wires `EventPublisher` as a plain `InMemoryEventPublisher`, not Spring's
        //     `ApplicationEventPublisher` - a real `@ApplicationModuleListener` on PaymentRetried
        //     would never run there, so "retry -> re-authorized" couldn't be asserted as one
        //     `retryPayment` call the way G-FT-02 expects.
        //  2. It still emits exactly one further event (PaymentAuthorized/Declined/Failed) in
        //     addition to PaymentRetried, so each conceptual step keeps its own single event; it's
        //     just not routed through a second Spring event round-trip.
        //  3. It avoids a window (crash/redelivery race) where the payment sits at
        //     AUTHORIZATION_REQUESTED with no listener ever picking it back up.
        return reauthorize(retried)
    }

    /** G-PP-03: re-authorizes the same [Payment] after a [PaymentRetried]. See [retryPayment]. */
    private fun reauthorize(payment: Payment): Payment {
        val now = Instant.now(clock)
        val result =
            paymentProvider.authorize(
                AuthorizeRequest(
                    orderId = payment.orderId!!,
                    // Payment does not persist paymentMethodId (not part of the ZDL aggregate), so
                    // the stub's paymentMethodId-triggered outcomes (pm_card_declined/pm_card_fail)
                    // can't be replayed on retry - tests drive this path with
                    // StubPaymentProvider.forcedAuthorizeOutcome instead.
                    paymentMethodId = "",
                    amount = payment.amount!!,
                    currency = payment.currency!!,
                )
            )
        return applyAuthorizeResult(payment, result, now)
    }

    private fun applyAuthorizeResult(payment: Payment, result: AuthorizeResult, now: Instant): Payment {
        return when (result.outcome) {
            AuthorizeOutcome.AUTHORIZED -> {
                payment.status = PaymentStatus.AUTHORIZED
                payment.authorizedAt = now
                payment.providerReference = result.providerReference
                payment.failureReason = null
                val saved = paymentRepository.save(payment)
                eventPublisher.onPaymentAuthorized(eventsMapper.asPaymentAuthorized(saved))
                saved
            }
            AuthorizeOutcome.DECLINED -> {
                payment.status = PaymentStatus.DECLINED
                payment.declinedAt = now
                payment.failureReason = result.failureReason
                val saved = paymentRepository.save(payment)
                eventPublisher.onPaymentDeclined(eventsMapper.asPaymentDeclined(saved))
                saved
            }
            AuthorizeOutcome.FAILED -> {
                payment.status = PaymentStatus.FAILED
                payment.failedAt = now
                payment.failureReason = result.failureReason
                val saved = paymentRepository.save(payment)
                eventPublisher.onPaymentFailed(eventsMapper.asPaymentFailed(saved))
                saved
            }
        }
    }

    /**
     * G-PP-04: fulfillment events carry no `paymentId` - resolved by `orderId`
     * (`Payment.orderId` is unique). Idempotent if already CAPTURED, guarded to AUTHORIZED. A
     * provider-side capture failure has no modeled target status of its own (the ZDL only declares
     * `AUTHORIZED -> CAPTURED`; the compensating `VOIDED` transition belongs to `voidPayment`,
     * triggered separately from `PaymentCaptureFailed`), so the payment stays AUTHORIZED and only
     * `failureReason` is updated.
     */
    override fun capturePayment(input: CapturePaymentInput): Payment {

        log.debug("Request capturePayment: {}", input)

        val payment =
            paymentRepository.findByOrderId(input.orderId)
                ?: throw NoSuchElementException("Payment not found for order: ${input.orderId}")

        if (payment.status == PaymentStatus.CAPTURED) {
            log.debug("Idempotent redelivery of capturePayment for order {}", input.orderId)
            return payment
        }
        PaymentTransitions.ensureCanCapturePayment(payment)

        val now = Instant.now(clock)
        val result =
            paymentProvider.capture(
                CaptureRequest(
                    orderId = input.orderId,
                    paymentId = payment.paymentId!!,
                    providerReference = payment.providerReference,
                )
            )

        return when (result.outcome) {
            CaptureOutcome.CAPTURED -> {
                payment.status = PaymentStatus.CAPTURED
                payment.capturedAt = now
                payment.providerReference = result.providerReference ?: payment.providerReference
                payment.failureReason = null
                val saved = paymentRepository.save(payment)
                eventPublisher.onPaymentCaptured(eventsMapper.asPaymentCaptured(saved))
                saved
            }
            CaptureOutcome.FAILED -> {
                payment.failureReason = result.failureReason
                val saved = paymentRepository.save(payment)
                eventPublisher.onPaymentCaptureFailed(
                    eventsMapper.asPaymentCaptureFailed(saved, result.failureReason, now)
                )
                saved
            }
        }
    }

    /**
     * G-PP-05: fulfillment/same-module events carry no `paymentId` either - resolved by `orderId`.
     * Idempotent if already VOIDED, guarded to `[AUTHORIZED, AUTHORIZATION_REQUESTED, FAILED]`. The
     * ZDL models a single outcome (`withEvents PaymentVoided`, no failure event) - voidPayment is
     * itself the compensating action, so a provider-side void failure isn't turned into a
     * fabricated status: it's thrown, leaving the event publication incomplete so Spring Modulith's
     * resubmission (X-03) retries the whole command later.
     */
    override fun voidPayment(input: VoidPaymentInput): Payment {

        log.debug("Request voidPayment: {}", input)

        val payment =
            paymentRepository.findByOrderId(input.orderId)
                ?: throw NoSuchElementException("Payment not found for order: ${input.orderId}")

        if (payment.status == PaymentStatus.VOIDED) {
            log.debug("Idempotent redelivery of voidPayment for order {}", input.orderId)
            return payment
        }
        PaymentTransitions.ensureCanVoidPayment(payment)

        val result =
            paymentProvider.void(
                VoidRequest(orderId = input.orderId, paymentId = payment.paymentId!!, providerReference = payment.providerReference)
            )
        if (result.outcome == VoidOutcome.FAILED) {
            throw IllegalStateException("Void rejected by provider for order ${input.orderId}: ${result.failureReason}")
        }

        payment.status = PaymentStatus.VOIDED
        payment.voidedAt = Instant.now(clock)
        val saved = paymentRepository.save(payment)
        eventPublisher.onPaymentVoided(eventsMapper.asPaymentVoided(saved, input.reason))
        return saved
    }
}
