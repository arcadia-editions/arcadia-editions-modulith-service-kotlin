VERDICT: PASS
SLICE: S2 stockGone   RANGE: 7e239c1..2984d99 (S2 commits: 026be4c T-14, 257f7bc T-15, 56b989f T-16, f7b893d + b9e3397 T-17)
SUITE: not rerun (orchestrator order). Read the run at 1f8c0ff, `mvn -q verify`, log logs/suite-1f8c0ff.log. 1f8c0ff..2984d99 = docs/tasks.md only.
       surefire 94/94 green, incl. OrdersCheckoutServiceTest 11/11, NotificationsConsumerServiceTest 11/11,
       OrdersCheckoutApiControllerTest 4/4.
       failsafe 48 run: OrdersCheckoutApiIntegrationTest 5/5 green, testStartOrderCheckout_409 among them
       (work/verify/target/failsafe-reports).
       PlaceOrderFlowStockGoneIntegrationTest: PENDING T-43, not counted, not run. It timed out at :95 with Scenario
       seeing [] events. In the same log (:5418-5424), sendStockUnavailableNotification ran on task-7 with the mapped
       input and inserted the notification. So the path ran, and only the event capture failed.
BASELINE: untouched (`git diff --stat 7e239c1..2984d99 -- '*.zfl' '*.zdl' '*openapi*.yml' '*asyncapi*.yml' 'src/test/resources/data*'` empty)

MODEL AXIS
- NOTE  T-14 OrdersCheckoutServiceImpl.kt:40  `noRollbackFor` (the S1 note said UNCONFIRMED until T-17) is now
  confirmed by the suite log.
  - reserveStock joins the transaction and returns UNAVAILABLE with no write (InventoryServiceImpl.kt:47-58,
    all-or-nothing).
  - The StockUnavailable publication is registered, the transaction commits, and the AFTER_COMMIT listener runs
    (log :5421-5424). The 409 IT is green.
  - Limit: this holds only while no caller wraps startOrderCheckout in its own @Transactional. That caller's rollback
    rules would see the exception and drop the publication. Today only the REST controller calls it.
- NOTE  T-14 OrdersCheckoutServiceImpl.kt:65-76  Two defensive gaps:
  - `else ->` takes every outcome other than RESERVED.
  - `?: mutableListOf()` would emit a StockUnavailable with no items. notifications-consumer zdl:71 says
    `minlength(1)`.
  Neither can happen with the current reserveStock, which always fills the unavailable result. No action.
- NOTE  T-14/T-15 orders-checkout zdl:98  The model declares `Order` as the return. The code answers with
  StockUnavailableException carrying the emitted event (A-05, P-03 A). This follows the decisions.
  OrdersCheckoutService.kt:14 (generated port) does not say it throws, which is correct, since generated files are not
  hand-edited.
- NOTE  T-16 NotificationsConsumerServiceImpl.kt:89  The status goes straight to SENT, with no PENDING
  (zdl:93 `@transition(to: PENDING)`). This is A-01 / open P-02, the same as its siblings. Not counted.
- NOTE  T-16 NotificationsConsumerServiceImpl.kt:83-84  The e-mail body names the orderId, but A-05 creates no Order
  for it, and it does not name the sold-out SKU or edition. ZFL:195-196 says "so they can look for alternatives".
  The models don't fix the body, and the text matches the approved fixture (data.sql:176). For the person, not a code
  finding.
- Checked, OK (by reading, plus the generated MapStruct impls in work/verify/target):
  - T-14: reserveStock is called and its result drives the branch (zfl:82-87). No Order, StockReservation or Payment
    is written. Exactly one StockUnavailable goes out, with orderId, customerId, customerEmail and
    unavailableItems{sku, requestedQuantity, availableQuantity} (zdl:126-135). The unit test asserts the C-07
    literals (SKU-003, 1, 0) and that the order and reservation counts are unchanged.
  - T-15: 409 with body {orderId, unavailableItems[sku, requestedQuantity, availableQuantity]}, as P-03 A says. The
    advice is scoped to OrdersCheckoutApiController and does not overlap WebExceptionsTranslator. No other endpoint,
    field or event was added.
  - T-16:
    - Listener: OrdersCheckoutEventsListener @ApplicationModuleListener -> sendStockUnavailableNotification. The mapper
      maps orderId, customerId, customerEmail and items, 4/4.
    - Notification: STOCK_UNAVAILABLE, EMAIL, recipient = customerEmail, skus from the items, createdAt/sentAt from
      the Clock.
    - Events: SENT -> StockUnavailableNotificationSent only, with all 7 fields mapped from Notification
      (zdl:112-121). A refused e-mail -> FAILED + NotificationDeliveryFailed only (T-42).
    - Redelivery: findByOrderIdAndType returns the existing notification, with no e-mail and no event. Unit test on
      NTF-007 (C-07).
  - T-17: the flow test exists and asserts the stockGone end:
    - StockUnavailableNotificationSent (orderId, customerId, skus, channel, recipient, sentAt);
    - Notification SENT with one e-mail;
    - no order, reservation or payment (zfl:197, 264);
    - SKU-003 counters 0/0/10 unchanged (InventoryPosition/3.json).
    No fixture can move SKU-003, so these absolute counters are safe under a shared context, unlike PlaceOrderFlowRetry
    (T-43). Proof of a run: PENDING T-43.
  - No test recomputes the code's values or mocks the unit under test.

STANDARDS AXIS
- NOTE  OrdersCheckoutApiExceptionHandler.kt:10-20  StockUnavailableBody and StockUnavailableItemBody are written by
  hand inside the handler file. Every other web DTO is generated from the OpenAPI. They are needed while the contract
  has no 409 (P-03: "contract not edited"). Consequence: orders-checkout-openapi.yml:44-52 still documents only
  201/400, so clients generated from it don't know the 409. If the contract ever gains the 409, replace these with the
  generated DTO.
- NOTE  OrdersCheckoutApiControllerTest.kt:53-69  The test calls the handler by hand, so it proves the mapping but not
  the advice wiring. testStartOrderCheckout_409 covers the wiring and is green.
- Checked, OK:
  - The code reads like its neighbours: same redelivery guard, id prefix (NTF-/ORD-), Clock use and save-then-publish
    order as sendOrderConfirmation and sendOrderCancelledNotification.
  - The exception lives in the inbound port package of its module, and no other module uses it.
  - The flow test sits in the root test package like the other PlaceOrderFlow*IntegrationTest.
  - All S2 task commits are unsigned, with AI-Agent, AI-Model and AI-Orchestrator trailers.

PROPOSALS: none.
NOT CHECKED:
- PlaceOrderFlowStockGoneIntegrationTest run (PENDING T-43; rerun once T-43 lands to close T-17).
- Suite not rerun by me (orchestrator order); evidence is the 1f8c0ff log and reports.
- Behaviour with a transactional caller of startOrderCheckout (none exists).
- Concurrent checkouts on the last unit of a SKU.
