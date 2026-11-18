VERDICT: FIX
SLICE: S5   RANGE: 85de5b1..824015c
SUITE: `bash .zenwave/handoff/place-order-flow/docker-run-it.sh mvn -q verify -Dmaven.test.failure.ignore=true` (from .zenwave/work/review-S5, JAVA_HOME sdkman current)
       → surefire 149/149 passed (ArchitectureTest 8/8 included); failsafe 59 run, 56 passed, 3 errors, 0 skipped.
       PlaceOrderFlowReservationExpiredIntegrationTest 2/2 passed (paymentStarted 15.9 s, noPayment 0.9 s).
       Errors, all outside S5, same cause `Scenario ... returned <false> for input of <[]> within 30 seconds`:
         - PlaceOrderFlowRetryIntegrationTest.c12PendingPaymentFailedIsRetriedAndEndsInOrderConfirmationSent (:218)
         - PlaceOrderFlowRetryExhaustedIntegrationTest.c13PendingPaymentFailedAtTheLimitEndsInOrderCancelledNotificationSent (:242)
         - PlaceOrderFlowLateAuthorizationIntegrationTest.lateAuthorizationForCancelledOrderIsRejectedAndVoidedAndTheOrderStaysCancelledOnce (:161)
       Rerun each method alone, through the lock: 3/3 pass. Rerun PlaceOrderFlowRetryIntegrationTest whole class alone:
       c12 fails again. So not flaky: order dependent and reproducible (see MODEL AXIS, first item).
BASELINE: untouched (`git diff --stat 85de5b1..824015c -- '*.zfl' '*.zdl' '*openapi*.yml' '*asyncapi*.yml' 'src/test/resources/data*'` empty)

MODEL AXIS
- FIX FIRST  (outside S5: T-59 / T-72 tests or the flow-test harness) PlaceOrderFlowRetryIntegrationTest.kt:213, PlaceOrderFlowRetryExhaustedIntegrationTest.kt:242, PlaceOrderFlowLateAuthorizationIntegrationTest.kt:156
  The suite is red. In each of the 3 classes the second test that takes a `Scenario` fails whenever both tests run in one
  JVM; alone it passes. The flow itself runs to the end: the log shows sendOrderConfirmation for ORD-012 (08:33:28.741)
  and voidPayment/releaseStock for ORD-022. Scenario captures nothing (`input of <[]>`). Where the first test takes no
  Scenario (ReservationExpired), the second passes. Cause UNCONFIRMED. Likely: Scenario capture stays bound to the
  first test's context, and `@DirtiesContext(AFTER_EACH_TEST_METHOD)` swaps the context after it. To settle it, run one
  class with the order of its tests reversed, or with the second test's Scenario replaced by `events.since(...)` plus
  awaits. Not caused by S5 code: no S5 file is in these tests' paths.
- NOTE  T-67 ReservationExpiredJob.kt:40 / InventoryServiceImpl.kt:209  The ZFL/ZDL timer fires once, "10 minutes after
  OrderCreated". The poll runs expireReservation again on every run (30 s by default) for each due order whose payment
  has started, until payment moves the reservation out of RESERVED. Behavior is right: getPaymentStatus answers STARTED
  for any existing Payment, so a repeat never releases (test reservationExpiredJobTwiceTest proves it). The cost is
  bounded in the normal path (payment ends in about 2 minutes, or confirmOrder makes the reservation CONFIRMED). It is
  unbounded only for a stuck payment (T-75: AUTHORIZATION_REQUESTED left behind) or a fixture like C-14: one read of
  Payment per run, for each one. A one-shot fire needs a persisted "fired" mark that StockReservation does not have in
  the ZDL. If the person wants that, it is a model gap (zenwave-model-gap). No proposal written (read-only review).
- NOTE  T-68 PlaceOrderFlowReservationExpiredIntegrationTest.kt:267-270  It assumes the job runs within
  PROCESSOR_TIMEOUT (5 s) of the first processor call. On a slow machine attempt 1 times out first, and the asserts
  `stalledOrderIds contains orderId` and `recorder.steps == [Authorize(1)]` fail. That gives a false red, never a false
  green: STARTED holds in every later state too. Measured here: about 0.2 s of the 5 s. Acceptable. Say it in the test
  if it ever flakes.
- NOTE  T-67 side effect in T-68 :267  In the payment-started test the same job run also releases C-30 (ORD-030, no
  Payment), and its cancellation flow runs in the background, not asserted. Harmless, because the later asserts filter
  by orderId.
- Checked, no finding:
  - T-66 PaymentsProcessingServiceImpl.kt:317: read-only, NO_PAYMENT only when no Payment, STARTED with status for any
    Payment (AUTHORIZATION_REQUESTED included, ZDL :163-164). No save, no event; tests C-01/C-14/C-30 compare
    repository state before and after.
  - T-67 InventoryServiceImpl.kt:198-217 (ZFL :280-297, ZDL :150-158):
    - late fire (no RESERVED line): no-op, payments not asked;
    - NO_PAYMENT: RESERVED lines go to RELEASED (RESERVATION_EXPIRED), guarded by ensureCanExpireReservation, with
      exactly one StockReleased with all lines;
    - STARTED: no change, no event;
    - redelivery: no-op;
    - the job calls expireReservation once per order (ORD-002 two lines: one call).
    The `release` extraction keeps releaseStock's behavior (its tests pass).
  - T-67, the race (timer reads NO_PAYMENT, then authorizePayment commits): it leads to the late-authorization path,
    which is S6 and modeled.
  - T-68: both ends proven against persisted state and the outbox, with the right listener targets:
    - noPayment: OrderCancelled, then OrderCancelledNotificationSent RESERVATION_EXPIRED; the C-14 Payment snapshot
      unchanged; second run and C-11 are no-ops;
    - paymentStarted: due and STARTED while attempt 1 is in flight, nothing released; 3 timeouts with recorded
      backoffs 5 s/15 s, then RETRY_EXHAUSTED, then release PAYMENT_RETRY_EXHAUSTED, then CANCELLED with one e-mail.
    The baseline-1 race is covered.
  - Nothing out of scope: no new field, endpoint or event.

STANDARDS AXIS
- SHOULD FIX  PlaceOrderFlowReservationExpiredIntegrationTest.kt:104, :202, :340  `TIMER_TARGET =
  "CatalogInventoryEventsListener.onStockReleased"` is the cancelOrder listener, not the timer. At :340 it reads
  "StockReleased to TIMER_TARGET is PAYMENT_RETRY_EXHAUSTED", the opposite of what it checks (the release came from
  payment, not the timer). The sibling test names the same target `CANCEL_ORDER`
  (PlaceOrderFlowLateAuthorizationIntegrationTest.kt:113). Rename it, and put it in RetryLoopTargets
  (PlaceOrderFlowRetrySupport.kt:152), which already holds the shared listener targets, so it is not duplicated.
- NOTE  InventoryServiceImpl.kt:10-12, :30  catalog.inventory now calls payments' PaymentsProcessingService directly,
  as the ZDL `@calls` says. It imports the port DTO and the domain enum, like orders → inventory does
  (OrdersCheckoutServiceImpl.kt:10-14). ArchitectureTest.applicationModulesAreValid passes: no new cycle or violation,
  and catalog.inventory>payments is already among the tolerated P-04 cycles. The in-memory config builds the payments
  context the same way orders builds the inventory one (ServicesInMemoryConfig.kt, convention followed).
- Checked, no finding: the KDoc of ReservationExpiredJob is updated; log messages, `@Transactional(readOnly = true)`
  and test style match the code around them. The generated "compile stub" markers are replaced as the board asks.
  Commits carry the AI-Agent/AI-Model/AI-Orchestrator trailers.

PROPOSALS: none written (read-only review). Possible model gap: one-shot ReservationExpired, see the MODEL AXIS NOTE on
  T-67.
NOT CHECKED: whether the 3 order-dependent failures also happen at 85de5b1 or before T-80 (no full run of the base). The
  production scheduler with the job enabled (it is off in tests, T-38).
