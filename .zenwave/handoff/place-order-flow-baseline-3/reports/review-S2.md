VERDICT: FIX
SLICE: S2   RANGE: 04f786c..035e289   (reviewer: claude-opus; read-only clone .zenwave/work/review-S2 at 035e289)
SUITE: docker-run-it.sh mvn -q verify -Dmaven.test.failure.ignore=true (complete run, 00:14-00:23, reports in review-S2/target)
       → unit 142 run, 3 failed (InventoryServiceTest.reservationExpiredJob{,Twice,OncePerOrder}Test: known T-67/T-68)
       → IT 55 run, 2 failures + 5 errors:
         known: PlaceOrderFlowCaptureFailedIntegrationTest (T-65), PlaceOrderFlowReservationExpiredIntegrationTest (T-67/T-68)
         new:   PlaceOrderFlowRetryIntegrationTest (2/2), PlaceOrderFlowRetryDeclinedIntegrationTest (1/1),
                PlaceOrderFlowRetryExhaustedIntegrationTest (2/2): ConditionTimeoutException, end event never arrives
       reruns alone: RetryExhausted#c13... PASSED (flaky in the suite); RetryDeclined and RetryExhausted#retriesExhausted...
         fail again with the same StaleObjectStateException; the PlaceOrderFlowRetryIntegrationTest rerun is void (context
         failed loading data.sql, duplicate key product_sku_key: another clone was using the shared Postgres, see NOT CHECKED);
         its suite run shows the same StaleObjectStateException in both tests
BASELINE: untouched (no *.zfl, *.zdl, *openapi*.yml, *asyncapi*.yml, src/test/resources/data* in the range). Commits
       unsigned, AI-Agent/AI-Model/AI-Orchestrator trailers present.

MODEL AXIS
- FIX FIRST  T-57 PaymentsProcessingServiceImpl.kt:265-299 (merge at :141)  reauthorizePayment always fails on JPA.
  The listener transaction loads the Payment (:265, version v0, now managed). The REQUIRES_NEW block (:276-287) loads it
  again, commits AUTHORIZATION_REQUESTED and attemptCount+1, which makes it v1. saveAuthorizationOutcome then
  save()s that detached v1 copy in the outer persistence context, which still holds v0, so Hibernate merge throws
  StaleObjectStateException (ObjectOptimisticLockingFailureException "Row was already updated or deleted by another
  transaction", stack PaymentsProcessingServiceImpl.kt:141 <- :299 <- PaymentsProcessingServiceEventsListener.kt:23).
  Input: any PaymentRetried with a matching attemptCount (C-12, the pm_card_processorErrorOnce / ErrorThenDecline / Error flows).
  The result: the Payment is stuck at AUTHORIZATION_REQUESTED with attemptCount 2. PaymentAuthorized, PaymentDeclined
  and PaymentFailed are never emitted, so reauthorizePayment[authorized|declined|failed] never happens
  (place-order-flow.zfl:138-146, payments-processing-domain-model.zdl:130-136). The retry path is dead past
  attempt 1 and the publication stays incomplete. PaymentsProcessingServiceTest misses it because the in-memory
  repository has no @Version. Fix options: don't keep a managed copy in the outer transaction (do the pre-backoff check
  in its own read-only transaction, or detach/refresh before the outcome save), or save the outcome onto the managed
  instance reloaded after the inner commit. Then add a JPA-backed test of reauthorizePayment (the flow ITs are that test).
- FIX FIRST  T-59 PlaceOrderFlowRetry{,Declined,Exhausted}IntegrationTest  5 of 5 tests fail in the suite: four are
  the FIX FIRST above (the scenario waits 30 s for OrderConfirmationSent / OrderCancelledNotificationSent). The fifth,
  RetryExhausted#c13PendingPaymentFailedAtTheLimitEndsInOrderCancelledNotificationSent, is FLAKY. In the suite its
  flow reached sendOrderCancelledNotification (notification insert at 00:19:15.315), then nothing more was logged
  and the event was not seen within 30 s. Alone it passed (85 s). UNCONFIRMED cause: possibly the shared-DB concurrency
  in NOT CHECKED. Settle it by rerunning in the suite once the lock is global. The test code itself reads right
  against T-59: attempts 1/2/3, backoffs 5 s/15 s recorded while FAILED, stale redeliveries snapshot-equal, no new events.
- SHOULD FIX T-56/T-57 PaymentsProcessingServiceImpl.kt:156-158, :265-267  retryPayment and reauthorizePayment throw
  NoSuchElementException when no Payment exists. The ZDL says "No-op unless FAILED ..." (payments-processing-domain-model.zdl:131, :139),
  and voidPayment in the same range treats no Payment as a no-op. The throw keeps the publication incomplete forever,
  and it is replayed on every restart (republish-outstanding-events-on-restart: true). Seen in this suite: 8 stale
  incomplete PaymentRetried publications from earlier runs (left by the FIX FIRST above) replayed by every context that
  republishes (the repository ITs, OrdersCheckoutApiIntegrationTest): 32 NoSuchElementException, each time occupying all 8
  async threads. Make both a logged no-op, as the removed authorizePayment comment explained (rollback-only, resubmitted forever).
- SHOULD FIX T-57 PaymentsProcessingServiceImpl.kt:272  The backoff is Thread.sleep(5 s / 15 s) on the
  @ApplicationModuleListener thread. It runs inside the listener's open REQUIRES_NEW transaction, after a query, so a
  JDBC connection is held. It is followed by up to 30 s of processor call. The async pool is the shared
  ThreadPoolTaskExecutor of AsyncConfiguration (Boot defaults: 8 core threads, unbounded queue), so 8 payments in
  backoff stall every module listener of the application (checkout, inventory, fulfillment, notifications). During a
  drop with a processor outage, that is the normal case. The models say only "5 and 15 seconds of backoff"
  (zfl:62-65, zdl:140-141), not how. UNCONFIRMED under load (pool or Hikari exhaustion not run). Options: schedule the
  reauthorization (TaskScheduler / a due time picked up by a job) instead of sleeping, or at least a dedicated executor for it.
- NOTE  T-57 backoffSleeper (point to judge).
  Does Spring use the default when no bean exists? Yes, confirmed. Every flow IT without RetryLoopTestConfiguration
  starts its context and passes (Completed, Declined, FulfillmentFailed, StockGone, VoidFailed). Spring's Kotlin
  constructor support treats an optional parameter it cannot resolve as absent, so `{ Thread.sleep(it) }` is used.
  Does the test bean replace it? Yes, confirmed. RecordingBackoffSleeper implements Function1<Duration, Unit>, which
  matches `(Duration) -> Unit` (Function1<? super Duration, Unit>). In RetryDeclined, reauthorizePayment went from
  request to error in 111 ms (00:29:45.332 -> .443), with no 5 s sleep.
  Fragile, though: any future bean of a compatible function type is injected silently, and two such beans fail
  startup (NoUniqueBeanDefinitionException is still thrown for an optional parameter). See STANDARDS.
- NOTE  T-57 AUTHORIZATION_REQUESTED on reauthorize (point to judge): proposal-worthy, not a code finding. The ZDL
  gives authorizePayment the in-flight state explicitly (zdl:126-127, the P-02 decision of 2026-11-17), but
  reauthorizePayment only `from: FAILED, to: [AUTHORIZED, DECLINED, FAILED]` (zdl:135). Its comment says "commits
  before calling the processor" without naming a state. The code (and board T-57, "as T-52") commit FAILED ->
  AUTHORIZATION_REQUESTED, a transition the model does not declare. This is the same class of gap as A-01/P-02, and
  the projections' state machine is incomplete without it. No other command behaves differently either way:
  getPaymentStatus is STARTED for any Payment, and voidPayment is a no-op for both states. Options for the proposal:
  A, add `@transition(from: FAILED, to: AUTHORIZATION_REQUESTED)` and `@transition(from: AUTHORIZATION_REQUESTED,
  to: [AUTHORIZED, DECLINED, FAILED])` to reauthorizePayment, as authorize has (recommended: matches the code, no
  task changes). B, commit only attemptCount+1 and stay FAILED (the attemptCount guard still makes redelivery a
  no-op). Not written here (reviewer is read-only): orchestrator to open P-11 with zenwave-model-gap. Related known
  issue: T-75 / P-10 (a throwing processor leaves AUTHORIZATION_REQUESTED).
- NOTE  T-59 RetryLoopEvents (point to judge). Reading EventPublicationRepository (modulith events.core SPI) in a
  test is acceptable: it is the only place that tells which listener got which event and that a redelivery completed.
  Two caveats. The KDoc says "redelivers ... the way the outbox does", but publishEvent creates a new publication
  rather than resubmitting the stored one. That still proves listener idempotency, which is what T-59 asks. Also,
  event_publication outlives the contexts: 12 unfinished publications were listed at shutdown, some targeting the
  pre-T-58 `authorizePayment(PaymentRetried)`.
  The invented failedAt (2026-10-01T11:57:15Z) of the stale C-13 PaymentFailed(attempt 2) is harmless. It is an input
  only, the data.sql header :69 says a stale attemptCount is "input only (no-op)", asRetryPaymentInput reads only
  orderId and attemptCount, and nothing asserts it. Not a finding.
- NOTE  T-57 PaymentsProcessingServiceImpl.kt:304-309  isAttemptToRetry does not check MAX_PAYMENT_ATTEMPTS. A
  PaymentRetried(3) on a Payment FAILED at 3 would run attempt 4. retryPayment cannot emit that and the model puts
  the limit in retryPayment (zdl:138-145), so this is defensive only.
- NOTE  T-58 PaymentsProcessingServiceEventsListener.kt:22  renaming the listener method changes the publication
  target id. Incomplete PaymentRetried publications recorded under `...authorizePayment(PaymentRetried)` in a real
  database will not reach reauthorizePayment after deploy. That only matters if there is a deployed database.
- T-56 retryPayment (:159-177), T-58 mapper/listener wiring and authorizePayment creation-only (:90-101), backoffBefore
  (5 s before attempt 2, 15 s before attempt 3), and the double check after the backoff (:279) match zfl:124-146 and
  zdl:119-145. Unit tests (44 in PaymentsProcessingServiceTest, 2 mapper tests) pass and assert observable state,
  not recomputed values.

STANDARDS AXIS
- NOTE  PaymentsProcessingServiceImpl.kt:119  stale comment "A-01 (P-02): the outcome state is set here, though the ZDL
  transition only names AUTHORIZATION_REQUESTED". A-01 is retired (data.sql:100), and zdl:127/:135 now name the outcome
  states. The comment now misleads in the shared saveAuthorizationOutcome.
- NOTE  PaymentsProcessingServiceImpl.kt:33  a raw `(Duration) -> Unit` constructor parameter injected by type (see
  MODEL NOTE). The code next to it uses named ports (PaymentProcessor, Clock) for what tests replace. A small
  `fun interface BackoffSleeper` (or reusing Clock with a scheduler) would follow that convention and remove the
  accidental-injection risk.
- NOTE  PaymentsProcessingServiceImpl.kt:161 vs :306  `input.attemptCount?.toInt()` vs `input.attemptCount.toInt()`
  for the same non-null DTO field. Cosmetic.
- Commits, branch flow and trailers follow AGENTS.md. No generated-always file hand-edited (listener and mapper are
  generated-once, edited as T-58 asked).

PROPOSALS: none written (read-only). Recommended: P-11 reauthorizePayment in-flight state (MODEL NOTE above).
NOT CHECKED:
- docker-run-it.sh: LOCK=".zenwave/handoff/place-order-flow/docker-it.lock" is relative, so each clone locks in its
  own tree. Clones running in parallel (review-S2, T-65, T-67, T-71, ...) all use the same Postgres on 15432 (test
  compose "already running ... Skipping TestContainers"). Evidence: the 00:31 rerun failed loading data.sql (duplicate
  key product_sku_key). Make the lock path absolute (main repo) before trusting flaky verdicts. The C-13 flake may
  come from this.
- Pool or thread exhaustion under concurrent retries (SHOULD FIX T-57 backoff) was not load-tested.
- PlaceOrderFlowRetryIntegrationTest was not rerun cleanly alone (see SUITE).
