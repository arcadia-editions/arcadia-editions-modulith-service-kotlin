VERDICT: PASS
SLICE: S7   RANGE: da48b53..5b77ad9 (fix round r2, fix T-87 = 2fcd98c, merge 5b77ad9)
SUITE: (clone review-S7 @ 5b77ad9) JAVA_HOME=$HOME/.sdkman/candidates/java/current;
       bash .zenwave/handoff/place-order-flow/docker-run-it.sh mvn -q verify -Dmaven.test.failure.ignore=true
       > scratchpad/review-S7-r2-suite.log
       → 14:01-14:06, exit 0. surefire 151/151 pass. failsafe 59/59 pass (0 err, 0 fail, 0 skip), report files fresh (14:03-14:06).
         Green includes Retry, RetryExhausted, RetryDeclined, RetryPendingFailed, RetryExhaustedPending,
         ReservationExpired, LateAuthorization{CancelledOrder,StockNotHeld}, NotificationFailed (3.7 s). No reruns needed, nothing flaky.
BASELINE: untouched. git diff --stat da48b53..5b77ad9 -- '*.zfl' '*.zdl' '*openapi*.yml' '*asyncapi*.yml' 'src/test/resources/data*' → empty.

PREVIOUS FINDINGS (review-S7.md)
- FIX FIRST (S2's, retry ×5 StaleObjectStateException)  resolved: T-80 merged (824015c); the 5 retry tests pass in this suite.
- SHOULD FIX paymentCapturedAt not asserted  resolved: PlaceOrderFlowNotificationFailedIntegrationTest.kt:144-147, awaited
  = now, same shape as PlaceOrderFlowCompletedIntegrationTest.kt:199-201.
- NOTE emailSender.sent assertion proves only the fake  resolved: dropped; :158 replaces it.
- NOTE event.notificationId not compared  resolved: :121 passes it, :158 compares it to the stored Notification.notificationId.
- SHOULD FIX (standards) no SKU-006 counters  resolved: :103-106 read before the stimulus, :141-143 assert
  (available-1, reserved, sold+1), like Completed :124-126, :175-177.

MODEL AXIS
- OK  T-73/T-87  path notificationFailed end to end (place-order-flow.zfl:207-213 sendOrderConfirmation @failure → 
  NotificationDeliveryFailed, end :336-345). recordPaymentCaptured (:218-220) is a sibling of the refused command and is now
  proven untouched. Event fields: orderId, customerId, type ORDER_CONFIRMATION, channel, recipient, reason, failedAt,
  notificationId. Notification FAILED, sentAt null, failedAt/failureReason set. Order CONFIRMED, reservation CONFIRMED,
  Payment CAPTURED, Fulfillment SCHEDULED, stock counters. Counters are relative, not computed the way the code computes
  them. Nothing out of scope.
- NOTE  other merges in range touching S7's module: T-79 (7c12d6a) changes only the ORDER_CANCELLED hold text
  (NotificationsConsumerServiceImpl.kt:116). Not on the S7 path, and the suite is green.

STANDARDS AXIS
- OK  T-87 diff (+17/-3, one test file) matches the sibling flow tests: same counter idiom and comment (T-43), same awaited
  paymentCapturedAt, no new helpers. FakeEmailSender stays in use for the refusal hook (:81, :86). Commit 2fcd98c is on
  afk/place-order-flow, unsigned, with AI-Agent / AI-Model / AI-Orchestrator trailers.

PROPOSALS: none
NOT CHECKED: other slices' changes in the range (T-75, T-80, T-82, T-83, T-85, T-86) judged only on whether they break the
  suite: they don't. Known open items (T-81/P-12 A, data.sql A-06 processor_error, T-84 HITL) were not judged; none showed
  up as a failure. Board still shows T-87 [/] at 5b77ad9; the orchestrator closes it.
