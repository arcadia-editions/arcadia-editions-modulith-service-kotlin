VERDICT: FIX (by the rule: suite not green. The failures come from S2/T-57, which T-80 fixes after this head. S4's own code has no FIX FIRST.)
SLICE: S4   RANGE: 104f2ea..8148f39   TASKS: T-64, T-65 (claude-sonnet)   REVIEWER: claude-opus
SUITE: from .zenwave/work/review-S4. `mvn -q verify -Dmaven.test.failure.ignore=true` ran under docker-run-it.sh.
  - surefire (00:28 run, complete): 143 run, 3 failed. All 3 are known: InventoryServiceTest.reservationExpiredJobTest, .reservationExpiredJobTwiceTest, .reservationExpiredJobOncePerOrderTest (T-67).
  - failsafe: the 00:28 and 07:42 runs are void. The old per-clone lock let them share the test DB with other runs ("relation product does not exist", missing rows). I reran failsafe only (`-Dtest=none`) under the new absolute lock, at 08:34: 55 run, 1 failure, 5 errors.
    - known: PlaceOrderFlowReservationExpiredIntegrationTest (assertEndState:209, Payment not null; T-67/T-68)
    - NOT on the S4 known list: PlaceOrderFlowRetryIntegrationTest (2), PlaceOrderFlowRetryExhaustedIntegrationTest (2), PlaceOrderFlowRetryDeclinedIntegrationTest (1). Each is a Scenario ConditionTimeout at 30 s, caused by StaleObjectStateException on Payment. Rerun alone (`-Dit.test=PlaceOrderFlowRetry*IntegrationTest`): 5/5 errors again, so not flaky.
    - PlaceOrderFlowCaptureFailedIntegrationTest (T-65): PASSES on its new assertions.
BASELINE: untouched (`git diff --stat 104f2ea..8148f39` on *.zfl, *.zdl, *openapi*.yml, *asyncapi*.yml and src/test/resources/data* is empty)

MODEL AXIS
- SHOULD FIX  T-65 src/test/kotlin/com/arcadiaeditions/PlaceOrderFlowCaptureFailedIntegrationTest.kt:168
  "The order is cancelled once" (board T-65) is only a comment. Nothing proves it:
  - The one notification and one e-mail at :213-220 hold no matter how many OrderCancelled events arrive. sendOrderCancelledNotification deduplicates per order (NotificationsConsumerServiceImpl.kt:101-104).
  - `cancelled hasSize(1)` at :207 doesn't prove it either: a second OrderCancelled hits CANCELLED and cancelFulfillment does nothing (FulfillmentShippingServiceImpl.kt:111-115).
  - So an OrderCancelled emitted twice (zfl:296-310 says "no event when already CANCELLED") would pass unseen.
  Fix: record OrderCancelled the same way and assert exactly one for orderId.
- NOTE  T-64 FulfillmentShippingServiceImpl.kt:108-129 matches zfl:311-320 and zdl:116-119 (CancelFulfillmentInput zdl:76-80):
  - loads by orderId; returns null when there is none; does nothing for CANCELLED/FAILED, with no event;
  - SCHEDULED or READY_TO_SHIP goes to CANCELLED, with cancellationReason = input.reason (A-11, data.sql:110);
  - emits exactly one FulfillmentCancelled; EventsMapper.kt:30 maps reason from cancellationReason;
  - listener OrdersCheckoutEventsListener.kt:24-27 calls cancelFulfillment, and the mapper maps orderId and reason by name;
  - unit tests cover C-25, C-06 (READY_TO_SHIP) and C-21/C-26/C-10, and assert the stored state and the event fields;
  - redelivery is idempotent.
- NOTE  T-64 FulfillmentShippingServiceImpl.kt:117, point judged: cancelledAt comes from the Clock. That is correct: CancelFulfillmentInput has no timestamp (zdl:76-80), and scheduledAt/failedAt follow the same pattern. Once listener lag exists in production, Fulfillment.cancelledAt may differ from Order.cancelledAt. The models chose this, so it is not a code issue.
- NOTE  T-64 FulfillmentShippingServiceImpl.kt:116 `ensureCanCancelFulfillment` can never fail after the guard at :111-115, which checks the same set. Harmless.
- No out-of-scope fields, events or endpoints.

STANDARDS AXIS
- NOTE  PlaceOrderFlowCaptureFailedIntegrationTest.kt:90-104, point judged: the class-local recorder (a nested @TestConfiguration plus a plain @EventListener) is acceptable.
  - It is auto-detected, and the passing test proves that.
  - It records at publish time, inside the publishing transaction, not after commit. That is fine because the assertion runs after the CANCELLED row is visible.
  - The neighbouring convention is a separate support file with `@TestConfiguration(proxyBeanMethods = false)` and `@Import` (PlaceOrderFlowRetrySupport.kt:91-103). Move it there if a second flow test needs it.
- NOTE  PlaceOrderFlowCaptureFailedIntegrationTest.kt:14 `import java.util.concurrent.CopyOnWriteArrayList` sits among the com.arcadiaeditions imports. The rest of the file is sorted (ktlint import order; spotless+ktlint is configured at pom.xml:340-347 but not bound to verify).
- NOTE  same file:101 uses fully-qualified `@org.springframework.context.annotation.Bean` while every other annotation is imported. :94 has annotation and fun on one line. :90 and :194 are longer than 120 columns.
- T-64 main and unit-test code read like the surrounding methods (guard, transition, save, emit pattern; the existing style of fully-qualified Assertions).

OTHER SLICES (breakage in range, not S4's to judge)
- FIX FIRST (owned by S2)  PlaceOrderFlowRetry*IntegrationTest (5): StaleObjectStateException on Payment during reauthorization. This is the S2 review FIX FIRST on T-57 (stale merge on JPA), fixed by T-80 (824015c), which merged after 8148f39. Nothing new for S4.

PROPOSALS: none
NOT CHECKED: Kafka externalization of FulfillmentCancelled (ExternalizationConfiguration is mocked in the ITs).
ENV: my first failsafe rerun deadlocked: an outer and an inner docker-run-it.sh both took the new absolute lock. I killed only my own processes (34300, 22320) and removed my own stale lock (created 07:42) at about 08:20. No other holder was touched.
