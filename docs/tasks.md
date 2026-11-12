# PlaceOrderFlow: tasks

flow: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl
baseline:
branch: afk/place-order-flow

<!-- Built by zenwave-tasks-from-code-and-models on 2026-11-12 from code generated with kotlin-backend-application
     2.9.0-SNAPSHOT and models service-all 0.9.0. Test data: src/test/resources/data.sql header (chains C-01..C-21,
     assumptions A-01..A-08, fixed now 2026-10-01T12:00:00Z). Open proposals: P-01 (blocks T-23), P-02 (blocks none,
     assumption A-01), P-03 (blocks T-15). External systems (payment processor, warehouse, e-mail) are ports with fake
     adapters driven by the fixtures' test ids (pm_card_visa, pm_card_chargeDeclined, ...); T-33 picks the real ones. -->

## S0 Baseline
review: [ ]

- [ ] T-01 mechanical AFK Inject a java.time.Clock bean (system clock; tests fix it at 2026-10-01T12:00:00Z) and use it for every Instant the services set
      tests: OrdersCheckoutServiceTest, InventoryServiceTest, PaymentsProcessingServiceTest
- [ ] T-02 bounded AFK ArchitectureTest: the archetype's single hexagonal/onion rule does not fit a modulith whose modules use Layered, SimpleDomain, Hexagonal and CleanHexagonal layouts; check each module with jMolecules and Spring Modulith ApplicationModules.verify() instead
      tests: ArchitectureTest, JMoleculesArchitectureTest
- [ ] T-03 mechanical AFK Generated repository updateTest: compare Instants at database precision (micros), not Instant.now() identity
      tests: mvn -q verify -Dit.test='*RepositoryIntegrationTest' -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false

## S1 completed (tracer bullet)
path: StartOrderCheckout > startOrderCheckout > reserveStock[StockReservedResult] > OrderCreated > authorizePayment[authorized]
      > PaymentAuthorized > confirmOrder > confirmStockReservation > OrderConfirmed > scheduleFulfillment[FulfillmentScheduled]
      > capturePayment[PaymentCaptured] > sendOrderConfirmation > end completed
review: [ ]

- [ ] T-04 bounded AFK reserveStock: all lines available -> one StockReservation per line RESERVED (expiresAt = now + 10m), available -> reserved, outcome RESERVED with the reserved lines; any line short -> outcome UNAVAILABLE with the short lines, nothing reserved
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:95-99, ../arcadia-editions-modulith-api/catalog-inventory-domain-model.zdl:52-59, ../arcadia-editions-modulith-api/catalog-inventory-domain-model.zdl:74-105, ../arcadia-editions-modulith-api/catalog-inventory-domain-model.zdl:111
      test data: data.sql C-02, data/jpa/InventoryPosition/1.json, data/jpa/InventoryPosition/3.json
      tests: InventoryServiceTest
      blocked by: T-01
- [ ] T-05 bounded AFK startOrderCheckout: call reserveStock in-process; on RESERVED create the Order CREATED (orderId, totalAmount = sum of quantity x unitPrice) and emit OrderCreated (amount = totalAmount)
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:73-89, ../arcadia-editions-modulith-api/orders-checkout-domain-model.zdl:61-80, ../arcadia-editions-modulith-api/orders-checkout-domain-model.zdl:95-98, ../arcadia-editions-modulith-api/orders-checkout-domain-model.zdl:114-123
      test data: data/jpa/InventoryPosition/1.json
      tests: OrdersCheckoutServiceTest
      blocked by: T-04
- [ ] T-06 mechanical AFK Web: POST /orders/start-checkout answers 201 with the Order, GET /orders/{id} 200 or 404
      model: ../arcadia-editions-modulith-api/orders-checkout-domain-model.zdl:92-111, ../arcadia-editions-modulith-api/orders-checkout-openapi.yml:32-70
      test data: data.sql C-01, data/jpa/Order/1.json
      tests: OrdersCheckoutApiControllerTest, OrdersCheckoutApiIntegrationTest
      blocked by: T-05
- [ ] T-07 advanced AFK authorizePayment on OrderCreated: create the Payment AUTHORIZATION_REQUESTED (attemptCount 1), ask a PaymentProcessor port (fake: pm_card_visa authorizes, pm_card_chargeDeclined declines, pm_card_processorError fails), set AUTHORIZED + providerReference / DECLINED / FAILED (A-01) and emit only that outcome's event; an OrderCreated for an orderId that already has a Payment does nothing
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:108-117, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:56-63, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:84-87, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:104-131
      test data: data.sql C-01
      tests: PaymentsProcessingServiceTest
      blocked by: T-01
- [ ] T-08 bounded AFK confirmStockReservation: every RESERVED reservation of the orderId -> CONFIRMED (confirmedAt), reserved -> sold, emit StockReservationConfirmed with the items; already CONFIRMED does nothing
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:155-158, ../arcadia-editions-modulith-api/catalog-inventory-domain-model.zdl:61-64, ../arcadia-editions-modulith-api/catalog-inventory-domain-model.zdl:120-122, ../arcadia-editions-modulith-api/catalog-inventory-domain-model.zdl:138-147
      test data: data.sql C-02, data.sql C-03
      tests: InventoryServiceTest
      blocked by: T-04
- [ ] T-09 bounded AFK confirmOrder on PaymentAuthorized: load the Order by orderId, guard CREATED, call confirmStockReservation, set CONFIRMED (confirmedAt), emit OrderConfirmed with items and shipping address; an already CONFIRMED order does nothing
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:146-150, ../arcadia-editions-modulith-api/orders-checkout-domain-model.zdl:82-84, ../arcadia-editions-modulith-api/orders-checkout-domain-model.zdl:100-103, ../arcadia-editions-modulith-api/orders-checkout-domain-model.zdl:138-157
      test data: data.sql C-02, data.sql C-03
      tests: OrdersCheckoutServiceTest
      blocked by: T-05, T-08
- [ ] T-10 bounded AFK scheduleFulfillment on OrderConfirmed: ask a Warehouse port (fake: schedules), create the Fulfillment SCHEDULED (fulfillmentId, items, address, scheduledAt), emit FulfillmentScheduled; a second OrderConfirmed for the order does nothing
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:166-170, ../arcadia-editions-modulith-api/fulfillment-shipping-domain-model.zdl:57-84, ../arcadia-editions-modulith-api/fulfillment-shipping-domain-model.zdl:91-97
      test data: data.sql C-03, data.sql C-04
      tests: FulfillmentShippingServiceTest
      blocked by: T-01
- [ ] T-11 advanced AFK capturePayment on FulfillmentScheduled: load the Payment by orderId, guard AUTHORIZED, capture through the PaymentProcessor port (fake: captures), set CAPTURED (capturedAt), emit PaymentCaptured with customerEmail; an already CAPTURED payment does nothing
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:178-182, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:71-74, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:93-95, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:150-159
      test data: data.sql C-04, data.sql C-05
      tests: PaymentsProcessingServiceTest
      blocked by: T-07
- [ ] T-12 bounded AFK sendOrderConfirmation on PaymentCaptured: create the Notification ORDER_CONFIRMATION by EMAIL to customerEmail through an EmailSender port (fake), SENT (sentAt) (A-01), emit OrderConfirmationSent; no second notification for the same order and type
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:189-192, ../arcadia-editions-modulith-api/notifications-consumer-domain-model.zdl:61-65, ../arcadia-editions-modulith-api/notifications-consumer-domain-model.zdl:88-90, ../arcadia-editions-modulith-api/notifications-consumer-domain-model.zdl:102-110
      test data: data.sql C-05, data.sql C-06
      tests: NotificationsConsumerServiceTest
      blocked by: T-01
- [ ] T-13 advanced AFK Flow test completed: from startOrderCheckout to OrderConfirmationSent across the five modules (Spring Modulith Scenario, Testcontainers)
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:73-192, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:262-266
      test data: data.sql C-06, data/jpa/InventoryPosition/1.json
      tests: PlaceOrderFlowCompletedIntegrationTest
      blocked by: T-06, T-09, T-10, T-11, T-12

## S2 stockGone
path: StartOrderCheckout > startOrderCheckout > reserveStock[StockUnavailableResult] > StockUnavailable
      > sendStockUnavailableNotification > end stockGone
review: [ ]

- [ ] T-14 bounded AFK startOrderCheckout UNAVAILABLE: persist no Order (A-05) and emit StockUnavailable with the unavailable items
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:82-89, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:194-202, ../arcadia-editions-modulith-api/orders-checkout-domain-model.zdl:98, ../arcadia-editions-modulith-api/orders-checkout-domain-model.zdl:126-135
      test data: data.sql C-07, data/jpa/InventoryPosition/3.json
      tests: OrdersCheckoutServiceTest
      blocked by: T-05
- [!] T-15 bounded AFK Web: the start-checkout answer when stock is unavailable
      model: ../arcadia-editions-modulith-api/orders-checkout-openapi.yml:32-52, ../arcadia-editions-modulith-api/orders-checkout-domain-model.zdl:95-98
      test data: data.sql C-07
      tests: OrdersCheckoutApiControllerTest, OrdersCheckoutApiIntegrationTest
      blocked by: T-06, T-14
      blocked: P-03
- [ ] T-16 bounded AFK sendStockUnavailableNotification on StockUnavailable: Notification STOCK_UNAVAILABLE with the skus, SENT, emit StockUnavailableNotificationSent; no duplicate on redelivery
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:199-202, ../arcadia-editions-modulith-api/notifications-consumer-domain-model.zdl:67-76, ../arcadia-editions-modulith-api/notifications-consumer-domain-model.zdl:92-94, ../arcadia-editions-modulith-api/notifications-consumer-domain-model.zdl:112-121
      test data: data.sql C-07
      tests: NotificationsConsumerServiceTest
      blocked by: T-12
- [ ] T-17 bounded AFK Flow test stockGone: startOrderCheckout for SKU-003 ends in StockUnavailableNotificationSent, no order, reservation or payment
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:82-99, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:199-202, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:262-266
      test data: data.sql C-07
      tests: PlaceOrderFlowStockGoneIntegrationTest
      blocked by: T-13, T-14, T-16

## S3 orderCancelled (payment declined)
path: OrderCreated > authorizePayment[declined] > PaymentDeclined > releaseStock > StockReleased > cancelOrder
      > OrderCancelled > sendOrderCancelledNotification > end orderCancelled
review: [ ]

- [ ] T-18 bounded AFK releaseStock on PaymentDeclined / PaymentRetryExhausted / PaymentVoided: every RESERVED or CONFIRMED reservation of the orderId -> RELEASED (releasedAt, releaseReason from the event: PAYMENT_DECLINED, PAYMENT_RETRY_EXHAUSTED, PAYMENT_VOIDED), quantities back to available (A-02), emit StockReleased; already RELEASED does nothing
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:223-228, ../arcadia-editions-modulith-api/catalog-inventory-domain-model.zdl:66-70, ../arcadia-editions-modulith-api/catalog-inventory-domain-model.zdl:113-118, ../arcadia-editions-modulith-api/catalog-inventory-domain-model.zdl:126-136
      test data: data.sql C-15, data.sql C-18, data.sql C-08
      tests: InventoryServiceTest
      blocked by: T-08
- [ ] T-19 bounded AFK cancelOrder on StockReleased: Order CREATED or CONFIRMED -> CANCELLED (cancelledAt, cancellationReason = reason, A-07), emit OrderCancelled; already CANCELLED does nothing
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:246-250, ../arcadia-editions-modulith-api/orders-checkout-domain-model.zdl:86-90, ../arcadia-editions-modulith-api/orders-checkout-domain-model.zdl:105-107, ../arcadia-editions-modulith-api/orders-checkout-domain-model.zdl:159-167
      test data: data.sql C-19, data.sql C-20, data.sql C-08
      tests: OrdersCheckoutServiceTest
      blocked by: T-09
- [ ] T-20 bounded AFK sendOrderCancelledNotification on OrderCancelled: Notification ORDER_CANCELLED with the reason, SENT, emit OrderCancelledNotificationSent; no duplicate on redelivery
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:257-260, ../arcadia-editions-modulith-api/notifications-consumer-domain-model.zdl:78-83, ../arcadia-editions-modulith-api/notifications-consumer-domain-model.zdl:96-98, ../arcadia-editions-modulith-api/notifications-consumer-domain-model.zdl:123-132
      test data: data.sql C-21, data.sql C-08
      tests: NotificationsConsumerServiceTest
      blocked by: T-12
- [ ] T-21 bounded AFK Flow test declined: checkout with pm_card_chargeDeclined ends in OrderCancelledNotificationSent, stock back to available
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:108-117, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:223-260, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:262-266
      test data: data.sql C-08
      tests: PlaceOrderFlowDeclinedIntegrationTest
      blocked by: T-13, T-18, T-19, T-20

## S4 retry loop
path: OrderCreated > authorizePayment[failed] > PaymentFailed > retryPayment[retried] > PaymentRetried
      > authorizePayment[authorized] > PaymentAuthorized > confirmOrder > ... > end completed
review: [ ]

- [ ] T-22 bounded AFK retryPayment on PaymentFailed: load the Payment by orderId, guard FAILED; attemptCount below 3 (A-03) -> attemptCount + 1, stays FAILED, emit PaymentRetried; at 3 -> RETRY_EXHAUSTED, emit PaymentRetryExhausted
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:124-130, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:65-69, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:89-91, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:133-148
      test data: data.sql C-12, data.sql C-13
      tests: PaymentsProcessingServiceTest
      blocked by: T-07
- [!] T-23 bounded AFK authorizePayment on PaymentRetried: authorize the existing FAILED payment again
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:132-140, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:56-63, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:85-87, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:133-139
      test data: data.sql C-12
      tests: PaymentsProcessingServiceTest
      blocked by: T-22
      blocked: P-01
- [ ] T-24 bounded AFK Flow test retry: a processor error on the first attempt (fake pm_card_processorErrorOnce), authorized on the retry, ends in OrderConfirmationSent
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:108-140, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:262-266
      test data: data.sql C-12
      tests: PlaceOrderFlowRetryIntegrationTest
      blocked by: T-13, T-23

## S5 orderCancelled (retries exhausted)
path: OrderCreated > authorizePayment[failed] > PaymentFailed > retryPayment[retry-exhausted] > PaymentRetryExhausted
      > releaseStock > StockReleased > cancelOrder > OrderCancelled > sendOrderCancelledNotification > end orderCancelled
review: [ ]

- [ ] T-25 bounded AFK Flow test retries exhausted: every attempt fails (pm_card_processorError), after 3 attempts the order is cancelled with PAYMENT_RETRY_EXHAUSTED
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:124-130, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:223-260, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:262-266
      test data: data.sql C-09, data.sql C-13
      tests: PlaceOrderFlowRetryExhaustedIntegrationTest
      blocked by: T-21, T-24

## S6 orderCancelled (fulfillment failed)
path: OrderConfirmed > scheduleFulfillment[FulfillmentFailed] > voidPayment > PaymentVoided > releaseStock > StockReleased
      > cancelOrder > OrderCancelled > sendOrderCancelledNotification > end orderCancelled
review: [ ]

- [ ] T-26 bounded AFK scheduleFulfillment FAILED branch: the Warehouse port refuses (fake, set by the test) -> Fulfillment FAILED (failedAt, failureReason), emit FulfillmentFailed only
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:160-170, ../arcadia-editions-modulith-api/fulfillment-shipping-domain-model.zdl:84, ../arcadia-editions-modulith-api/fulfillment-shipping-domain-model.zdl:99-106
      test data: data.sql C-03, data.sql C-16
      tests: FulfillmentShippingServiceTest
      blocked by: T-10
- [ ] T-27 advanced AFK voidPayment on FulfillmentFailed / PaymentCaptureFailed: load the Payment by orderId, guard AUTHORIZED, AUTHORIZATION_REQUESTED, FAILED or CAPTURE_FAILED, void through the PaymentProcessor port (fake), VOIDED (voidedAt, failureReason = reason), emit PaymentVoided; already VOIDED does nothing
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:204-215, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:76-80, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:97-100, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:169-175
      test data: data.sql C-16, data.sql C-01, data.sql C-12, data.sql C-17, data.sql C-10
      tests: PaymentsProcessingServiceTest
      blocked by: T-11
- [ ] T-28 bounded AFK Flow test fulfillment failed: ends in OrderCancelledNotificationSent with PAYMENT_VOIDED, payment VOIDED, stock back to available
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:160-170, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:204-260, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:262-266
      test data: data.sql C-10
      tests: PlaceOrderFlowFulfillmentFailedIntegrationTest
      blocked by: T-21, T-26, T-27

## S7 orderCancelled (capture failed)
path: FulfillmentScheduled > capturePayment[PaymentCaptureFailed] > voidPayment > PaymentVoided > releaseStock
      > StockReleased > cancelOrder > OrderCancelled > sendOrderCancelledNotification > end orderCancelled
review: [ ]

- [ ] T-29 bounded AFK capturePayment failure branch: the PaymentProcessor port refuses the capture (fake, set by the test) -> CAPTURE_FAILED (failedAt, failureReason), emit PaymentCaptureFailed only
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:172-182, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:95, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:161-167
      test data: data.sql C-04, data.sql C-17
      tests: PaymentsProcessingServiceTest
      blocked by: T-11
- [ ] T-30 bounded AFK Flow test capture failed: ends in OrderCancelledNotificationSent with PAYMENT_VOIDED
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:172-182, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:204-260, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:262-266
      test data: data.sql C-17
      tests: PlaceOrderFlowCaptureFailedIntegrationTest
      blocked by: T-28, T-29

## S8 orderCancelled (reservation expired)
path: ReservationExpired (10 minutes after OrderCreated) > releaseStock > StockReleased > cancelOrder > OrderCancelled
      > sendOrderCancelledNotification > end orderCancelled
review: [ ]

- [ ] T-31 advanced AFK ReservationExpired timer: a scheduled job (Clock-driven) finds RESERVED reservations with expiresAt at or before now and calls releaseStock(orderId, RESERVATION_EXPIRED) once per order; reservations before their deadline are untouched
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:230-240, ../arcadia-editions-modulith-api/catalog-inventory-domain-model.zdl:66-70, ../arcadia-editions-modulith-api/catalog-inventory-domain-model.zdl:116-118
      test data: data.sql C-14, data.sql C-01, data.sql C-11
      tests: InventoryServiceTest
      blocked by: T-18
- [ ] T-32 bounded AFK Flow test reservation expired: an order whose payment never resolves is cancelled with RESERVATION_EXPIRED after the Clock passes 10 minutes
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:230-260, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:262-266
      test data: data.sql C-11, data.sql C-14
      tests: PlaceOrderFlowReservationExpiredIntegrationTest
      blocked by: T-21, T-31

## S9 Integrations
review: [ ]

- [ ] T-33 bounded HITL Choose the real payment processor, warehouse and e-mail providers and their credentials (the fakes stay for tests)
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:100-117, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:160-192
