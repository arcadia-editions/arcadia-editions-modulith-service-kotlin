VERDICT: PASS (no FIX FIRST; slice end outcome unproven until T-43 runs PlaceOrderFlowRetryIntegrationTest green)
SLICE: S4   RANGE: 7e239c1..6748bcc (slice commits 8a057f9 T-22, 6916947 T-23, 0e43f3d T-41, 50c237c T-24)
SUITE: not run (orchestrator instruction). Log suite-1f8c0ff.log (1f8c0ff..6748bcc = docs/tasks.md only):
       surefire 94/94 green; failsafe 48 run, non-flow ITs green.
       PlaceOrderFlowRetryIntegrationTest: PENDING T-43 (failed at :92, absolute stock precondition, before any
       flow assertion ran; not counted against the slice).
BASELINE: untouched (git diff --stat 7e239c1..6748bcc on *.zfl *.zdl *openapi*.yml *asyncapi*.yml
          src/test/resources/data* = empty)

Checked OK against model lines:
- T-22 retryPayment (zfl:124-130, zdl:65-69, 89-91, 133-148): load by orderId, guard FAILED, attempts < 3 ->
  attemptCount+1, stays FAILED, one PaymentRetried (retriedAt = clock); at 3 -> RETRY_EXHAUSTED, one
  PaymentRetryExhausted (reason = failureReason, exhaustedAt). Loop gives 3 authorization attempts (A-03).
- T-23 authorizePayment on PaymentRetried (zfl:132-140, P-01 D): listener maps orderId/customerId; service reuses
  FAILED Payment, takes amount/currency/email/paymentMethodId from it, clears previous failure, one event per
  outcome; other existing states no-op; load in service, not listener (hexagonal).
- T-41: no Payment + placeholder input -> rejected, nothing created (service level).
- T-24: flow test exists for end completed via retry (fake pm_card_processorErrorOnce, attemptCount 2).

MODEL AXIS
- SHOULD FIX  T-41 PaymentsProcessingServiceEventsListener.kt:24-31 (+ PaymentsProcessingServiceImpl.kt:59-67)
  The catch does not do what its comment says. Input: PaymentRetried for an orderId with no Payment. `require`
  throws IAE out of the @Transactional service proxy, which participates in the listener's REQUIRES_NEW tx
  (@ApplicationModuleListener, spring-modulith-events-api 2.0.0 ApplicationModuleListener.java:45-47). Spring marks
  the tx rollback-only on participating failure (spring-tx 7.0.1 AbstractPlatformTransactionManager.java:138
  globalRollbackOnParticipationFailure = true, :901-903); the listener returns normally, commit throws
  UnexpectedRollbackException (:803/:937): the publication stays incomplete and is resubmitted, the opposite of
  line 28. Also the catch is broad: any IAE on the FAILED-reuse path would be caught; if the rollback were avoided
  with a blanket noRollbackFor, that path would commit AUTHORIZATION_REQUESTED with no outcome event.
  Fix: make the no-Payment PaymentRetried case a logged no-op in the service (return, no throw), or a dedicated
  exception with noRollbackFor (pattern already used: OrdersCheckoutServiceImpl.kt:40), and catch only that.
  Confirmed by reading code + Spring sources, not run; T-41 test covers the service only. Settle by a Spring test
  publishing PaymentRetried(ORD-NO-PAYMENT) and checking incomplete event publications.
  Model: zdl:85-87, P-01 D "reject otherwise".
- SHOULD FIX  T-41 PaymentsProcessingServiceImpl.kt:63  `input.amount.signum() > 0` is a rule no model declares
  (zdl:56-63 amount required only; orders-checkout zdl:36, :69 unitPrice has no min). Input: OrderCreated with
  total 0 (unitPrice 0) -> IAE on the S1 OrderCreated path (OrdersCheckoutEventsListener.kt:15 does not catch) ->
  publication retried forever, no Payment, order cancelled later by reservation expiry. The blank-string checks
  alone detect the placeholders (mapper constants "", PaymentsProcessingServiceEventsListenerMapper.kt:63-66).
- NOTE  T-22/T-23  redelivered PaymentFailed retries twice; redelivered OrderCreated while FAILED is taken for a
  retry; PaymentFailed redelivered after RETRY_EXHAUSTED throws. Known, P-05 open: not counted.
- NOTE  T-23 PaymentsProcessingServiceTest.kt (authorizePaymentOnPaymentRetried*)  only the approved outcome of a
  retry is tested; no focused test for a retry that fails again (zfl:139, PaymentFailed with the Payment's
  attemptCount) or is declined (zfl:137). The C-12 fixture is the pre-retry state (attemptCount 1), so attempt > 1
  reaches the processor only in the flow test. S5 flow test T-25 covers fail-again end to end.
- NOTE  T-24 PlaceOrderFlowRetryIntegrationTest.kt:146  the retry is proven only via payment.attemptCount == 2
  (set only by retryPayment) plus the fake; no assertion that PaymentFailed/PaymentRetried were published.
  Sufficient once it runs.

STANDARDS AXIS
- NOTE  PaymentsProcessingServiceEventsListener.kt:27  only listener in the project that catches; all others let
  exceptions propagate to the publication registry. Same root as the first SHOULD FIX.
- NOTE  PaymentsProcessingServiceEventsListenerMapper.kt:59-66  sentinel placeholder values ("" / ZERO) in a DTO
  with non-null fields: sanctioned by P-01 D, documented; the sentinel check must stay on the strings (see above).
- OK: commits unsigned, conventional prefix, AI-Agent/AI-Model/AI-Orchestrator trailers; `as Integer` casts and
  fully qualified JUnit assertions match surrounding code; fake changes stay behind arcadia.fake-adapters.enabled.

PROPOSALS: none written (redelivery already P-05)
NOT CHECKED: PlaceOrderFlowRetryIntegrationTest (PENDING T-43); the listener rollback behavior was not run;
             model lint not rerun (models untouched).
