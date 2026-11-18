VERDICT: FIX
SLICE: S1   RANGE: 85de5b1..7499242
SUITE: `JAVA_HOME=$HOME/.sdkman/candidates/java/current bash .zenwave/handoff/place-order-flow/docker-run-it.sh mvn -q verify -Dmaven.test.failure.ignore=true` from .zenwave/work/review-S1 (23:26:41-23:32:32, 5:51)
       → surefire 115 tests, 3 failures, 2 errors; failsafe 49 tests, 1 failure; 0 skipped, no flaky.
       Every failure is a known one: PaymentsProcessingServiceTest.voidPaymentFrom{Failed,AuthorizationRequested}Test (T-60),
       InventoryServiceTest.reservationExpiredJob{,Twice,OncePerOrder}Test (T-67),
       PlaceOrderFlowReservationExpiredIntegrationTest:209 "expected: null but was: Payment#1352" (no-Payment assertion, T-67/T-68).
       Not rerun alone: only expected failures. PlaceOrderFlowCompletedIntegrationTest green (1.76 s).
BASELINE: untouched (no *.zfl, *.zdl, *openapi*.yml, *asyncapi*.yml, src/test/resources/data* in the range)

MODEL AXIS
- FIX FIRST  T-52 src/main/kotlin/com/arcadiaeditions/payments/processing/application/service/PaymentsProcessingServiceImpl.kt:67-68, 86-90
  A processor that throws (not a timeout) is rethrown after AUTHORIZATION_REQUESTED is already committed. The listener
  transaction rolls back, no outcome event is emitted, and the incomplete OrderCreated publication, when resubmitted, hits
  the no-op at :87-90 (status != FAILED). The Payment stays AUTHORIZATION_REQUESTED for good. getPaymentStatus will answer
  STARTED (T-66), so expireReservation will not release either (T-67). The order is left CREATED with its stock held and the
  flow never ends. ZFL place-order-flow.zfl:111-112 says a technical error "on our side or the processor's" is the
  failed outcome (PaymentFailed), and :64 says "once payment has started, payment owns the end". This can happen with the
  fake today: pm_card_processorNoAnswer throws when released (FakePaymentProcessor.kt:62-71). That is what
  PlaceOrderFlowReservationExpiredIntegrationTest's @AfterEach triggers: the IT failed at 0.9 s with the Payment row present.
  Fix: in callProcessor, map an ExecutionException (processor exception) to PaymentProcessorResult.failed(<reason>), the
  same way as the timeout, so the outcome is FAILED + PaymentFailed. The reason code is the person's call: A-06 lists
  only processor_timeout as a technical code; reuse it, or add a code to A-06 (HITL). Add a unit test with a throwing processor.
- NOTE  T-52 PaymentsProcessingServiceImpl.kt:83-110 vs payments-processing-domain-model.zdl:120 ("Creation only ... supersedes
  P-01 D"): it still reuses a FAILED Payment (P-01 D). The board gives that to T-58, so it is not a finding for this slice.
- NOTE  T-52 timeout path matches ZDL :121-123 and the board (FAILED, processor_timeout, one PaymentFailed, attemptCount 1):
  authorizePaymentProcessorTimeoutTest (200 ms stand-in). The commit-before-call is shown across threads by T-55.
- NOTE  T-53 FulfillmentShippingServiceImpl.kt:94-106 matches fulfillment-shipping-domain-model.zdl:105-110 (no Fulfillment,
  not SCHEDULED, already set: no-op; no event). The listener was already wired. Mapper test present.
- NOTE  T-54 FulfillmentShippingServiceImpl.kt:76-86 matches ZDL :112-114 (refused, no transition, no event). Tested with
  C-04 (refused) and C-24 (allowed).
- NOTE  T-55 PlaceOrderFlowCompletedIntegrationTest.kt: proves ZFL :336-345 completed and :215-221. The in-flight snapshots
  are deterministic: authorize is observed after the REQUIRES_NEW commit; capture is observed before PaymentCaptured
  exists, so paymentCapturedAt must be null. Reads run on another thread (no caller transaction joined). paymentCapturedAt
  is awaited before the assert. markReadyToShip after the end is checked. The refusal is checked only by exception type
  (same type as the state guard). It is unambiguous only because the Fulfillment is asserted SCHEDULED (see STANDARDS on T-54).
- NOTE  S3 (T-61, T-62): nothing broken. Their unit tests are green, and so is every flow IT except the known one.

STANDARDS AXIS
- SHOULD FIX  FulfillmentShippingServiceImpl.kt:78-85 (T-54). It reuses InvalidStateTransitionException with
  currentState SCHEDULED and allowedStates [SCHEDULED]. The message reads "operation 'markReadyToShip' not allowed from
  state SCHEDULED, allowed states: [SCHEDULED]": it contradicts itself and does not name the cause (payment not captured).
  The refusal is a precondition, not a state transition, and callers cannot tell it from the state guard. The code next to
  it throws a plain IllegalStateException with a reason for a refusal that is not a transition
  (PaymentsProcessingServiceImpl.kt:246). Throw a dedicated exception, or one whose message names paymentCapturedAt.
  FulfillmentTransitions.kt looks generated: do not edit it.
- SHOULD FIX (UNCONFIRMED)  PaymentsProcessingServiceImpl.kt:19-20, 83, 121 (T-52). The class-level @Transactional, plus
  the listener's own REQUIRES_NEW transaction, stays open for up to 30 s during the processor call, and the REQUIRES_NEW
  block takes a second connection. Spring Boot 4.0.0 sets hibernate provider_disables_autocommit for Hikari with
  auto-commit false (seen in HibernateJpaConfiguration), so Hibernate should take the outer connection lazily. Nothing in
  authorizePayment runs SQL in the outer transaction before the call. Whether Spring Modulith 2.0's listener
  infrastructure touches the DB in that transaction first is not confirmed. If it does, 8 listener threads (Boot's default
  executor) need 16 connections against Hikari's default 10, and stalled calls starve the pool. To settle it: watch Hikari
  active connections during a pm_card_processorNoAnswer call, or run 5+ of them at once. The ZDL (:121-123) asks for this
  commit-then-call shape; holding nothing during the call would need the listener to be non-transactional (HITL design question).
- NOTE  FakePaymentProcessor.kt:12-13: the KDoc says "throws, so the authorization rolls back and payment never resolves".
  Since T-52, AUTHORIZATION_REQUESTED stays committed, and with the 30 s timeout the stalled call ends as FAILED
  (processor_timeout). The @AfterEach comment in PlaceOrderFlowReservationExpiredIntegrationTest.kt:105 is stale too (T-68 rewrites it).
- NOTE  PaymentsProcessingServiceImpl.kt:55 PROCESSOR_TIMEOUT duplicates FakePaymentProcessor.PROCESSOR_TIMEOUT (A-06 code in two places).
- NOTE  PaymentsProcessingServiceImpl.kt:48-49: the virtual-thread executor is never shut down (no @PreDestroy). An
  abandoned call is interrupted on timeout, so the cost is small.
- NOTE  The orchestrator's merge commits (3c12441, d72a329, 1de0966, 61ac850, 104f2ea, 7499242) have no AI-Agent / AI-Model /
  AI-Orchestrator trailers (AGENTS.md). The task and board commits have them.

PROPOSALS: none written. The processor-exception reason code (A-06) is a decision for the person, raised with the FIX FIRST above.
NOT CHECKED: the connection hold under load (needs a load run or Hikari metrics). The real processor timeout behavior
(T-33, HITL). Reviewer model claude-opus-5-5 may be the same model as the T-52 and T-55 implementer ("claude-opus"): the
skill asks for a different one.
