VERDICT: FIX
SLICE: S2   RANGE: 04f786c..824015c   (fix round 2, T-80: d531fc7..824015c; reviewer: claude-opus; read-only clone .zenwave/work/review-S2 at 824015c)
SUITE: docker-run-it.sh mvn -q verify -Dmaven.test.failure.ignore=true (complete run, 08:10-08:28, global lock, reports in review-S2/target;
       copy in scratchpad s2r2-suite-reports)
       → unit 149 run, 0 failed
       → IT 59 run, 0 failures, 3 errors (ConditionTimeoutException, end event not seen within 30 s):
         PlaceOrderFlowRetryIntegrationTest#c12PendingPaymentFailedIsRetriedAndEndsInOrderConfirmationSent (T-59)
         PlaceOrderFlowRetryExhaustedIntegrationTest#c13PendingPaymentFailedAtTheLimitEndsInOrderCancelledNotificationSent (T-59)
         PlaceOrderFlowLateAuthorizationIntegrationTest#lateAuthorizationForCancelledOrderIsRejectedAndVoidedAndTheOrderStaysCancelledOnce (T-72, S6)
       reruns alone (one -Dit.test run, through the lock, 08:30-09:14): all 3 PASS (8 s, 9 s, 57 s).
       Not flaky: deterministic in the suite and in any run of the whole class. Cause below (FIX FIRST T-59).
       All other ITs green, previous known failures included (CaptureFailed, ReservationExpired, InventoryServiceTest).
       No StaleObjectStateException / ObjectOptimisticLockingFailureException anywhere in the reports.
BASELINE: untouched (no *.zfl, *.zdl, *openapi*.yml, *asyncapi*.yml, src/test/resources/data* in 04f786c..824015c).
       T-80 commits unsigned, AI-Agent/AI-Model/AI-Orchestrator trailers present. Tree clean.

PREVIOUS FINDINGS (review-S2.md)
- FIX FIRST T-57 stale merge in reauthorizePayment: RESOLVED. PaymentsProcessingServiceImpl.kt:267-269 reads the Payment
  in a REQUIRES_NEW, so the listener's persistence context never holds it; the outcome save (:301 -> :140) merges onto
  the committed v1. Run evidence: the C-12 IT log goes reauthorizePayment(attempt 2) -> confirmOrder ->
  capturePayment -> sendOrderConfirmation; the fresh-flow ITs through attempts 2 and 3 pass (processorErrorOnce,
  RetryDeclined, retriesExhausted). The new unit tests (PaymentsProcessingServiceTest.kt:545-597, VersionedPaymentStore)
  would fail on 035e289, by reading: outer context v0, detached v1 -> Repository.save throws
  ObjectOptimisticLockingFailureException (VersionedPaymentStore.kt:111).
- FIX FIRST T-59 retry flow ITs: PARTIAL. 3 of 5 pass in the suite. The 2 C-12/C-13 fixture tests still fail, now
  for a different cause: see the new FIX FIRST.
- SHOULD FIX T-56/T-57 NoSuchElementException with no Payment: NOT RESOLVED, known (T-81 blocked by P-12). Not a
  finding this round. Still visible: replays in InventoryPositionRepositoryIntegrationTest (39) and OrdersCheckoutApiIntegrationTest (46).
- SHOULD FIX T-57 Thread.sleep backoff on the listener thread: NOT RESOLVED, and not on the board (T-80 left it out).
  Still at PaymentsProcessingServiceImpl.kt:274. Since T-80, the outer listener transaction holds its connection
  across the sleep, and each reauthorize also takes a second connection per REQUIRES_NEW. Still UNCONFIRMED under
  load. Orchestrator: put it on the board or close it explicitly.
- NOTE T-57 backoffSleeper injection by function type: RESOLVED. fun interface BackoffSleeper (BackoffSleeper.kt:9),
  PaymentsProcessingServiceImpl.kt:33, RecordingBackoffSleeper (PlaceOrderFlowRetrySupport.kt:88). The ITs assert the
  recorded Backoff steps and pass.
- NOTE T-57 AUTHORIZATION_REQUESTED on reauthorize: RESOLVED in the model (P-11 A). payments-processing-domain-model.zdl:136-137
  (api 53687b5) declares FAILED -> AUTHORIZATION_REQUESTED -> [AUTHORIZED, DECLINED, FAILED]. The code matches.
- NOTE T-59 RetryLoopEvents, NOTE T-57 isAttemptToRetry without MAX_PAYMENT_ATTEMPTS (:305-310), NOTE T-58 rename of the
  listener target: unchanged, they stand as NOTEs. Stale `authorizePayment(PaymentRetried)` publications are still in
  the shutdown lists.
- STANDARDS NOTE stale A-01 comment in saveAuthorizationOutcome: RESOLVED (:117-119).
- STANDARDS NOTE raw (Duration) -> Unit: RESOLVED (above).
- STANDARDS NOTE attemptCount null handling: RESOLVED. attemptsOf (:313) is used at :111, :159, :273 and :306, and
  input.attemptCount.toInt() at both :160 and :307.
- NOT CHECKED relative lock path: RESOLVED. docker-run-it.sh:6 uses an absolute path next to the script, and the rerun was clean.

MODEL AXIS (new)
- FIX FIRST  T-59 PlaceOrderFlowRetryIntegrationTest.kt:70/74/194, PlaceOrderFlowRetryExhaustedIntegrationTest.kt:71/75/216
  The second Scenario test of each class can never see an event. Spring Modulith 2.0.0 PublishedEventsParameterResolver
  (the delegate of ScenarioParameterResolver, registered once per class by @EnableScenarios) caches its
  ThreadBoundApplicationListenerAdapter in an instance field: `initializeListener` returns early when `listener != null`.
  It adds the adapter only to the first context. @DirtiesContext(AFTER_EACH_TEST_METHOD) then gives test 2 a fresh
  context without the adapter, so Scenario captures nothing: "for input of <[]> within 30 seconds".
  The flows themselves complete. The c12 log reaches the notification insert for ORD-012 at 08:27:33.365, and the c13
  log reaches sendOrderCancelledNotification at 08:26:56.211. Each test passes when it is the only one in its class
  run. This also explains the earlier "flaky" C-13 verdict. It is deterministic, because JUnit's default method order
  is fixed (c12/c13 run second).
  Proof by contrast: ReservationExpired passes because its first test takes no Scenario, so the adapter is first
  bound to the second context.
  Fix options:
  A, one Scenario test per class (move c12 and c13 into their own classes; recommended, keeps the T-43 fresh context).
  B, drop Scenario from the second test and await the end state through the repositories and RetryLoopEvents.
  C, @DirtiesContext(BEFORE_EACH_TEST_METHOD) would not help: the cached listener is the problem, not the order.
- NOTE  T-72 (S6, not mine to judge) PlaceOrderFlowLateAuthorizationIntegrationTest.kt:79/83/145/204 has the same
  defect: lateAuthorizationForCancelled... fails in the suite (second test run), passes alone. Same fix.
- NOTE  T-80 PaymentsProcessingServiceImpl.kt:267-270  the pre-backoff read is a full read-write REQUIRES_NEW. A
  read-only template would state the intent. Cosmetic, no behavior change.
- T-80 otherwise matches zfl:138-146 and zdl:130-147. The second check after the backoff (:279-284) is kept, the
  redelivery no-op stays snapshot-equal (unit :580-582, ITs), and only the outcome's event is emitted.
  P-12 (docs/proposals/P-12.md) is correctly written: its zdl line refs (:131, :138, :141, :147, :157, :163) match api 53687b5.

STANDARDS AXIS (new)
- NOTE  VersionedPaymentStore.kt:95-97  `@Suppress("UNCHECKED_CAST")` twice in save (on the `if` and on the return).
  Only the second is needed. Cosmetic.
- NOTE  VersionedPaymentStore.kt (158 lines) hand-emulates Hibernate merge and flush. T-80 allowed this ("in-memory
  repository with @Version semantics"). It sits next to the generated in-memory repositories under test, as the code
  around it does. Its fidelity rests on the flow ITs, which now exercise the same path on Postgres. Acceptable.
- T-80 follows AGENTS.md: unsigned commits on the AFK branch, trailers, no generated-always file edited, and a proposal
  instead of a model edit for the signature gap.

PROPOSALS: none written by the reviewer. P-12 (by T-80) is open and blocks T-81.
NOT CHECKED:
- Pool/thread exhaustion under concurrent backoffs (SHOULD FIX carried over), not load-tested.
- Spring Modulith versions other than 2.0.0. Whether a later version re-binds the listener per context was not checked,
  and the fix should not depend on it.
