VERDICT: FIX
SLICE: S1   RANGE: 0c1fddd..2c897e8
SUITE: NOT RUN (environment). `bash .../docker-run.sh mvn -q verify -Dmaven.test.failure.ignore=true` never got past
       busy(): 127.0.0.1:5432 / :::5432 held by Rancher wslrelay (pid 10768) + host-switch (pid 21060) since 2026-11-12
       04:13, no container publishing 5432. Stopped on orchestrator order.
       Partial, no Docker: `mvn -q test -Dmaven.test.failure.ignore=true` (surefire only) -> 84 run, 0 fail, 0 err, 0 skip.
       Failsafe ITs (incl. PlaceOrderFlowCompletedIntegrationTest, OrdersCheckoutApiIntegrationTest) not run.
       ORPHANS (mine, TaskStop did not kill the child; kill was denied to me): docker-run.sh pid 34488 (04:08) and
       pid 23608 (08:06), both `mvn -q verify -Dmaven.test.failure.ignore=true` in .zenwave/work/review-S1, queued on
       docker.lock. Kill them before T-37, or they will run the suite twice in that checkout. Also queued, not mine:
       12744, 34276 (04:04-04:08), 384, 34504 (07:57-07:58).
BASELINE: untouched (`git diff --stat 0c1fddd..2c897e8 -- '*.zfl' '*.zdl' '*openapi*.yml' '*asyncapi*.yml' 'src/test/resources/data*'` empty)

MODEL AXIS
- FIX FIRST  T-13 (with T-31) PlaceOrderFlowCompletedIntegrationTest.kt:88-90,137-139; ReservationExpiredJob.kt:29
  ReservationExpiredJob runs in every Spring test context: AsyncConfiguration @EnableScheduling is active under
  profile "test", and fixedDelay PT30S with no initial delay fires as soon as the context starts. In T-13's context
  the clock is TestClock.NOW (12:00), so RES-014 (ORD-014, SKU-001, expires 11:55, data.sql:111) gets released:
  SKU-001 goes to 90/5/5, and that races the `before` 89/6/5 and end 88/6/6 assertions. Same in
  OrdersCheckoutApiIntegrationTest (system clock 2026-11-13, every RESERVED fixture expired): ORD-001 released ->
  StockReleased -> cancelOrder, and that races testGetOrder_200 `$.status == CREATED` (:108). T-13 (3bdb5ac) does not
  contain T-31 (d3fc664), so this pair has never run together; the worker's green IT predates the job.
  Confirmed by reading; the failure timing is UNCONFIRMED (the suite settles it). Fix: keep the job out of test
  contexts (e.g. @ConditionalOnProperty `catalog-inventory.reservation-expired-job.enabled`, false in
  application-test.yml). T-32 then calls releaseExpiredReservations() itself after advancing the MutableClock.
- NOTE  T-07/T-23 PaymentsProcessingServiceImpl.kt:48-62  An OrderCreated redelivered while the Payment is FAILED
  runs the authorization again: attemptCount stays the same, and a second PaymentFailed/PaymentAuthorized goes out for
  the same attempt. So T-07's "an OrderCreated for an orderId that already has a Payment does nothing" is no longer
  true for FAILED. Consequence of P-01 D: both listeners map to the same command and input, so code cannot tell them
  apart. Model gap with the same root as P-05; suggest adding it to P-05 (not written: read-only review).
- NOTE  T-09 OrdersCheckoutServiceImpl.kt:88-99, InventoryServiceImpl.kt:133-136  (S8 territory, for that review)
  (a) A PaymentAuthorized arriving after the order is CANCELLED throws InvalidStateTransitionException. The
  publication stays incomplete and is retried with no end. (b) The job checks only reservation status, not
  "not PaymentAuthorized" (zfl:236). So if the release beats confirmOrder, confirmStockReservation returns silently
  over RELEASED reservations and the order is CONFIRMED with no stock behind it, then cancelled from CONFIRMED.
- NOTE  T-23 PaymentsProcessingServiceEventsListenerMapper.kt:154-157 + PaymentsProcessingServiceImpl.kt:58
  A PaymentRetried with no Payment creates one with amount 0 and empty currency/email/method (the placeholders).
  P-01 D says "validate the other required fields after filling them from the Payment"; nothing validates. S4 scope.
- NOTE  T-14 OrdersCheckoutServiceImpl.kt:40  noRollbackFor is correct by reading: reserveStock joins the
  transaction and does not throw on UNAVAILABLE, and the method-level @Transactional overrides the class-level one. The
  transaction commits with the publication row and the AFTER_COMMIT listener fires. No IT in range proves it against
  the real registry (T-17 open): UNCONFIRMED until T-17.
- NOTE  T-12 NotificationsConsumerServiceImpl.kt:56-58 (also :88-90, :118-120)  A refused e-mail throws
  UnsupportedOperationException ("a later task"), but no task on the board covers NotificationDeliveryFailed
  (ZDL:90 declares it; the ZFL does not). The publication is retried with no end. Board task, or fix the comment.
- Checked, OK:
  - Path end to end by reading: startOrderCheckout -> reserveStock (in-process, all-or-nothing, expiresAt +10m) ->
    OrderCreated (amount = totalAmount) -> authorizePayment -> PaymentAuthorized -> confirmOrder ->
    confirmStockReservation -> OrderConfirmed (items, address) -> scheduleFulfillment -> capturePayment ->
    PaymentCaptured (customerEmail) -> sendOrderConfirmation -> OrderConfirmationSent.
  - One event per outcome.
  - Redelivery no-ops on every S1 listener (authorizePayment non-FAILED, confirmOrder CONFIRMED,
    confirmStockReservation without RESERVED, scheduleFulfillment existing, capturePayment CAPTURED,
    sendOrderConfirmation by orderId+type), each with a unit test.
  - Listener mappers: the generated MapStruct impls map every input field: OrderCreated 6/6, PaymentAuthorized orderId,
    OrderConfirmed orderId/customerId/items/address, FulfillmentScheduled orderId, PaymentCaptured
    orderId/customerId/customerEmail.
  - Tests assert fixture literals, not recomputed values. The flow test exists and asserts the C-06 end shape.

STANDARDS AXIS
- SHOULD FIX  FakePaymentProcessor.kt:85, FakeWarehouse.kt:116, FakeEmailSender.kt:160
  - The three fakes are @Component in src/main with no profile or condition. Any deployment approves and captures
    every card except the test ids, "schedules" every fulfillment and "sends" every e-mail, and reports success.
  - The real adapters of T-33 will clash with them (NoUniqueBeanDefinitionException) unless the fakes are removed or
    conditioned.
  - Mutable test hooks (captureRefusal, refusalReason) sit on production singletons.
  - FakeEmailSender.sent (CopyOnWriteArrayList of every message) grows without bound in a running app.
  - The project keeps test doubles in src/test (ServicesInMemoryConfig per module), and the board says the fakes
    "stay for tests" (T-33). Gate them (@Profile or @ConditionalOnProperty with matchIfMissing for tests) or move them
    to src/test plus a test configuration.
- NOTE  EmailSender.kt:135, FakeEmailSender.kt:161 (layered notifications.consumer) and Warehouse.kt / FakeWarehouse.kt
  (SimpleDomain fulfillment) use jMolecules hexagonal SecondaryPort/SecondaryAdapter. Their neighbours use the layered
  annotations (@InfrastructureLayer/@ApplicationLayer) or none.

PROPOSALS: none written (read-only). Suggest adding the OrderCreated-over-FAILED redelivery to P-05.
NOT CHECKED: full suite and all failsafe ITs (environment, above); MapStruct compile warnings (quiet incremental build,
  read the generated impls instead); concurrency and oversell under parallel checkouts.
