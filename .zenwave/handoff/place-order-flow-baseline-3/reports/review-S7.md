VERDICT: FIX
SLICE: S7   RANGE: da48b53..3a3066d
SUITE: (clone review-S7 @ 3a3066d) bash .zenwave/handoff/place-order-flow/docker-run-it.sh mvn -q verify -Dmaven.test.failure.ignore=true
       → 13:11-13:29, 17m27s (lock wait included). surefire 146/146 pass. failsafe 56 run, 0 fail, 6 err:
         known: PlaceOrderFlowReservationExpiredIntegrationTest (T-68, running), :161 ConditionTimeout
         not on list: PlaceOrderFlowRetryIntegrationTest (2), PlaceOrderFlowRetryExhaustedIntegrationTest (2),
           PlaceOrderFlowRetryDeclinedIntegrationTest (1): Scenario ConditionTimeout 30 s, cause
           StaleObjectStateException on Payment (reauthorization). Each class rerun once alone (-Dit.test=<class>):
           fails again, 5/5. Not flaky.
         PlaceOrderFlowNotificationFailedIntegrationTest (T-73, first run): PASS.
       Earlier runs void, not counted: run 1 killed by the usage-limit interruption; run 2 (07:40) held the old
       clone-relative lock, overlapped another clone on the shared DB 15432 ("relation checkout_order does not exist"),
       and ran mvn twice when docker-run-it.sh was edited mid-run (07:45); run 3 died on a syntax error from another
       mid-wait edit of the script before mvn started.
BASELINE: untouched (no *.zfl, *.zdl, *openapi*.yml, *asyncapi*.yml, src/test/resources/data* in the range).
       Range = 1 file: src/test/kotlin/com/arcadiaeditions/PlaceOrderFlowNotificationFailedIntegrationTest.kt (+146).

Verdict is FIX only because the suite is red, and the red is not S7's: the 5 retry errors are the S2 review FIX FIRST
(T-57 stale merge on JPA), fixed by T-80, which is not in 3a3066d. S7's own diff has no FIX FIRST.

MODEL AXIS
- FIX FIRST (owned by S2, not S7)  PlaceOrderFlowRetry{,Exhausted,Declined}IntegrationTest  5/5 fail in the suite and
  alone, StaleObjectStateException on Payment during reauthorizePayment. S7 adds only a test file and cannot cause this.
  Settled by T-80 merged. Recheck at the next head that has it.
- SHOULD FIX  T-73 PlaceOrderFlowNotificationFailedIntegrationTest.kt:126-133  the test says it checks "shape of C-29"
  (KDoc :48) and that "the business outcome is untouched" (:126), but it never asserts Fulfillment.paymentCapturedAt.
  C-29 (data.sql:37, :233) has paymentCapturedAt set. recordPaymentCaptured (zfl:215-221) listens to the same
  PaymentCaptured as the refused sendOrderConfirmation (zfl:202-213), so it is the one sibling outcome a notification
  failure could plausibly disturb. Add the awaited check as Completed does (PlaceOrderFlowCompletedIntegrationTest.kt:199-201).
- NOTE  T-73 :144  `emailSender.sent.filter { it.body.contains(orderId) }).isEmpty()` is true by construction: with
  refusalReason set, FakeEmailSender.send returns before `sent.add` (FakeEmailSender.kt:25-27). It proves the fake, not
  the code. Harmless, but sentAt null + FAILED (:137, :141) already carry the point.
- NOTE  T-73 :107-113  event.notificationId is not compared to the stored Notification.notificationId (ZDL
  NotificationDeliveryFailed.notificationId, notifications-consumer-domain-model.zdl:136). Every other field is checked.
- OK  path end to end: PaymentCaptured > sendOrderConfirmation refused > Notification FAILED (failedAt, failureReason
  smtp_rejected = A-10, data.sql:109; sentAt null) > NotificationDeliveryFailed with type ORDER_CONFIRMATION, channel,
  recipient, reason, failedAt (end notificationFailed, zfl:336-345). Order CONFIRMED, reservation CONFIRMED, Payment
  CAPTURED, Fulfillment SCHEDULED asserted. Implementation (NotificationsConsumerServiceImpl.kt:37-65) emits exactly one
  event per outcome, and a redelivery returns the existing row. Nothing out of scope.

STANDARDS AXIS
- SHOULD FIX  PlaceOrderFlowNotificationFailedIntegrationTest.kt:119-145  no inventory counters. All 10 other flow
  tests read the position before the stimulus and assert counters relative to it (e.g. Completed :122-126, :175-177).
  SKU-006 is stable in this context: the expiry job is off in the test profile (application-test.yml:15), so a relative
  available-1 / reserved / sold+1 check is deterministic. Follow the convention or say in a comment why not.
- OK  setup follows the flow-test convention: @DirtiesContext, @MockitoBean ExternalizationConfiguration, republish off
  (T-43), fake hook set in @BeforeEach and reset in @AfterEach (as FulfillmentFailed :87-92), C-29 input, commit wait
  before reads. Commit e4b5ba1 on afk branch, unsigned, AI-Agent / AI-Model / AI-Orchestrator trailers present.

PROPOSALS: none
NOT CHECKED: other slices' merges in the range: none (the only merge is 3a3066d, T-73). ReservationExpired not
  rerun (known, T-68). Lock harness: before 07:45 docker-run-it.sh used a lock path relative to the clone, so it did not
  serialize clones. Fixed by making it absolute. Edits to the script while runs wait or hold it break those runs
  (bash re-reads the file). Treat suites of other reviews that overlapped 07:40-07:46 as suspect.
