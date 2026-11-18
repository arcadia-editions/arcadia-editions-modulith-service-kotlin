# PlaceOrderFlow: tasks

flow: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl
baseline: baseline/place-order-flow-3
branch: afk/place-order-flow

<!-- Built by zenwave-tasks-from-code-and-models on 2026-11-17: the delta from baseline/place-order-flow-1 (board archived
     in docs/tasks/place-order-flow-baseline-1.md, T-01..T-51) to models service-all 0.10.1 (0.10.0 plus the OpenAPI regenerated with its overlay, P-09). The code was regenerated
     with zenwave-regenerate (.zenwave/handoff/place-order-flow/regen-reference-0.10.0.patch): compile stubs are marked
     "baseline/place-order-flow-2 compile stub"; listeners left out: PaymentRetried -> reauthorizePayment (T-58),
     ReservationExpired -> expireReservation (T-67). Test data: data.sql header, chains C-01..C-30, assumptions A-02..A-12,
     fixed now 2026-10-01T12:00:00Z. Known failing at the baseline (overlay): voidPaymentFrom{Failed,AuthorizationRequested}Test
     (T-60), reservationExpiredJob{,Twice,OncePerOrder}Test (T-67). Limits from the ZFL header: N = 3 attempts, T = 30 s
     processor timeout, backoff 5 s before attempt 2 and 15 s before attempt 3. Proposals: P-09 accepted (OpenAPI overlay + generator fix).
     Paths unchanged from baseline 1 (stockGone, declined) have no tasks. -->

## S1 completed (tracer bullet)
path: StartOrderCheckout > startOrderCheckout > reserveStock[StockReservedResult] > OrderCreated > authorizePayment[authorized]
      > PaymentAuthorized > confirmOrder[confirmed] > confirmStockReservation > OrderConfirmed > scheduleFulfillment[FulfillmentScheduled]
      > capturePayment[PaymentCaptured] > recordPaymentCaptured, sendOrderConfirmation[sent] > end completed
review: [ ]

- [x] T-52 advanced AFK authorizePayment: commit AUTHORIZATION_REQUESTED (attemptCount 1) in its own transaction before calling the processor, so a call in flight counts as started for getPaymentStatus; a processor call longer than 30 s ends as FAILED with PaymentFailed (processor_timeout)
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:42-69, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:107-122, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:119-128
      test data: data.sql C-01, data/jpa/Payment/1.json
      tests: PaymentsProcessingServiceTest
      done: claude-opus opus
- [x] T-53 bounded AFK recordPaymentCaptured: on PaymentCaptured set Fulfillment.paymentCapturedAt (SCHEDULED, no state change, no event); no-op when no Fulfillment exists or it is already set (replace the no-op compile stub)
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:215-221, ../arcadia-editions-modulith-api/fulfillment-shipping-domain-model.zdl:105-110
      test data: data.sql C-05, data.sql C-06, data.sql C-24
      tests: FulfillmentShippingServiceTest, PaymentsProcessingEventsListenerMapperTest
      done: claude-sonnet sonnet
- [x] T-54 bounded AFK markReadyToShip: refused while Fulfillment.paymentCapturedAt is null (the warehouse ships only after capture); unchanged otherwise
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:51-56, ../arcadia-editions-modulith-api/fulfillment-shipping-domain-model.zdl:112-114
      test data: data.sql C-04, data.sql C-24
      tests: FulfillmentShippingServiceTest
      blocked by: T-53
      done: claude-sonnet sonnet
- [x] T-55 advanced AFK Flow test completed: the Payment passes through AUTHORIZATION_REQUESTED before AUTHORIZED, the Fulfillment records paymentCapturedAt, markReadyToShip works only after it; ends at OrderConfirmationSent
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:336-345, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:202-221
      test data: data.sql C-06
      tests: PlaceOrderFlowCompletedIntegrationTest
      blocked by: T-52, T-54
      done: claude-opus opus
- [x] T-75 bounded AFK Processor exception is a failure: callProcessor maps a processor exception (not only the timeout) to FAILED + PaymentFailed with failureReason processor_error (P-10 A), for authorizePayment and reauthorizePayment; unit test with a throwing processor; one A-06 constant shared with FakePaymentProcessor; shut the processor-call executor down (@PreDestroy); fix the stale FakePaymentProcessor KDoc (pm_card_processorNoAnswer now ends FAILED after the timeout)
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:107-122, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:119-136
      test data: data.sql C-01, data.sql C-12
      tests: PaymentsProcessingServiceTest
      fixes: .zenwave/handoff/place-order-flow/reports/review-S1.md (FIX FIRST T-52, NOTEs)
      blocked by: T-80
      done: claude-sonnet sonnet
- [x] T-76 mechanical AFK markReadyToShip refusal names its cause: not InvalidStateTransitionException with allowedStates [SCHEDULED] from SCHEDULED, but an exception (or message) that says the payment is not captured yet (paymentCapturedAt null); FulfillmentTransitions is generated, do not edit it; adjust the refusal assertions (FulfillmentShippingServiceTest, PlaceOrderFlowCompletedIntegrationTest)
      model: ../arcadia-editions-modulith-api/fulfillment-shipping-domain-model.zdl:112-114
      test data: data.sql C-04, data.sql C-24
      tests: FulfillmentShippingServiceTest
      fixes: .zenwave/handoff/place-order-flow/reports/review-S1.md (SHOULD FIX T-54)
      done: claude-sonnet sonnet

## S2 retry loop (retried, retry-exhausted)
path: OrderCreated > authorizePayment[failed] > PaymentFailed > retryPayment[retried] > PaymentRetried > reauthorizePayment[authorized|declined|failed]
      > ... > retryPayment[retry-exhausted] > PaymentRetryExhausted > releaseStock > StockReleased > cancelOrder > OrderCancelled
      > sendOrderCancelledNotification > end orderCancelled
review: [ ]

- [x] T-56 bounded AFK retryPayment: no-op, no event, unless the Payment is FAILED and input.attemptCount == Payment.attemptCount (redelivery); never increments attemptCount; retried below 3 attempts, retry-exhausted at 3
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:124-136, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:138-145
      test data: data.sql C-12, data.sql C-13
      tests: PaymentsProcessingServiceTest, PaymentsProcessingServiceEventsListenerMapperTest
      done: claude-sonnet sonnet
- [x] T-57 advanced AFK reauthorizePayment: from FAILED with input.attemptCount == Payment.attemptCount, wait the backoff (5 s before attempt 2, 15 s before attempt 3), increment attemptCount and commit AUTHORIZATION_REQUESTED before the processor call (as T-52), 30 s timeout; outcomes AUTHORIZED / DECLINED / FAILED with their events; any other state or a stale attemptCount is a no-op (replace the TODO compile stub)
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:42-69, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:138-146, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:130-136
      test data: data.sql C-12, data.sql C-14
      tests: PaymentsProcessingServiceTest
      blocked by: T-52, T-56
      done: claude-opus opus
- [x] T-58 mechanical AFK Wire PaymentRetried to reauthorizePayment (PaymentsProcessingServiceEventsListener + asReauthorizePaymentInput, as the 0.10.0 generator writes it) and make authorizePayment creation only: drop the P-01 D branch that reuses a FAILED Payment and the placeholder mapping
      model: ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:119-136
      test data: data.sql C-01, data.sql C-12
      tests: PaymentsProcessingServiceTest, PaymentsProcessingServiceEventsListenerMapperTest
      blocked by: T-57
      done: claude-sonnet sonnet
- [x] T-59 advanced AFK Flow tests of the retry loop: attempts counted only by authorizePayment (1) and reauthorizePayment (2, 3), a redelivered PaymentFailed or PaymentRetried with a stale attemptCount changes nothing, backoff respected (fixed Clock or a test backoff)
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:124-146, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:254-266
      test data: data.sql C-09, data.sql C-12, data.sql C-13
      tests: PlaceOrderFlowRetryIntegrationTest, PlaceOrderFlowRetryDeclinedIntegrationTest, PlaceOrderFlowRetryExhaustedIntegrationTest
      blocked by: T-58
      done: claude-opus opus
- [x] T-80 advanced AFK reauthorizePayment works on JPA: do not merge a detached Payment over the one loaded in the listener transaction (load and check inside the REQUIRES_NEW block, or re-read after it), so the outcome of attempts 2 and 3 is saved without an optimistic-lock failure; a test that fails on the bug with a versioned entity (in-memory repository with @Version semantics or a JPA slice test); a named fun interface for the backoff instead of a raw (Duration) -> Unit bean; drop the stale A-01 comment in saveAuthorizationOutcome; one null handling of attemptCount
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:138-146, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:130-145
      test data: data.sql C-12, data.sql C-13, data.sql C-14
      tests: PaymentsProcessingServiceTest
      fixes: .zenwave/handoff/place-order-flow/reports/review-S2.md (FIX FIRST T-57, T-59; NOTEs)
      done: claude-opus opus
- [!] T-81 mechanical AFK retryPayment and reauthorizePayment are no-ops when no Payment exists (return null, log, no event), as voidPayment; the NotFound tests assert it
      model: ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:131-147
      tests: PaymentsProcessingServiceTest
      fixes: .zenwave/handoff/place-order-flow/reports/review-S2.md (SHOULD FIX no-op without Payment)
      blocked: P-12 (accepted A 2026-11-18; ZDL in a later round, T-81 goes to the next board)
- [x] T-82 mechanical AFK One Scenario test per flow-test class in the retry loop: move c12PendingPaymentFailedIsRetried... (PlaceOrderFlowRetryIntegrationTest) and c13PendingPaymentFailedAtTheLimit... (PlaceOrderFlowRetryExhaustedIntegrationTest) into classes of their own (Spring Modulith 2.0 binds the Scenario listener to the first context of a class only; @DirtiesContext(AFTER_EACH_TEST_METHOD) gives the second test a context without it); drop the duplicate @Suppress in VersionedPaymentStore.save
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:124-146
      test data: data.sql C-12, data.sql C-13
      tests: PlaceOrderFlowRetryIntegrationTest, PlaceOrderFlowRetryExhaustedIntegrationTest
      fixes: .zenwave/handoff/place-order-flow/reports/review-S2-r2.md (FIX FIRST T-59, NOTE VersionedPaymentStore)
      done: claude-sonnet sonnet


## S3 orderCancelled (void refused)
path: OrderConfirmed > scheduleFulfillment[FulfillmentFailed] > voidPayment[void-failed] > PaymentVoidFailed > releaseStock > StockReleased
      > cancelOrder > OrderCancelled > sendOrderCancelledNotification > end orderCancelled
review: [x]

- [x] T-60 bounded AFK voidPayment: no-op, no event, when there is no Payment or it is not AUTHORIZED or CAPTURE_FAILED (rewrite voidPaymentFromFailedTest and voidPaymentFromAuthorizationRequestedTest to assert the no-op); a refused void sets VOID_FAILED and voidFailedAt and emits PaymentVoidFailed instead of throwing (fake processor: a void-refusal test hook)
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:236-252, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:152-161, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:243-249
      test data: data.sql C-01, data.sql C-10, data.sql C-12, data.sql C-16, data.sql C-17, data.sql C-27
      tests: PaymentsProcessingServiceTest
      done: claude-sonnet sonnet
- [x] T-61 mechanical AFK releaseStock on PaymentVoidFailed (listener and mapper already wired): reason PAYMENT_VOID_FAILED from a CONFIRMED reservation, sold back to available; listener mapper test
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:254-266, ../arcadia-editions-modulith-api/catalog-inventory-domain-model.zdl:142-148
      test data: data.sql C-27, data.sql C-28
      tests: InventoryServiceTest, PaymentsProcessingEventsListenerMapperTest
      done: claude-sonnet sonnet
- [x] T-62 bounded AFK sendOrderCancelledNotification: when the reason is PAYMENT_VOID_FAILED the e-mail says the hold on the card may still show for a few days
      model: ../arcadia-editions-modulith-api/notifications-consumer-domain-model.zdl:96-98, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:152-161
      test data: data.sql C-28
      tests: NotificationsConsumerServiceTest
      done: claude-sonnet sonnet
- [x] T-63 advanced AFK Flow test void refused: fulfillment fails, the void is refused, the stock is released (PAYMENT_VOID_FAILED), the order is cancelled once and the customer told; ends at OrderCancelledNotificationSent with the Payment VOID_FAILED
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:236-266, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:299-309, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:322-345
      test data: data.sql C-28
      tests: PlaceOrderFlowVoidFailedIntegrationTest
      blocked by: T-60, T-61, T-62
      done: claude-opus opus
- [x] T-78 mechanical AFK voidPayment tests prove what T-60 claims: assertVoidNoOp compares fields (status, failureReason, voidedAt, voidFailedAt), not the entity (Payment.equals is by id); voidPaymentRefusedTest asserts saved.failureReason and PaymentVoidFailed.reason = the void input reason (C-27); log.warn the processor's refusal reason on a refused void (ops own VOID_FAILED); voidable check as `status in listOf(AUTHORIZED, CAPTURE_FAILED)`; feat/test commit types are the orchestrator's
      model: ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:152-161, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:243-249
      test data: data.sql C-01, data.sql C-12, data.sql C-16, data.sql C-27
      tests: PaymentsProcessingServiceTest
      fixes: .zenwave/handoff/place-order-flow/reports/review-S3.md (SHOULD FIX T-60 x2, NOTEs)
      done: claude-sonnet sonnet
- [x] T-79 mechanical AFK Cancellation e-mail on PAYMENT_VOID_FAILED says the hold may still show until we release it (ZFL: ops release the hold by hand), not "for a few days" (the person, 2026-11-18, review S3 NOTE)
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:322-326, ../arcadia-editions-modulith-api/notifications-consumer-domain-model.zdl:96-98
      test data: data.sql C-28
      tests: NotificationsConsumerServiceTest
      fixes: .zenwave/handoff/place-order-flow/reports/review-S3.md (NOTE T-62 wording)
      done: claude-sonnet sonnet

## S4 orderCancelled (capture failed, fulfillment cancelled)
path: FulfillmentScheduled > capturePayment[PaymentCaptureFailed] > voidPayment[voided] > PaymentVoided > releaseStock > StockReleased
      > cancelOrder > OrderCancelled > cancelFulfillment > FulfillmentCancelled, sendOrderCancelledNotification > end orderCancelled
review: [ ]

- [x] T-64 bounded AFK cancelFulfillment: on OrderCancelled, SCHEDULED or READY_TO_SHIP becomes CANCELLED with cancelledAt and cancellationReason = OrderCancelled.reason (A-11), emits FulfillmentCancelled; no-op, no event, when there is no Fulfillment or it is CANCELLED or FAILED (replace the no-op compile stub)
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:311-320, ../arcadia-editions-modulith-api/fulfillment-shipping-domain-model.zdl:116-119, ../arcadia-editions-modulith-api/fulfillment-shipping-domain-model.zdl:151-157
      test data: data.sql C-21, data.sql C-25, data.sql C-26
      tests: FulfillmentShippingServiceTest, OrdersCheckoutEventsListenerMapperTest
      done: claude-sonnet sonnet
- [x] T-65 bounded AFK Flow test capture failed: after the void and the cancellation the Fulfillment is CANCELLED and FulfillmentCancelled was emitted; the order is cancelled once
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:190-200, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:311-320
      test data: data.sql C-26
      tests: PlaceOrderFlowCaptureFailedIntegrationTest
      blocked by: T-60, T-64
      done: claude-sonnet sonnet
- [x] T-85 mechanical AFK The capture-failed flow test proves the order is cancelled once: record OrderCancelled (as FulfillmentCancelled is) and assert exactly one for the orderId; sort the imports, import @Bean, keep lines under 120 columns in PlaceOrderFlowCaptureFailedIntegrationTest
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:296-320
      test data: data.sql C-26
      tests: PlaceOrderFlowCaptureFailedIntegrationTest
      fixes: .zenwave/handoff/place-order-flow/reports/review-S4.md (SHOULD FIX T-65, STANDARDS NOTEs)
      done: claude-sonnet sonnet


## S5 orderCancelled (reservation expired, query timer)
path: ReservationExpired (10 minutes after OrderCreated) > expireReservation > getPaymentStatus[NoPaymentResult] > StockReleased > cancelOrder
      > OrderCancelled > sendOrderCancelledNotification > end orderCancelled; getPaymentStatus[PaymentStartedResult]: no release, payment owns the end
review: [x]

- [x] T-66 bounded AFK getPaymentStatus: read-only; NO_PAYMENT when no Payment exists for orderId, STARTED otherwise (AUTHORIZATION_REQUESTED included) (replace the TODO compile stub)
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:293-297, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:163-164
      test data: data.sql C-01, data.sql C-14, data.sql C-30
      tests: PaymentsProcessingServiceTest
      done: claude-sonnet sonnet
- [x] T-67 advanced AFK expireReservation and the timer: ReservationExpiredJob calls expireReservation instead of releaseStock; it calls getPaymentStatus and releases the RESERVED lines (RESERVATION_EXPIRED, StockReleased) only on NO_PAYMENT; no-op on STARTED or when nothing is RESERVED; rewrite reservationExpiredJob{,Twice,OncePerOrder}Test for 0.10.0 (C-30 released, C-14 kept) (replace the TODO compile stub)
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:42-69, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:268-291, ../arcadia-editions-modulith-api/catalog-inventory-domain-model.zdl:150-158
      test data: data.sql C-01, data.sql C-11, data.sql C-14, data.sql C-30
      tests: InventoryServiceTest
      blocked by: T-52, T-66
      done: claude-opus opus
- [x] T-68 advanced AFK Flow test reservation expired: with no payment started the stock is released and the order cancelled; with a payment started (pm_card_processorNoAnswer) the timer releases nothing and payment ends the order (timeout, retries); the skipped race of baseline 1 is covered
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:42-69, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:268-309
      test data: data.sql C-11, data.sql C-30
      tests: PlaceOrderFlowReservationExpiredIntegrationTest
      blocked by: T-67
      done: claude-opus opus
- [x] T-86 mechanical AFK PlaceOrderFlowReservationExpiredIntegrationTest names its listener targets for what they are: TIMER_TARGET (CatalogInventoryEventsListener.onStockReleased, the cancelOrder listener) becomes CANCEL_ORDER, shared from RetryLoopTargets as the sibling tests do; the assertion message at :340 says the release came from payment
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:268-309
      tests: PlaceOrderFlowReservationExpiredIntegrationTest
      fixes: .zenwave/handoff/place-order-flow/reports/review-S5.md (SHOULD FIX TIMER_TARGET)
      done: claude-sonnet sonnet


## S6 orderCancelled (late authorization rejected)
path: PaymentAuthorized (after expiry) > confirmOrder > confirmStockReservation[StockReservationNotHeldResult] or order CANCELLED
      > OrderConfirmationRejected > voidPayment[voided] > PaymentVoided > releaseStock (no-op, RELEASED) > end orderCancelled
review: [ ]

- [x] T-69 bounded AFK confirmStockReservation NOT_HELD: when the order has no RESERVED or CONFIRMED reservation (released by the timer) answer NOT_HELD, no change, no event; CONFIRMED stays the redelivery answer
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:166-176, ../arcadia-editions-modulith-api/catalog-inventory-domain-model.zdl:161-163
      test data: data.sql C-02, data.sql C-03, data.sql C-23
      tests: InventoryServiceTest
      done: claude-sonnet sonnet
- [x] T-70 bounded AFK confirmOrder rejections: a CANCELLED order emits OrderConfirmationRejected (ORDER_CANCELLED) without calling inventory; NOT_HELD emits OrderConfirmationRejected (STOCK_NOT_HELD) and the order stays CREATED; rejectedAt from the Clock
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:148-164, ../arcadia-editions-modulith-api/orders-checkout-domain-model.zdl:101-108, ../arcadia-editions-modulith-api/orders-checkout-domain-model.zdl:177-183
      test data: data.sql C-02, data.sql C-22, data.sql C-23
      tests: OrdersCheckoutServiceTest
      blocked by: T-69
      done: claude-sonnet sonnet
- [x] T-71 mechanical AFK voidPayment on OrderConfirmationRejected (listener and mapper already wired): listener mapper test, void reason = the rejection reason
      model: ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:152-161
      test data: data.sql C-22
      tests: OrdersCheckoutEventsListenerMapperTest, PaymentsProcessingServiceTest
      blocked by: T-60
      done: claude-sonnet sonnet
- [x] T-72 advanced AFK Flow test late authorization: the authorization arrives after the timer released the stock; confirmOrder rejects, the hold is voided, releaseStock and cancelOrder are no-ops, the order is cancelled once
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:148-176, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:236-266, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:299-309
      test data: data.sql C-22, data.sql C-23
      tests: PlaceOrderFlowLateAuthorizationIntegrationTest
      blocked by: T-67, T-70, T-71
      done: claude-opus opus
- [x] T-83 mechanical AFK One Scenario test per class for the late authorization: move lateAuthorizationForCancelledOrder... out of PlaceOrderFlowLateAuthorizationIntegrationTest into a class of its own (same Scenario/@DirtiesContext defect as T-82)
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:148-176
      test data: data.sql C-22, data.sql C-23
      tests: PlaceOrderFlowLateAuthorizationIntegrationTest
      fixes: .zenwave/handoff/place-order-flow/reports/review-S2-r2.md (NOTE T-72)
      done: claude-sonnet sonnet


## S7 notificationFailed
path: PaymentCaptured > sendOrderConfirmation[failed] > NotificationDeliveryFailed > end notificationFailed
review: [ ]

- [x] T-73 bounded AFK Flow test notification failed: the e-mail provider refuses the order confirmation (fake: a refusal test hook), Notification FAILED, ends at NotificationDeliveryFailed with its type
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:202-213, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:336-345
      test data: data.sql C-29
      tests: PlaceOrderFlowNotificationFailedIntegrationTest
      blocked by: T-55
      done: claude-sonnet sonnet
- [x] T-87 mechanical AFK The notification-failed flow test proves the business outcome is untouched: await Fulfillment.paymentCapturedAt = now (as the completed flow test does), assert the SKU-006 counters relative to the position read before the checkout (available -1, sold +1, as the other flow tests), compare NotificationDeliveryFailed.notificationId with the stored Notification; drop the e-mail-list assertion that only proves the fake
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:202-221, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:336-345
      test data: data.sql C-29
      tests: PlaceOrderFlowNotificationFailedIntegrationTest
      fixes: .zenwave/handoff/place-order-flow/reports/review-S7.md (SHOULD FIX x2, NOTEs)
      done: claude-sonnet sonnet


## S8 Polish
review: [x]

- [x] T-74 mechanical AFK startOrderCheckout validation: quantity >= 1 and unitPrice >= 0 (ZDL bounds, P-09) answer 400 before any reservation; getOrder answers 400 on an invalid id (the generated testGetOrder_400 of 0.10.1, overlay response)
      model: ../arcadia-editions-modulith-api/orders-checkout-domain-model.zdl:69-70, ../arcadia-editions-modulith-api/orders-checkout-domain-model.zdl:96-100
      tests: OrdersCheckoutApiControllerTest, OrdersCheckoutApiIntegrationTest
      done: claude-sonnet sonnet
- [x] T-77 mechanical AFK The 400 of startOrderCheckout proves nothing was reserved: testStartOrderCheckout_400_bounds asserts StockReservation and Order counts unchanged after each request (as OrdersCheckoutServiceTest does for the 409) and the ProblemDetail field (items[0].quantity / items[0].unitPrice); one case per test or per assertion so the second is not hidden; trim the controller-test comment to what it proves; import jakarta.validation.Validation
      model: ../arcadia-editions-modulith-api/orders-checkout-domain-model.zdl:69-70, ../arcadia-editions-modulith-api/orders-checkout-domain-model.zdl:96-100
      tests: OrdersCheckoutApiControllerTest, OrdersCheckoutApiIntegrationTest
      fixes: .zenwave/handoff/place-order-flow/reports/review-S8.md (SHOULD FIX T-74, NOTEs)
      done: claude-sonnet sonnet

## S9 Integrations
review: [ ]

- [ ] T-33 bounded HITL Choose the real payment processor, warehouse and e-mail providers and their credentials (the fakes stay for tests)
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:107-122, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:202-213
- [ ] T-84 bounded HITL Decide whether the retry backoff (Thread.sleep 5 s / 15 s) and the processor call (up to 30 s) may hold a listener thread and its DB connections (8-thread async pool, Hikari default 10): accept, add a load check, or reschedule the retry instead of sleeping (review S1 and S2 SHOULD FIX, UNCONFIRMED under load)
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:42-69, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:119-147
      fixes: .zenwave/handoff/place-order-flow/reports/review-S1.md, .zenwave/handoff/place-order-flow/reports/review-S2-r2.md
