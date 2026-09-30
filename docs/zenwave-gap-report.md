# ZenWave Gap Report — arcadia-editions-modulith-service-kotlin

| | |
|---|---|
| Date | 2026-09-27 |
| Code baseline | `1c77bb4` (test: add sample JPA fixtures for modulith entities) |
| Flows | `arcadia-editions-architecture/business-flows/place-order-flow.zfl` (`PlaceOrderFlow`) |
| Models | `arcadia-editions-modulith-api/*-domain-model.zdl` (6 bounded contexts) |
| Deployment shape | Modulith: in-process events via `@ApplicationModuleListener` |
| Produced with | `skills/generic/zenwave-gap-report` (read-only; no `src/` changes) |

## Summary

The project compiles and starts, but **none of the `PlaceOrderFlow` steps work end to end yet**:

1. **The first event crashes.** `OrderCreated → authorizePayment` builds `AuthorizePaymentInput` with
   `null` for four non-null Kotlin parameters, which throws an NPE. Of the 11 listener mappings, 8
   do the same (see [§2](#2-placeorderflow--traceability)).
2. **Most crashes come from model gaps, not code gaps.** `StartOrderCheckout` only carries
   `items: String[]`, so customer, price, payment method, shipping address and e-mail exist nowhere
   upstream ([M-01](#m-01)). Mapper configuration can't fix that: the ZDL has to change first.
3. **Every service method with a `TODO` follows the same pattern.** It builds a new empty entity,
   never loads or saves it, and emits *every* `withEvents` alternative unconditionally.
   16 methods are affected across 6 services.
4. **Four ZFL edges have no trigger in code.** They are `PaymentFailed→retryPayment`,
   `PaymentRetried→authorizePayment`, `PaymentCaptureFailed→voidPayment`, and the Scheduler's
   `ReservationExpired→releaseStock`. The two synchronous `call`s
   (`reserveStock`, `confirmStockReservation`) are only TODO comments.
5. **Tests and fixtures cover only generated structure.** There are 19 empty `// TODO` service tests,
   and the generated assertions will fail once the behavior is correct. The fixtures hold one record
   per entity, with states that contradict each other.

| Severity | Model (M) | Code (G) | Cross-cutting (X) | Open questions (Q) |
|---|---|---|---|---|
| blocker | 10 | 6 | 0 | — |
| major | 1 | 19 | 5 | — |
| minor | 1 | 9 | 3 | — |
| **total** | **12** | **34** | **8** | **15** |

Resolve the **model gaps and open questions first** (ZDL edit → regenerate via `zenwave-scripts.zw`),
and only then fill in the code. Otherwise hand-written code will be overwritten or will contradict the model.

---

## 1. Model gaps (fix in ZFL / ZDL, then regenerate)

### Status after model passes 1–2 (2026-09-27, not yet regenerated)

| Id | Status | Change |
|---|---|---|
| M-01 | ✅ fixed | No customers BC exists, so the customer data is a **snapshot at checkout**. `StartOrderCheckoutInput` / `Order` gained `customerId`, `customerEmail`, `paymentMethodId`, `currency`, `items {sku, quantity, unitPrice}`, `shippingAddress` (plus `Order.totalAmount`). `OrderCreated` / `OrderConfirmed` / `OrderCancelled` carry what their consumers need. `Payment` + `AuthorizePaymentInput` + `PaymentCaptured` carry `customerEmail`. Notification inputs renamed `email` → `customerEmail`. |
| M-02 | ✅ fixed | Multi-line orders, one reservation per line. `StockReservation.orderId` is no longer `unique`. `ReserveStockInput` takes `items[]`. `ReleaseStockInput` is `{orderId, reason}` and releases every reservation of the order (returns `StockReservation[]`). `StockReleased` carries `items[]`. |
| M-03 | ✅ fixed | `reserveStock(ReserveStockInput) ReserveStockResult`: a wrapper with `outcome ReserveStockOutcome {RESERVED, UNAVAILABLE}` plus nullable `reserved: StockReservedResult` / `unavailable: StockUnavailableResult`. The caller branches with `when (result.outcome)`. The rule was added to `skills/zfl-to-zdl.md` (v1.4.0) for all future multi-response calls. |
| M-04 | ✅ fixed | `StockUnavailable {orderId, customerId, customerEmail, unavailableItems[sku, requestedQuantity, availableQuantity]}`. The notification input and `Notification.skus` were aligned to it. |
| M-05 | ⏳ open | Depends on M-03 / Q-03. |
| M-06 | ✅ fixed | `confirmStockReservation(ConfirmStockReservationInput {orderId}) StockReservation[]` confirms every reservation of the order. `StockReservation.confirmedAt` added. `StockReservationConfirmed {orderId, confirmedAt, items[reservationId, sku, quantity]}`. |
| M-07 | ✅ fixed | `CancelOrderInput.reason` (from `StockReleased.reason`), `Order.cancellationReason`, `OrderCancelled.reason`. The notification input maps by name. |
| M-08 | ⏸ deferred | Scheduler for `ReservationExpired`: kept as a task for later. |
| M-09 | ✅ partly fixed | `@asyncapi` removed and replaced by same-module `@listener(event: PaymentFailed)` on `retryPayment` and `@listener(event: PaymentCaptureFailed)` on `voidPayment`. **Deferred:** `PaymentRetried → authorizePayment` (authorize is modeled as a create) and the `RETRY_EXHAUSTED` transition. |
| G-PP-04/05 (model part) | ✅ fixed | `CapturePaymentInput {orderId}` and `VoidPaymentInput {orderId, reason}` no longer take `paymentId`; the payment is resolved by `orderId` (unique). |
| M-10 | ✅ fixed | `releaseStock @transition(from: [RESERVED, CONFIRMED], to: RELEASED)`. |
| M-11 | ✅ fixed | `Fulfillment` stores `@json shippingAddress` and `@json items[]`. |
| M-12 | ✅ fixed | ZFL: `confirmStockReservation` typo, `CatalogInventory @zdl` path, `@compensates(startOrderCheckout)`, `StartOrderCheckout` payload updated. Products: `output ProductPublishedResponse`. |

Still open in code: payment-event `reason` → `StockReleaseReason` needs constant mappings (G-CI-04).
Still open in the model: M-05 (what `POST /orders/start-checkout` returns and whether an order is persisted when stock
is unavailable, Q-03), M-08 (deferred), and the deferred part of M-09.

<a id="m-01"></a>
**M-01 · blocker · Order lacks the context every downstream step needs.**
`StartOrderCheckoutInput`/`Order` only have `items`. Downstream required inputs have no source:

| Needed by | Missing fields |
|---|---|
| `AuthorizePaymentInput` (payments) | `customerId`, `amount`, `currency`, `paymentMethodId` |
| `ScheduleFulfillmentInput` (fulfillment) | `customerId`, `shippingAddress`, `items[sku, quantity]` |
| `SendOrderConfirmationInput` (notifications) | `email` |
| `SendStockUnavailableNotificationInput` | `customerId`, `email` (and `sku`, see M-04) |
| `SendOrderCancelledNotificationInput` | `customerId`, `email`, `reason` (see M-07) |

→ Extend `StartOrderCheckoutInput`, `Order`, `OrderCreated`, `OrderConfirmed`, `OrderCancelled`,
or introduce a lookup such as a Customer context. Decision needed: [Q-01](#q-01), [Q-04](#q-04).

**M-02 · blocker · Line-item shape mismatch.** ZFL `items SKU[]` and `Order.items String[]` carry no
quantity. `ReserveStockInput` is a single `sku` + `quantity`, and `StockReservation.orderId` is `unique`,
so only one SKU per order can ever be reserved. `ScheduleFulfillmentInput.items` needs `sku` + `quantity`.
Decision needed: [Q-02](#q-02).

**M-03 · blocker · `reserveStock` can't express "unavailable".** ZFL declares two responses
(`StockReservedResult` / `StockUnavailableResult`), but ZDL returns only `StockReservedResult`, so
`startOrderCheckout` has no way to branch. Fix with a union return, a sealed result, or a
domain exception, whichever the generator supports.

**M-04 · blocker · `StockUnavailable` event can't feed its consumer.** It has `productId`,
`requestedQuantity`, but no `orderId`/`customerId`. Its consumer needs `customerId`, `sku`, `email`.
Also `productId` vs `sku` naming.

**M-05 · blocker · ZFL and ZDL disagree on whether the order exists after stock is unavailable.** ZFL says "no order
record was created", but ZDL `startOrderCheckout` is `@transition(to: CREATED)` and returns `Order`.
Decision needed: [Q-03](#q-03).

**M-06 · blocker · `confirmStockReservation(ReserveStockInput)` has the wrong input.** It requires
`sku` + `quantity`, but `confirmOrder` only knows `orderId`. It should take
`ConfirmStockReservationInput { orderId }`. Also, the `StockReservationConfirmed` event copies
`reason`/`releasedAt` from `StockReleased`; it should have `confirmedAt`.

**M-07 · blocker · The cancellation reason is lost.** `StockReleased.reason` never reaches the notification:
`CancelOrderInput`, `Order` and `OrderCancelled` have no `reason`, yet
`SendOrderCancelledNotificationInput.reason` is required.

**M-08 · blocker · The `ReservationExpired` timer has no source.** `releaseStock` has
`@timer(api: SchedulerApi, channel: "reservation-expired-event-v1")`, but `SchedulerApi` isn't declared in
`apis {}` and nothing is generated for it. Decision needed: [Q-07](#q-07).

**M-09 · blocker · Same-module edges aren't modeled as in-process listeners.** `retryPayment` and
`voidPayment` use `@asyncapi(channel: ...)`. In a modulith that doesn't generate a listener, so
`PaymentFailed→retryPayment`, `PaymentRetried→authorizePayment` and
`PaymentCaptureFailed→voidPayment` never fire. Also, `authorizePayment` is modeled as a *create*
(`@transition(to: AUTHORIZATION_REQUESTED)`) that needs the full `AuthorizePaymentInput`, so a retry
can't re-enter it with only `PaymentRetried{paymentId, orderId}`. There is also no transition to
`RETRY_EXHAUSTED`.

**M-10 · blocker · Compensation after confirmation is impossible.** `confirmOrder` moves the reservation to
`CONFIRMED`. Later, `FulfillmentFailed/PaymentCaptureFailed → voidPayment → PaymentVoided → releaseStock`
needs `releaseStock @transition(from: RESERVED)`, which will throw `InvalidStateTransitionException`.
Allow `from: [RESERVED, CONFIRMED]` or model a separate return-to-stock. Decision needed: [Q-08](#q-08).

**M-11 · major · `Fulfillment` can't store what `scheduleFulfillment` receives.** The entity has no
shipping address or items, so the input data is dropped.

**M-12 · minor · ZFL identity issues.** These will produce false drift findings:
- `confirStockReservation` (typo) in the `systems` block
- `CatalogInventory @zdl("catalog-products-api/...")` points at the wrong model
- `@compensates(createOrder)` names a command that doesn't exist (should be `startOrderCheckout`)
- In catalog-products, `output ProductPublished` has the same name as `event ProductPublished`

---

## 2. PlaceOrderFlow — traceability

| # | ZFL step | Trigger in code | Input mapping | Service body |
|---|---|---|---|---|
| 1 | `StartOrderCheckout → startOrderCheckout` | ✅ `POST /api/orders/start-checkout` | ✅ | ❌ G-OC-01/02 |
| 2 | `call reserveStock` | ❌ TODO comment only | — | ❌ G-CI-01 |
| 3 | `OrderCreated → authorizePayment` | ✅ `payments/.../OrdersCheckoutEventsListener.kt:22` | ❌ **NPE** (M-01) | ❌ G-PP-02 |
| 4 | `PaymentFailed → retryPayment` | ❌ none (M-09) | — | ❌ G-PP-03 |
| 5 | `PaymentRetried → authorizePayment` | ❌ none (M-09) | — | ❌ |
| 6 | `PaymentAuthorized → confirmOrder` | ✅ `orders/.../PaymentsProcessingEventsListener.kt:22` | ✅ | ❌ G-OC-03 |
| 7 | `call confirmStockReservation` | ❌ TODO comment only | ❌ M-06 | ❌ G-CI-03 |
| 8 | `OrderConfirmed → scheduleFulfillment` | ✅ `fulfillment/.../OrdersCheckoutEventsListener.kt:22` | ❌ **NPE** (M-01/02) | ❌ G-FS-02 |
| 9 | `FulfillmentScheduled → capturePayment` | ✅ `payments/.../FulfillmentShippingEventsListener.kt:22` | ❌ **NPE** `paymentId` | ❌ G-PP-04 |
| 10 | `PaymentCaptured → sendOrderConfirmation` | ✅ `notifications/.../PaymentsProcessingEventsListener.kt:22` | ❌ **NPE** `email` | ❌ G-NC-02 |
| 11 | `StockUnavailable → sendStockUnavailableNotification` | ✅ `notifications/.../OrdersCheckoutEventsListener.kt:22` | ❌ **NPE** (M-04) | ❌ G-NC-02 |
| 12 | `FulfillmentFailed → voidPayment` | ✅ `payments/.../FulfillmentShippingEventsListener.kt:27` | ❌ **NPE** `paymentId` | ❌ G-PP-05 |
| 12b | `PaymentCaptureFailed → voidPayment` | ❌ none (M-09) | — | ❌ G-PP-05 |
| 13 | `PaymentDeclined / RetryExhausted / Voided → releaseStock` | ✅ `catalog/inventory/.../PaymentsProcessingEventsListener.kt:22,27,32` | ❌ **NPE** + enum crash (G-CI-04) | ❌ G-CI-02 |
| 13b | `ReservationExpired → releaseStock` (Scheduler) | ❌ none (M-08) | — | ❌ |
| 14 | `StockReleased → cancelOrder` | ✅ `orders/.../CatalogInventoryEventsListener.kt:22` | ✅ (reason lost, M-07) | ❌ G-OC-04 |
| 15 | `OrderCancelled → sendOrderCancelledNotification` | ✅ `notifications/.../OrdersCheckoutEventsListener.kt:29` | ❌ **NPE** (M-01/07) | ❌ G-NC-02 |

**Why these are NPEs.** Look at the generated MapStruct impls under `target/generated-sources/kapt`.
They assign `null` to unmapped locals and pass them to Kotlin data-class constructors with non-null
parameters, which fails with `NullPointerException` from Kotlin's parameter null-checks. The
compiler already reports these as `Unmapped target propert(y|ies)` warnings.

Paths below are relative to `src/main/kotlin/com/arcadiaeditions/`.

### 2.1 orders-checkout (phase order: steps 1, 6, 14)

| Id | Sev | Where | Gap → expected behavior | Model ref |
|---|---|---|---|---|
| G-OC-01 | blocker | `orders/checkout/service/impl/OrdersCheckoutServiceImpl.kt:33` | No `reserveStock` call and no dependency on `InventoryService`. Call it, branch on the result (M-03): reserved → `OrderCreated`, unavailable → `StockUnavailable`. | ZDL `@calls(InventoryService.reserveStock)`, ZFL step 1 |
| G-OC-02 | major | same | Emits **both** `OrderCreated` and `StockUnavailable`. Never generates `orderId`, never sets `createdAt`, never saves. | ZDL `withEvents [OrderCreated \| StockUnavailable]` |
| G-OC-03 | major | `…/OrdersCheckoutServiceImpl.kt:46` | `confirmOrder` builds `Order()` instead of loading by `orderId`. Missing: `OrderRepository.findByOrderId`, `OrderTransitions.ensureCanConfirmOrder`, `status=CONFIRMED`, `confirmedAt`, save, the `confirmStockReservation` call. The generated TODO wrongly says to emit `StockReservationConfirmed` (that event belongs to inventory). | `@transition(from: CREATED, to: CONFIRMED)` |
| G-OC-04 | major | `…/OrdersCheckoutServiceImpl.kt:59` | `cancelOrder` has the same gaps (load, `ensureCanCancelOrder`, `CANCELLED`, `cancelledAt`, save). It also needs idempotency: `StockReleased` for an already-cancelled order must be a no-op, not a 409. | `@transition(from: [CREATED, CONFIRMED], to: CANCELLED)` |
| G-OC-05 | major | `orders/checkout/service/impl/mappers/EventsMapper.kt` | `asStockUnavailable(order)` can't fill `productId`, `requestedQuantity` (compiler warning). Map from `StockUnavailableResult` instead. | M-03/M-04 |
| G-OC-06 | minor | `orders/checkout/repository/jpa/OrderRepository.kt` | Add a `findByOrderId` natural-key finder. | `orderId unique` |

### 2.2 catalog-inventory (steps 2, 7, 13)

| Id | Sev | Where | Gap → expected behavior | Model ref |
|---|---|---|---|---|
| G-CI-01 | major | `catalog/inventory/service/impl/InventoryServiceImpl.kt:33` | `reserveStock` has no availability check against `InventoryPosition`, doesn't move `available→reserved`, doesn't generate `reservationId`, doesn't set `reservedAt`/`expiresAt` (= +10 min per ZFL), and never saves. Must handle concurrent reservations during a drop (optimistic `@Version` or row lock). | ZFL `reserveStock`, `@transition(to: RESERVED)` |
| G-CI-02 | major | `…/InventoryServiceImpl.kt:42` | `releaseStock` builds a new entity. Load by `orderId` (add `StockReservationRepository.findByOrderId`), guard, set `RELEASED`, `releasedAt`, `releaseReason`, move `reserved→available`, save. Idempotent: 3 payment events plus the timer can race. | `@transition(from: RESERVED, to: RELEASED)`, M-10 |
| G-CI-03 | major | `…/InventoryServiceImpl.kt:53` | `confirmStockReservation` creates an unused `StockReservation()` and maps the event from the *input*, so `reservationId` is null. Load, guard, set `CONFIRMED`, save, then move `reserved→sold` ([Q-08](#q-08)). | `@transition(from: RESERVED, to: CONFIRMED)`, M-06 |
| G-CI-04 | blocker | `catalog/inventory/events/processing/mappers/PaymentsProcessingEventsListenerMapStructMapper.kt` | `reason` is mapped with `Enum.valueOf(StockReleaseReason, event.reason)` from free text, which throws `IllegalArgumentException`. Map a **constant** per source event: `PAYMENT_DECLINED`, `PAYMENT_RETRY_EXHAUSTED`, `PAYMENT_VOIDED`. `sku`/`quantity` are null, causing an NPE; relax the input to `orderId` + `reason` and let the service look them up. | ZDL `StockReleaseReason` |
| G-CI-05 | minor | `catalog/inventory/service/impl/mappers/EventsMapper.kt` | Unmapped event fields: `StockReleased.reason` ← `releaseReason`; `StockReservationConfirmed.reservationId` (source is input, see G-CI-03). | — |

### 2.3 payments-processing (steps 3, 4, 5, 9, 12, 12b)

| Id | Sev | Where | Gap → expected behavior | Model ref |
|---|---|---|---|---|
| G-PP-01 | blocker | `payments/processing/events/checkout/mappers/OrdersCheckoutEventsListenerMapStructMapper.kt` | `customerId`, `amount`, `currency`, `paymentMethodId` are null, causing an NPE on the first order. Blocked by M-01. | M-01 |
| G-PP-02 | major | `payments/processing/service/impl/PaymentsProcessingServiceImpl.kt:34` | `authorizePayment` emits Authorized, Declined **and** Failed. It needs a `PaymentProvider` port and a stub adapter, `paymentId`, `attemptCount=1`, an outcome → status + timestamp + exactly one event, and save. | ZFL `@outcome(authorized\|declined\|failed)` |
| G-PP-03 | major | `…/PaymentsProcessingServiceImpl.kt:47` | `retryPayment` has no trigger (M-09). Load by `paymentId`, guard `FAILED`, compare `attemptCount` with the maximum ([Q-05](#q-05)), then emit `PaymentRetried` or `PaymentRetryExhausted` and set `RETRY_EXHAUSTED`. Add in-process listeners for `PaymentFailed→retryPayment` and `PaymentRetried→authorizePayment` (re-authorize the existing payment). | ZFL steps 4–5 |
| G-PP-04 | major | `…/PaymentsProcessingServiceImpl.kt:59` and `payments/processing/events/shipping/mappers/…` | `FulfillmentScheduled` has no `paymentId`, causing an NPE. Look up the payment by `orderId` (`PaymentRepository.findByOrderId`). Guard `AUTHORIZED`, capture through the provider, then emit **one** of `PaymentCaptured`/`PaymentCaptureFailed` and set `capturedAt`. | `@transition(from: AUTHORIZED, to: CAPTURED)` |
| G-PP-05 | major | `…/PaymentsProcessingServiceImpl.kt:71` | `voidPayment` has the same `paymentId` NPE for `FulfillmentFailed`, and no listener for `PaymentCaptureFailed` (M-09). Load, guard, void through the provider, set `VOIDED`/`voidedAt`, save. | ZFL `@compensates(scheduleFulfillment)` |
| G-PP-06 | minor | `payments/processing/service/impl/mappers/EventsMapper.kt` | Unmapped: `reason` on Declined/Failed/RetryExhausted/CaptureFailed/Voided (← `failureReason` or input), `PaymentRetried.retriedAt`, `PaymentRetryExhausted.exhaustedAt`. | — |

### 2.4 fulfillment-shipping (step 8)

| Id | Sev | Where | Gap → expected behavior | Model ref |
|---|---|---|---|---|
| G-FS-01 | blocker | `fulfillment/shipping/events/checkout/mappers/OrdersCheckoutEventsListenerMapStructMapper.kt` | `customerId`, `shippingAddress`, `items` are null, causing an NPE. Blocked by M-01/M-02. | M-01 |
| G-FS-02 | major | `fulfillment/shipping/service/impl/FulfillmentShippingServiceImpl.kt:35` | Emits both `FulfillmentScheduled` and `FulfillmentFailed`. It needs a warehouse/carrier port that decides the outcome, `fulfillmentId`, `scheduledAt`/`failedAt`/`failureReason`, and save. | `withEvents [FulfillmentScheduled \| FulfillmentFailed]`, M-11 |
| G-FS-03 | minor | `fulfillment/shipping/service/impl/mappers/EventsMapper.kt` | `FulfillmentFailed.reason` ← `failureReason` is unmapped. | — |

### 2.5 notifications-consumer (steps 10, 11, 15)

| Id | Sev | Where | Gap → expected behavior | Model ref |
|---|---|---|---|---|
| G-NC-01 | blocker | `notifications/consumer/events/*/mappers/*MapStructMapper.kt` | All 3 mappings are missing `email`; stock-unavailable is also missing `customerId` and `sku`; cancelled is also missing `customerId` and `reason`. Each causes an NPE. Blocked by M-01/M-04/M-07. | M-01 |
| G-NC-02 | major | `notifications/consumer/service/impl/NotificationsConsumerServiceImpl.kt:34,46,58` | Each method emits *Sent **and** `NotificationDeliveryFailed`. Each needs `notificationId`, `type`, `channel`, `recipient`, `subject`/`body` from a template ([Q-10](#q-10)), `createdAt`, a `NotificationSender` port, the status `SENT`/`FAILED` + timestamp, exactly one event, and save. | `withEvents [*Sent \| NotificationDeliveryFailed]` |
| G-NC-03 | minor | `notifications/consumer/service/impl/mappers/EventsMapper.kt` | `NotificationDeliveryFailed.reason` ← `failureReason` is unmapped. `OrderCancelledNotificationSent.reason` has no source on `Notification`. | M-07 |

### 2.6 Flow-level tests

| Id | Sev | Gap |
|---|---|---|
| G-FT-01 | major | No end-to-end test for any ZFL `end` outcome. Add Spring Modulith `Scenario` tests (or `@SpringBootTest` + published events) for `completed`, `stockGone` and `orderCancelled`, and within `orderCancelled` for each cause: declined, retry-exhausted, fulfillment failed, capture failed, reservation expired. |
| G-FT-02 | major | 19 service tests are empty `// TODO` stubs across `src/test/kotlin/com/arcadiaeditions/*/*/service/impl/*Test.kt`. Each needs a happy path, each alternative outcome, an invalid transition, not-found, and idempotent redelivery. |
| G-FT-03 | major | `src/test/kotlin/com/arcadiaeditions/orders/checkout/web/OrdersCheckoutApiIntegrationTest.kt:49-50` asserts that `confirmedAt` and `cancelledAt` are **not empty** on a new order, which becomes wrong once the behavior exists. It posts a random SKU that isn't in the fixtures. `OrdersCheckoutApiControllerTest` posts empty `items` (violating `minlength(1)`) and expects 201. |
| G-FT-04 | minor | Each `ServicesInMemoryConfig` builds services with only a repository and a publisher. It must supply stubs for new dependencies: `InventoryService` in orders, and the provider, carrier and sender ports. |

---

## 3. Baseline: not touched by any flow

| Id | Sev | Where | Gap |
|---|---|---|---|
| G-CI-06 | blocker | `catalog/inventory/service/impl/InventoryBackofficeServiceImpl.kt:34` | `createInventoryPosition`: `initialQuantity` isn't mapped to `availableQuantity`, and `reserved`/`sold` aren't initialised to 0 (compiler warning). Required columns will be null. Also needs an `sku` uniqueness check (409). |
| G-CI-07 | major | `…/InventoryBackofficeServiceImpl.kt:43,54` | `receiveStock`/`adjustStock` build a new entity. Load by `sku` (`findBySku`), apply the quantity arithmetic, reject a negative result ([Q-14](#q-14)), save. The events need input data (`quantity`, `quantityDelta`, `reason`, `reference`, timestamps), which is currently unmapped. |
| G-CI-08 | major | `zenwave-scripts.zw` | `InventoryBackofficeService` is `@rest("/inventory")` but has no OpenAPI file and no `OpenAPIControllersPlugin`, so there's no controller. |
| G-CP-01 | minor | `catalog/products/service/impl/mappers/EventsMapper.kt` | `ProductPublished.productId` (String) ← `id` (Long), `publishedAt`, `ProductRetired.productId`, `retiredAt` are unmapped. |
| G-CP-02 | minor | `catalog/products/service/impl/ProductCatalogServiceImpl.kt:34` | Needs an `sku` uniqueness check (409 instead of a DB constraint error). |
| G-CP-03 | major | `zenwave-scripts.zw` | `ProductCatalogService` is `@rest("/products")` but has no OpenAPI file or controller. |
| G-FS-04 | minor | `fulfillment/shipping/service/impl/FulfillmentShippingServiceImpl.kt:47` | `markReadyToShip` is implemented, but nothing calls it (no REST, no listener). [Q-09](#q-09). |

Products and `markReadyToShip` are otherwise fully generated. For CRUD with transitions, ZenWave
produces working bodies.

---

## 4. Cross-cutting

| Id | Sev | Gap |
|---|---|---|
| X-01 | major | `config/WebExceptionsTranslator.kt` only handles bean validation. Map `NoSuchElementException` → 404, `InvalidStateTransitionException` → 409, and domain errors such as stock unavailable → 409/422. Otherwise they all return 500. |
| X-02 | major | No repository has natural-key finders (`findByOrderId`, `findBySku`, `findByPaymentId`, `findByReservationId`, …). Every event-driven command addresses aggregates by business id. |
| X-03 | major | Listener idempotency. `ExternalizationConfiguration.resubmitIncompletePublications()` re-delivers events, so every `@ApplicationModuleListener` target must tolerate duplicates and out-of-order delivery. |
| X-04 | major | Cross-module synchronous calls (orders → inventory) must go through an allowed module API. Verify with Spring Modulith `ApplicationModules.verify()` / `JMoleculesArchitectureTest`. |
| X-05 | major | Fixtures (`src/test/resources/data/jpa/*/1.json`, `data.sql`) have one record per entity, all for `ORD-001`, in contradictory states: Order `CONFIRMED` but reservation still `RESERVED`, payment `AUTHORIZED`, fulfillment `SCHEDULED`. Needed: one record per precondition state (Order `CREATED`; reservation `RESERVED`/`CONFIRMED`; payment `FAILED`, `AUTHORIZED`, `AUTHORIZATION_REQUESTED` with attempts at max-1; an `InventoryPosition` with 0 available). `expiresAt` = +15 min, but ZFL says 10 min. |
| X-06 | minor | Timestamps use no injectable `Clock`. Add one so expiry and timestamp logic are testable. |
| X-07 | minor | Generated TODO comments are misleading: `confirmOrder` says to emit `StockReservationConfirmed` with `ReserveStockInput`. Report this to the ZenWave template. |
| X-08 | minor | A build with JDK 21 fails with `release version 24 not supported`. The README lists "Java 25+", but nothing enforces it; add a Maven enforcer rule so the error is clear. |

---

## 5. Open questions (→ `/speckit.clarify`)

| Id | Question | Blocks |
|---|---|---|
| <a id="q-01"></a>Q-01 | Where do `customerId`, e-mail, shipping address and payment method come from? The checkout request, an authenticated session, or a Customer context that isn't modeled yet? | M-01 |
| <a id="q-02"></a>Q-02 | Is an order one SKU (limited drop, quantity 1), or multiple lines with quantities? One reservation per order or per line? | M-02 |
| <a id="q-03"></a>Q-03 | When stock is unavailable, do we persist a rejected order or none? What does `POST /orders/start-checkout` return (409? 201 with a status?) | M-05 |
| <a id="q-04"></a>Q-04 | Where does the price come from: `Product.price` looked up at checkout, or the client? Which currency? | M-01 |
| <a id="q-05"></a>Q-05 | How many payment retries, with what backoff? Must retries stop at reservation expiry? | G-PP-03 |
| <a id="q-06"></a>Q-06 | Which payment provider? Is a stub adapter enough for now? | G-PP-02 |
| <a id="q-07"></a>Q-07 | How is `ReservationExpired` scheduled: `@Scheduled` polling on `StockReservation.expiresAt`, or a delayed message? How does it "stop when payment is terminal"? | M-08 |
| <a id="q-08"></a>Q-08 | When does stock count as sold: at `confirmStockReservation` or at `PaymentCaptured`? How is a CONFIRMED reservation returned to stock after void? | M-10, G-CI-03 |
| <a id="q-09"></a>Q-09 | What makes `scheduleFulfillment` fail (warehouse/carrier port)? Who calls `markReadyToShip`? | G-FS-02, G-FS-04 |
| <a id="q-10"></a>Q-10 | What is the notification channel (EMAIL only?), where do templates come from, which sender? Is `NotificationDeliveryFailed` retried? | G-NC-02 |
| <a id="q-11"></a>Q-11 | Should `ProductPublished` create the `InventoryPosition` (with `editionSize`) automatically? | baseline |
| <a id="q-12"></a>Q-12 | Does the customer need `GET /orders/{orderId}` to see the async outcome? | web |
| <a id="q-13"></a>Q-13 | What's the idempotency policy for duplicate or late events (e.g. `StockReleased` for an already-cancelled order: ignore or error)? | X-03 |
| <a id="q-14"></a>Q-14 | Can `adjustStock` take `availableQuantity` below 0? | G-CI-07 |
| <a id="q-15"></a>Q-15 | Is `StockUnavailable` owned by orders-checkout (ZDL) and triggered via the `reserveStock` result (ZFL), with no inventory-side event? Please confirm. | M-03/M-04 |

---

## 6. Suggested Spec Kit mapping

Modulith, so one feature per ZFL flow, with one phase per bounded context in flow order:

- **`001-place-order-flow`**
  - Phase 0: Clarify Q-01…Q-15. Fix M-01…M-12 in ZFL/ZDL. Regenerate with `zenwave-scripts.zw`.
    Re-run this report and confirm the NPE rows are gone.
  - Phase 1: Foundations X-01…X-06: natural-key finders, error mapping, `Clock`, ports + stub
    adapters, fixtures per state.
  - Phase 2: orders-checkout `startOrderCheckout` + catalog-inventory `reserveStock` (steps 1–2).
  - Phase 3: payments `authorizePayment`/`retryPayment` + listeners (steps 3–5).
  - Phase 4: orders `confirmOrder` + inventory `confirmStockReservation` (steps 6–7).
  - Phase 5: fulfillment `scheduleFulfillment`, payments `capturePayment`/`voidPayment` (steps 8, 9, 12).
  - Phase 6: compensations: inventory `releaseStock` + scheduler, orders `cancelOrder` (13, 13b, 14).
  - Phase 7: notifications (10, 11, 15).
  - Phase 8: flow tests G-FT-01 for each `end` outcome.
- **`000-catalog-inventory-baseline`**: G-CI-06…08.
- **`000-catalog-products-baseline`**: G-CP-01…03.

---

## 7. Implementation status

Phase 1 (foundations), 2026-09-27:

- **X-01** ✅ `config/WebExceptionsTranslator.kt` now maps `NoSuchElementException` → 404, every
  module's `*Transitions.InvalidStateTransitionException` (Order/StockReservation/Payment/
  Fulfillment/Product) → 409, the new `common.DomainRejectionException` (base for business
  rejections, e.g. `orders.checkout.service.StockUnavailableException`) → 409, and
  `IllegalArgumentException` → 400.
- **X-02 / G-OC-06** ✅ Natural-key finders added to every JPA repository and its in-memory
  counterpart: `OrderRepository.findByOrderId`, `StockReservationRepository.findByOrderId`
  (`List`, not unique) `/findByReservationId`, `InventoryPositionRepository.findBySku`,
  `PaymentRepository.findByPaymentId/findByOrderId`, `FulfillmentRepository.findByFulfillmentId/
  findByOrderId`, `NotificationRepository.findByNotificationId`, `ProductRepository.findBySku`.
- **X-06** ✅ `config/ClockConfiguration.kt` provides `Clock.systemUTC()`; every module's
  `ServicesInMemoryConfig` provides a fixed `Clock` bean instead (`2026-01-01T00:00:00Z`).
- **Ports + stub adapters (Q-06/Q-09/Q-10)** ✅ `PaymentProvider` (payments.processing.service.ports,
  stub `StubPaymentProvider`), `FulfillmentCarrier` (fulfillment.shipping.service.ports, stub
  `StubFulfillmentCarrier`), `NotificationSender` (notifications.consumer.service.ports, stub
  `StubNotificationSender`). Each stub is the default `@Component` bean; outcomes are forced either
  via a `forced*Outcome` mutable property or (payments only) `AuthorizeRequest.paymentMethodId`
  (`pm_card_declined` / `pm_card_fail`) — see each stub's KDoc for the exact trigger values.
- **G-FT-04** ✅ Every `*ServiceImpl` constructor now takes `Clock` (all six services) plus its
  port (`PaymentsProcessingServiceImpl` → `PaymentProvider`, `FulfillmentShippingServiceImpl` →
  `FulfillmentCarrier`, `NotificationsConsumerServiceImpl` → `NotificationSender`); the params are
  wired but unused (no method bodies changed - that's phase 2+). `OrdersCheckoutServiceImpl`
  intentionally does **not** yet take `InventoryService` (deferred to phase 2 per the brief).
  Every `ServicesInMemoryConfig` updated to match.
- **X-08** ✅ `maven-enforcer-plugin:3.5.0` `requireJavaVersion [25,)` added to `pom.xml`, with a
  message pointing at the `jvmTarget`/JDK 25 requirement. (Left the version pinned to `3.5.0`: the
  locally cached `3.6.2` failed to resolve offline in this environment.)

Build/test results (JDK 25, offline `-o`):
- `mvn -o -DskipTests clean test-compile`: **SUCCESS**. Remaining `Unmapped target propert(y|ies)`
  warnings are all pre-existing gaps out of this phase's scope (service bodies, event mappers -
  phases 2+).
- `JMoleculesArchitectureTest`: **4/4 passed**.
- `*ServiceTest` (in-memory): **22/22 passed** - all currently empty `// TODO` stubs (G-FT-02), so
  passing them doesn't yet prove behavior; they'll gain real assertions once the corresponding
  service method is implemented in a later phase.

Not touched (out of phase-1 scope, deferred to later phases per the brief): service method bodies,
`InventoryService` dependency on `OrdersCheckoutServiceImpl`, event mapper fixes, fixtures (X-05),
flow-level tests (G-FT-01/02/03).

### Phase 2 - PlaceOrderFlow steps 1, 2, 6, 7, 13 (releaseStock), 14 (orders-checkout + catalog-inventory)

- **G-CI-01** ✅ `InventoryServiceImpl.reserveStock`: checks `InventoryPosition.availableQuantity`
  per line (`findBySku`; a missing position counts as 0 available); ALL-or-nothing per ZFL - if
  every line is available it creates one `StockReservation` per line (generated `reservationId`,
  `RESERVED`, `reservedAt`/`expiresAt` = +10 min via `Clock`) and moves `available → reserved` on
  each position; otherwise nothing is reserved and the outcome is `UNAVAILABLE` with one
  `StockUnavailableLine` per short/missing line. Idempotent: if reservations already exist for the
  `orderId`, they're returned as `RESERVED` without reserving again. `reserveStock` does not emit an
  event itself (ZFL: only `confirmStockReservation`/`releaseStock` do). Concurrency: both
  `StockReservation` and `InventoryPosition` already carry JPA `@Version` (unchanged), so two
  concurrent reservations racing the same SKU fail with an optimistic-lock exception on `save`
  rather than overselling; no extra locking code was needed.
- **G-CI-03 / M-06** ✅ `confirmStockReservation`: loads all reservations by `orderId`, throws
  `NoSuchElementException` if none exist, is an idempotent no-op if every reservation is already
  `CONFIRMED`, guards via `StockReservationTransitions.ensureCanConfirmStockReservation`, then sets
  `CONFIRMED`/`confirmedAt`, moves `reserved → sold` (Q-08) on each position, saves, and emits
  exactly one `StockReservationConfirmed` with all the order's items.
- **G-CI-02 / M-10** ✅ `releaseStock`: loads all reservations by `orderId`, throws
  `NoSuchElementException` if none exist, skips reservations already `RELEASED` (idempotent; if
  every reservation is already released nothing is mutated and no event is emitted, Q-13), guards
  the rest via `ensureCanReleaseStock` (`[RESERVED, CONFIRMED]`), sets `RELEASED`/`releasedAt`/
  `releaseReason`, returns quantity to `available` from `reserved` or from `sold` depending on the
  prior status, saves, and emits one `StockReleased` with items + reason. Fixed a mutable-aliasing
  bug found by the tests: the "already released" reservations must be captured *before* mutating
  the ones being released, since entities are mutated in place and share object identity with the
  list read at the top of the method - computing it after mutation double-counted a
  just-released reservation.
- **G-CI-04** ✅ `PaymentsProcessingEventsListenerMapStructMapper` (catalog-inventory):
  `reason` is now a MapStruct `constant` per source event type (`PAYMENT_DECLINED`/
  `PAYMENT_RETRY_EXHAUSTED`/`PAYMENT_VOIDED`, matching the ZDL `StockReleaseReason` enum), never
  `Enum.valueOf` on event payload text.
- **G-CI-05** ✅ `catalog/inventory/service/impl/mappers/EventsMapper.kt`: `StockReleased`/
  `StockReservationConfirmed` are composed from `(orderId, timestamp, List<StockReservation>)`
  overloads (MapStruct can't map a per-order event from a single entity), with per-item
  `asStockReleasedItem`/`asStockReservationConfirmedItem` auto-mapped by field name; `reason` maps
  from the method parameter (ultimately `ReleaseStockInput.reason`), not from the entity's
  `releaseReason`. Zero unmapped-property warnings on this mapper for `StockReleased`/
  `StockReservationConfirmed`/`StockReceived` (the pre-existing `StockAdjusted`/`StockReceived`
  input-field warnings are G-CI-06/07, baseline, out of this phase's scope).
  `InventoryServiceMapper` was pruned of the broken generated-once input-mapping methods
  (`asStockReservation(ReserveStockInput|ConfirmStockReservationInput|ReleaseStockInput)`, unused
  now that these commands build/load entities explicitly) and given a manual
  `asReserveStockResult(orderId, expiresAt, reservations)` overload for the same reason.
- **G-OC-01 / G-OC-02** ✅ `OrdersCheckoutServiceImpl.startOrderCheckout`: generates `orderId`,
  computes `totalAmount` from `items`, calls `InventoryService.reserveStock` (injected as the
  interface, see X-04), and branches on `result.outcome`: `RESERVED` → `status = CREATED`,
  `createdAt`, save, emit only `OrderCreated`; `UNAVAILABLE` → nothing is saved (Q-03/Q-15), emit
  `StockUnavailable` (mapped from `result.unavailable` + the input, G-OC-05) and throw
  `StockUnavailableException`. The method is annotated
  `@Transactional(noRollbackFor = [StockUnavailableException::class])` so Spring Modulith's event
  publication registry - which records/clears the publication inside the same transaction - still
  commits that registration even though the method then throws; every other exception still rolls
  back normally. No order row is written in the `UNAVAILABLE` branch, so there was nothing else at
  risk of being rolled back.
- **G-OC-03** ✅ `confirmOrder`: loads by `orderId` (404 if missing), idempotent no-op if already
  `CONFIRMED`, guards via `OrderTransitions.ensureCanConfirmOrder`, calls
  `InventoryService.confirmStockReservation`, sets `CONFIRMED`/`confirmedAt`, saves, emits
  `OrderConfirmed`. Ignored the generated TODO's suggestion to emit `StockReservationConfirmed`
  here (X-07 - that event belongs to inventory).
- **G-OC-04 / M-07** ✅ `cancelOrder`: loads by `orderId` (404 if missing), idempotent no-op (no
  event) if already `CANCELLED`, guards via `ensureCanCancelOrder` (`[CREATED, CONFIRMED]`), sets
  `CANCELLED`/`cancelledAt`/`cancellationReason`, saves, emits `OrderCancelled` with the reason.
- **G-OC-05** ✅ `orders/checkout/service/impl/mappers/EventsMapper.kt`: `asStockUnavailable` is now
  a `(orderId, customerId, customerEmail, StockUnavailableResult)` overload (the old
  `asStockUnavailable(order: Order)` couldn't fill `unavailableItems` - that data only exists on
  `InventoryService.reserveStock`'s result, never on the `Order` entity, since no order is
  persisted on this branch). `asOrderCreated`/`asOrderConfirmed`/`asOrderCancelled` got the
  `amount ← totalAmount` and `reason ← cancellationReason` renames they needed. Zero unmapped
  warnings on `OrderCreated`/`OrderConfirmed`/`OrderCancelled`/`StockUnavailable`.
  `OrdersCheckoutServiceMapper.asOrder/update(..., StartOrderCheckoutInput|ConfirmOrderInput|
  CancelOrderInput)` were left as-is (not a phase-2 finding) and still warn on fields the service
  sets explicitly afterwards (`orderId`, `status`, `totalAmount`, timestamps) - consistent with the
  skill's "keep the generated `mapper.update(Entity(), input)` line for the input fields" pattern.
- **X-04** ✅ `OrdersCheckoutServiceImpl` now depends on
  `com.arcadiaeditions.catalog.inventory.service.InventoryService` (its
  `@NamedInterface("InventoryService")`), never the repository or `InventoryServiceImpl`.
  `JMoleculesArchitectureTest` passes (4/4). A cheap `ApplicationModules.of(Application::class.java)
  .verify()` test was tried and then **removed**: it fails for reasons entirely outside this
  phase's scope - the whole codebase's saga-style choreography makes `catalog`/`payments`/
  `fulfillment`/`orders` a dependency cycle at the default Spring Modulith "slice" granularity
  (e.g. catalog's `PaymentsProcessingEventsListener` listening to payments' domain events, which
  the generator already wired in phase 0, long before this phase), plus every module's shared
  `common.mappers.BaseMapper` and every `*Transitions.InvalidStateTransitionException` used by
  `config.WebExceptionsTranslator` are "non-exposed" under default verification. Fixing that
  properly needs `@NamedInterface`/`package-info` work across payments/fulfillment/
  notifications/products, which the brief explicitly puts out of scope for this phase ("Don't touch
  payments, fulfillment, notifications or products service bodies") - flagged here as a new
  candidate finding (`X-04b`?) rather than silently worked around.
- **G-FT-02** ✅ `InventoryServiceTest` (12 tests) and `OrdersCheckoutServiceTest` (10 tests) now
  have real assertions: happy path, alternative outcome (`UNAVAILABLE`/idempotent-released/etc.),
  invalid transition, not-found, and idempotent redelivery for every command, each asserting exactly
  one expected event (or zero, for the idempotent/no-op cases) in the in-memory publisher. No new
  JSON fixtures were needed - the existing `data/jpa/{StockReservation,InventoryPosition,Order}`
  fixtures already covered every precondition state this phase needed (a 0-available position
  `AE-EDN-003`, a `RESERVED`/`CONFIRMED`/`RELEASED` reservation, a `CREATED`/`CONFIRMED`/`CANCELLED`
  order).
- **G-FT-03** ✅ `OrdersCheckoutApiControllerTest`: fixed the empty-`items` request (violated
  `minlength(1)` and only "passed" because nothing validated it pre-implementation) to a real item
  with a SKU/quantity `InventoryService` has stock for. `OrdersCheckoutApiIntegrationTest`: replaced
  the random SKU with a fixture SKU (`AE-PRT-002`) and quantity within stock, and changed
  `confirmedAt`/`cancelledAt`/`cancellationReason` assertions from `isNotEmpty()` to `isEmpty()` (a
  freshly created order is `CREATED`, not confirmed/cancelled). Not run (needs Docker); verified it
  compiles.
- Also updated (required by the above, not separate findings): `catalog/inventory/service/impl/
  InventoryServiceImpl.kt` constructor now also takes `InventoryPositionRepository`; `catalog/
  inventory/config/ServicesInMemoryConfig.kt` updated to match. `orders/checkout/service/impl/
  OrdersCheckoutServiceImpl.kt` constructor now also takes `InventoryService`; `orders/checkout/
  config/ServicesInMemoryConfig.kt` now composes `catalog.inventory.config.ServicesInMemoryConfig`
  internally (imported under an alias) to get a real in-memory `InventoryService` wired to the same
  fixtures `InventoryServiceTest` uses, and its `reloadTestData()` now also reloads that nested
  config so both bounded contexts' fixtures reset together.

Build/test results (JDK 25, offline `-o`):
- `mvn -o -DskipTests clean test-compile`: **SUCCESS**. No new `Unmapped target propert(y|ies)`
  warnings on any mapper touched this phase; remaining warnings across the codebase are all
  pre-existing and out of phase-2 scope (payments/fulfillment/notifications/products event mappers,
  `StockAdjusted`/`StockReceived` on catalog-inventory's own `EventsMapper` = G-CI-06/07 baseline,
  the Kotlin-data-class-`copy` false positive on two `*EventsListenerMapStructMapper`s unrelated to
  this phase's findings).
- `JMoleculesArchitectureTest`: **4/4 passed**.
- `InventoryServiceTest`: **12/12 passed**.
- `OrdersCheckoutServiceTest`: **10/10 passed**.
- `OrdersCheckoutApiControllerTest`: **1/1 passed**.

Not touched / deferred: `InventoryBackofficeServiceImpl` (G-CI-06/07, baseline, not a PlaceOrderFlow
step), `OrdersCheckoutApiIntegrationTest` not executed (Docker), the `ReservationExpired` scheduler
(Q-07, deferred by design), payments/fulfillment/notifications/products service bodies.

### Phase 3 - PlaceOrderFlow steps 3, 4, 5, 9, 12, 12b (payments-processing)

- **G-PP-01** ✅ Verified, not a code change: the regenerated `OrderCreated` (customerId,
  customerEmail, paymentMethodId, amount, currency), `FulfillmentScheduled`/`FulfillmentFailed`
  (orderId, reason) and `PaymentCaptureFailed`/`PaymentFailed` (orderId/reason, paymentId/orderId)
  events now carry every field their respective inputs need, with matching names, so MapStruct's
  default mapping already fills `AuthorizePaymentInput`/`CapturePaymentInput`/`VoidPaymentInput`/
  `RetryPaymentInput` with zero unmapped warnings and no NPE risk. The finding was written against a
  pre-regeneration snapshot of the events (M-01, since resolved in the ZDL/ZFL); no listener mapper
  in `payments/processing/events/**` needed a single `@Mapping`.
- **G-PP-02** ✅ `authorizePayment`: idempotent by `orderId` (`Payment.orderId` is unique - an
  existing payment means `OrderCreated` was redelivered, returned as-is, no provider call, no
  event). Otherwise generates `paymentId`, sets `attemptCount = 1`, calls
  `PaymentProvider.authorize`, and branches on the outcome to set exactly one of
  AUTHORIZED/authorizedAt/providerReference, DECLINED/declinedAt/failureReason, or
  FAILED/failedAt/failureReason, saves, and emits exactly one of
  PaymentAuthorized/PaymentDeclined/PaymentFailed.
- **G-PP-03** ✅ `retryPayment`: loads by `paymentId`. Per Q-13, any redelivery once the payment is
  no longer FAILED (already retried, exhausted, or moved on) is an idempotent no-op - there is no
  single "already done" target status to compare against the way there is for the other commands,
  since "not FAILED anymore" is the only state that safely subsumes every legitimate redelivery.
  `PaymentTransitions.ensureCanRetryPayment` is still called right after (defensive, always a
  no-op at that point given the short-circuit above - kept to preserve the standard load → idempotent
  → guard → mutate → save → emit shape). If `attemptCount < 3` (Q-05): bump `attemptCount`, move to
  AUTHORIZATION_REQUESTED, save, emit PaymentRetried, then call a new private `reauthorize(payment)`
  that re-runs `PaymentProvider.authorize` and applies the same outcome branching as
  `authorizePayment` (factored into a shared `applyAuthorizeResult` helper), emitting exactly one
  more event. Else (3rd attempt already spent): sets `status = RETRY_EXHAUSTED` directly and emits
  PaymentRetryExhausted(reason ← the payment's existing `failureReason`). **Model gap** (new
  finding, not fixed here per the fill-gaps skill - flagging instead of editing the generated
  `PaymentTransitions`): the ZDL's `retryPayment` `@transition` only declares `to:
  AUTHORIZATION_REQUESTED`; it has no target status for the RETRY_EXHAUSTED outcome, so
  `PaymentTransitions` has no `ensureCanExhaustRetryPayment`-equivalent guard for that branch.
  Suggest widening the ZDL to `@transition(from: FAILED, to: [AUTHORIZATION_REQUESTED,
  RETRY_EXHAUSTED])`.
  **Design decision on the deferred `PaymentRetried -> authorizePayment` step** (brief asked to
  choose and explain): implemented as a direct, synchronous call to `reauthorize(payment)` from
  inside `retryPayment`, not as a second `@ApplicationModuleListener` reacting to `PaymentRetried`
  within the payments module. Reasons: (1) this module's in-memory test harness
  (`ServicesInMemoryConfig`/`PaymentsProcessingServiceTest`) wires `EventPublisher` as a plain
  `InMemoryEventPublisher`, never Spring's real `ApplicationEventPublisher` - a genuine
  `@ApplicationModuleListener` on `PaymentRetried` would never run there, so "retry -> re-authorize"
  could not be asserted as the result of one `retryPayment()` call the way G-FT-02 requires; (2) the
  direct call still emits exactly one further event in addition to PaymentRetried, so the "one event
  per command" boundary is preserved even though it isn't event-sourced internally; (3) it avoids a
  window where a crash or lost in-process event leaves the payment stuck at
  AUTHORIZATION_REQUESTED with nothing to pick it back up. `reauthorize` calls the provider with
  `paymentMethodId = ""` (Payment doesn't persist that field - it's not part of the ZDL aggregate),
  so the stub's `pm_card_declined`/`pm_card_fail` magic values can't be replayed on retry; tests
  drive the retry outcome via `StubPaymentProvider.forcedAuthorizeOutcome` instead.
- **G-PP-04** ✅ `capturePayment(CapturePaymentInput{orderId})`: loads by `orderId`
  (`PaymentRepository.findByOrderId`, fulfillment events carry no `paymentId`). Idempotent if
  already CAPTURED, guarded to AUTHORIZED. `PaymentProvider.capture` outcome CAPTURED → status
  CAPTURED/capturedAt/providerReference, emits PaymentCaptured (with `customerEmail` for the
  downstream confirmation e-mail). Outcome FAILED → the ZDL only models `AUTHORIZED -> CAPTURED`
  (no modeled failure status; the compensating VOIDED transition belongs to `voidPayment`, triggered
  separately from `PaymentCaptureFailed`), so the payment stays AUTHORIZED, only `failureReason` is
  updated, and PaymentCaptureFailed is emitted with `reason`/`failedAt` from the capture attempt
  (not the entity's own `failedAt`/`failureReason`, which are reserved for authorization failures).
  `FulfillmentShippingEventsListenerMapper.asCapturePaymentInput(FulfillmentScheduled)` needed no
  fix - `orderId` already matches by name.
- **G-PP-05** ✅ `voidPayment(VoidPaymentInput{orderId, reason})`: loads by `orderId`. Idempotent if
  already VOIDED, guarded to `[AUTHORIZED, AUTHORIZATION_REQUESTED, FAILED]`. The
  same-module listener `PaymentsProcessingServiceEventsListener.voidPayment` on `PaymentCaptureFailed`
  already existed and needed no change (its mapper already matches `orderId`/`reason` by name). The
  ZDL declares a single outcome (`withEvents PaymentVoided`, no failure event) - since voidPayment is
  itself the compensating action, a provider-side void failure isn't turned into a fabricated status;
  it's thrown as `IllegalStateException` (nothing saved, no event emitted), leaving the event
  publication incomplete so Spring Modulith's resubmission (X-03) retries the whole command later.
  On success: VOIDED/voidedAt, emits PaymentVoided with `reason` from the input (never persisted on
  `Payment`) and `providerReference` carried over from the entity.
- **G-PP-06** ✅ `service/impl/mappers/EventsMapper.kt`: `PaymentDeclined.reason`/`PaymentFailed.reason`
  now `@Mapping(source = "failureReason")`. `PaymentRetried.retriedAt`,
  `PaymentRetryExhausted.{reason,exhaustedAt}`, `PaymentCaptureFailed.{reason,failedAt}` and
  `PaymentVoided.reason` are composed manually from `(payment, extraValue(s))` overloads (same
  pattern as catalog-inventory's G-CI-05) since none of those fields live on `Payment` (no
  retry/exhaustion timestamp columns; a capture failure must not reuse the entity's own
  authorization `failureReason`/`failedAt`; void's `reason` is caller input). Zero unmapped-property
  warnings on this mapper. `PaymentsProcessingServiceMapper` (the generated-once input mapper used
  only by `authorizePayment`'s `update(Payment(), input)`) was left as-is, consistent with the
  phase-2 pattern for `OrdersCheckoutServiceMapper` - its warnings are all fields the service sets
  explicitly afterwards.
- **G-FT-02** (payments part) ✅ `PaymentsProcessingServiceTest`: 20 real tests (was 4 empty `//
  TODO` stubs) - happy path, every alternative outcome (declined, failed, retry → re-authorized
  incl. a forced decline/fail on the retry itself, retry exhausted at the 3rd attempt), invalid
  transition, not-found, and idempotent redelivery for all four commands, each asserting the exact
  expected event(s) (in order, for retryPayment's two-event case) in `InMemoryEventPublisher`, plus
  one test asserting a provider-rejected void throws and changes nothing. `forced*Outcome` on the
  shared `StubPaymentProvider` bean is reset in `@BeforeEach` (it isn't recreated by
  `reloadTestData()`, unlike the repository). One new fixture: `data/jpa/Payment/7.json` /
  `data.sql` (`PAY-007`, orderId `ORD-008`, FAILED, `attemptCount = 3`) - the only missing
  precondition state (a payment on its last attempt, for the retry-exhausted test); every other
  state (AUTHORIZATION_REQUESTED, AUTHORIZED, CAPTURED, FAILED@1, DECLINED, VOIDED) already existed
  in the phase-1 fixtures. `ORD-008` has no `checkout_order` row: `retryPayment`/`capturePayment`/
  `voidPayment` never call `OrdersCheckoutService`/`InventoryService`, so payments fixtures don't
  need a matching order the way `authorizePayment`'s trigger (`OrderCreated`) would.

Build/test results (JDK 25, offline `-o`):
- `mvn -o -DskipTests clean test-compile`: **SUCCESS**. Zero `Unmapped target propert(y|ies)`
  warnings on `payments/processing/service/impl/mappers/EventsMapper.kt` (the only payments mapper
  touched this phase); the two remaining payments-related warnings are both pre-existing and
  untouched: `PaymentsProcessingServiceMapper`'s `asPayment`/`update` input-mapping warnings (same
  accepted pattern as `OrdersCheckoutServiceMapper` in phase 2) and a Kotlin-data-class `copy`
  false positive on `FulfillmentShippingEventsListenerMapStructMapper` (unrelated to any phase-3
  finding). No new warnings introduced anywhere else.
- `JMoleculesArchitectureTest`: **4/4 passed**.
- `PaymentsProcessingServiceTest`: **20/20 passed**.
- `InventoryServiceTest`: **12/12 passed**, `InventoryBackofficeServiceTest`: **3/3 passed**,
  `OrdersCheckoutServiceTest`: **10/10 passed** (re-run to confirm the new `Payment` fixture didn't
  disturb shared fixtures - it didn't, `Payment` fixtures are independent of `Order`/
  `StockReservation`).

Not touched / deferred: `PaymentsProcessingServiceMapper` input-mapping warnings (pre-existing,
consistent with the phase-2 pattern), fulfillment/notifications/products service bodies, flow-level
tests (G-FT-01, Docker-only integration tests).

### Phase 4 - PlaceOrderFlow step 8 (fulfillment-shipping) and steps 10, 11, 15 (notifications-consumer)

- **G-FS-01 / G-NC-01** ✅ Verified, not a code change: like G-PP-01 in phase 3, the finding was
  written against a pre-regeneration snapshot. `OrderConfirmed` (customerId, items, shippingAddress
  - `OrderConfirmedItem`/`OrderConfirmedShippingAddress` field names match `FulfillmentItemInput`/
  `ShippingAddressInput` exactly) and `PaymentCaptured`/`StockUnavailable`/`OrderCancelled` (email,
  customerId, unavailable items/skus, reason) now carry every field their respective
  `ScheduleFulfillmentInput`/`SendOrderConfirmationInput`/`SendStockUnavailableNotificationInput`/
  `SendOrderCancelledNotificationInput` need. Confirmed by compiling and grepping for warnings on
  `fulfillment/shipping/events/checkout/mappers/**` and `notifications/consumer/events/{checkout,
  processing}/mappers/**`: zero, on all four listener mappers.
- **G-FS-02 / M-11** ✅ `scheduleFulfillment`: idempotent by `orderId` (`Fulfillment.orderId` is
  unique - an existing fulfillment means `OrderConfirmed` was redelivered, returned as-is, no
  carrier call, no event). Otherwise generates `fulfillmentId`, stores `shippingAddress`/`items` via
  the generated `mapper.update(Fulfillment(), input)`, calls `FulfillmentCarrier.schedule`, and
  branches on the outcome: SCHEDULED → `scheduledAt`/`carrierCode`/`trackingNumber`, emits only
  `FulfillmentScheduled`; FAILED → `status = FAILED`/`failedAt`/`failureReason`, emits only
  `FulfillmentFailed`. Same shape as `authorizePayment` (G-PP-02): a create-transition with two
  outcomes, so the FAILED branch sets `status` directly rather than going through a
  `FulfillmentTransitions.ensureCan…` guard (there's none for a create).
- **G-FS-03** ✅ `fulfillment/shipping/service/impl/mappers/EventsMapper.kt`: `FulfillmentFailed.reason`
  now `@Mapping(target = "reason", source = "failureReason")` on `asFulfillmentFailed(fulfillment:
  Fulfillment)` (same pattern as `PaymentDeclined`/`PaymentFailed` in payments' `EventsMapper`, not
  the "extra parameter" pattern - `failureReason` is already on the entity by the time it's called).
  Zero unmapped warnings on this mapper.
- **G-FS-04** ✅ Verified baseline, no trigger added (Q-09, out of PlaceOrderFlow, no REST added).
  Trivial fix: `markReadyToShip` now sets `readyAt = Instant.now(clock)` before saving (previously
  left null, so `FulfillmentReadyToShip.readyAt` was always null even though the mapper had nothing
  to warn about - it just copied a null field).
- **G-NC-02** ✅ All three `send*` methods (`sendOrderConfirmation`, `sendStockUnavailableNotification`,
  `sendOrderCancelledNotification`) follow the same shape: idempotent by (`orderId`, `type`) - a new
  `NotificationRepository.findByOrderIdAndType` finder (added to the JPA interface and
  `NotificationRepositoryInMemory`, since `Notification.orderId` is present but not unique - multiple
  notification types share an order). If a notification of that type for that order is already
  `SENT`, return it as-is, no sender call, no event (Q-13). If one exists but isn't `SENT` yet (a
  stalled `PENDING` row from before this phase's fixtures, or a previous `FAILED` attempt), it's
  reused/retried in place - same `notificationId`/`createdAt`, new send attempt - rather than
  creating a duplicate row, since the ZDL's `orderId`/`type` pair has no uniqueness constraint to
  enforce "at most one row" any other way. Otherwise builds a new row: `notificationId` (`NOT-`
  prefix), `type`, `channel = EMAIL`, `recipient = customerEmail`, an inline subject/body template
  per type (order id always included; stock-unavailable lists the skus; cancelled includes the
  reason - Q-10), `createdAt`. Calls `NotificationSender.send`, then branches: SENT → `sentAt`,
  emits only the matching `*Sent` event; FAILED → `failedAt`/`failureReason`, emits only
  `NotificationDeliveryFailed`. Saved in both branches. Extracted two small private helpers
  (`prepareNotification`, `dispatch`) shared by all three methods to avoid tripling the
  save/branch/emit boilerplate; each caller still supplies its own `onSent`/`onFailed` lambda so
  each method still emits exactly one event of its own type, not a generic one.
- **G-NC-03** ✅ `notifications/consumer/service/impl/mappers/EventsMapper.kt`:
  `NotificationDeliveryFailed.reason` ← `@Mapping(target = "reason", source = "failureReason")` (entity
  field exists by mapping time, same pattern as G-FS-03). `OrderCancelledNotificationSent.reason` has
  no source on `Notification` at all (no `reason` column on the entity), so `asOrderCancelledNotificationSent`
  is now a `(notification, reason: String?)` overload, called with `input.reason` - same "extra
  parameter" pattern as `PaymentVoided`/`PaymentCaptureFailed` in payments' `EventsMapper` (G-PP-06).
  Zero unmapped warnings on this mapper.
- **G-FT-02** ✅ `FulfillmentShippingServiceTest` (7 tests: happy path, carrier-rejected-postal-code
  failure, `forcedOutcome`-driven failure, idempotent redelivery, `markReadyToShip` happy path,
  invalid transition from `READY_TO_SHIP`, not-found) and `NotificationsConsumerServiceTest` (8
  tests: happy path + undeliverable-recipient failure + idempotent-SENT redelivery for
  `sendOrderConfirmation`; happy path + FAILED-retried-in-place for `sendStockUnavailableNotification`;
  happy path + PENDING-retried-in-place + idempotent-SENT redelivery for
  `sendOrderCancelledNotification`) now have real assertions, each asserting exactly one expected
  event (or zero, for the idempotent no-op cases) in the in-memory publisher. Both stubs'
  `forced*Outcome`/`forcedOutcome` are reset to `null` in `@BeforeEach`, matching
  `PaymentsProcessingServiceTest`'s pattern. No new JSON fixtures were needed - the existing
  `data/jpa/{Fulfillment,Notification}` fixtures (`ORD-002` SCHEDULED, `ORD-003` READY_TO_SHIP,
  `ORD-006` FAILED for Fulfillment; `ORD-003`/`ORD-005` SENT, `ORD-006` PENDING, `ORD-007` FAILED for
  Notification) already covered every precondition state this phase needed. `data.sql` was already
  in sync with these fixtures (unchanged).
- Also fixed while writing `NotificationsConsumerServiceTest`: the original `dispatch(notification,
  emit: (sent, failed) -> Unit)` draft called `emit(saved, saved)` and let the single lambda invoke
  *both* `eventPublisher.onXxxSent(...)` and `eventPublisher.onNotificationDeliveryFailed(...)`
  unconditionally - the exact "emits both events" bug this phase exists to close. Caught by writing
  the "exactly one event" assertions before finishing the refactor; fixed by splitting `dispatch`
  into two separate `onSent`/`onFailed` callback parameters, only one of which is ever invoked per
  call.

Build/test results (JDK 25, offline `-o`):
- `mvn -o -DskipTests clean test-compile`: **SUCCESS**. Zero `Unmapped target propert(y|ies)`
  warnings on `fulfillment/shipping/events/checkout/mappers/**`,
  `notifications/consumer/events/{checkout,processing}/mappers/**`,
  `fulfillment/shipping/service/impl/mappers/EventsMapper.kt` and
  `notifications/consumer/service/impl/mappers/EventsMapper.kt` (the mappers this phase touched).
  Remaining warnings on `FulfillmentShippingServiceMapper`/`NotificationsConsumerServiceMapper`
  (`asFulfillment`/`asNotification`/`update` input-mapping methods) are the same accepted
  baseline pattern as `OrdersCheckoutServiceMapper`/`PaymentsProcessingServiceMapper` in phases 2-3
  (fields the service sets explicitly after the generated `mapper.update(Entity(), input)` call).
  No new warnings introduced anywhere else.
- `JMoleculesArchitectureTest`: **4/4 passed**.
- All `*ServiceTest` (`-Dtest='*ServiceTest'`): **64/64 passed** -
  `InventoryBackofficeServiceTest` 3/3, `InventoryServiceTest` 12/12, `ProductCatalogServiceTest`
  4/4, `FulfillmentShippingServiceTest` 7/7, `NotificationsConsumerServiceTest` 8/8,
  `OrdersCheckoutServiceTest` 10/10, `PaymentsProcessingServiceTest` 20/20 (re-run confirms this
  phase's changes didn't disturb any other module's fixtures or tests).

Not touched / deferred: `FulfillmentShippingServiceMapper`/`NotificationsConsumerServiceMapper`
input-mapping warnings (pre-existing, consistent with the phase-2/3 pattern), catalog-products
service bodies, flow-level tests (G-FT-01, G-FT-03's `OrdersCheckoutApiIntegrationTest` - Docker-only
integration tests).

### Phase 5 - Catalog baseline (InventoryBackofficeService + ProductCatalogService)

- **G-CI-06** ✅ `InventoryBackofficeServiceImpl.createInventoryPosition`: sets `availableQuantity = initialQuantity`,
  `reservedQuantity = 0`, `soldQuantity = 0`. Rejects duplicate SKU with `DomainRejectionException` (409).
- **G-CI-07** ✅ `receiveStock` and `adjustStock`: load by `sku` (404 if not found), apply quantity arithmetic
  (receive: `available += quantity`; adjust: `available += quantityDelta`), reject negative result with
  `DomainRejectionException` (Q-14), save, emit events. New `EventsMapper` overloads map all required fields
  from input + clock timestamp (`quantity`, `quantityDelta`, `reason`, `reference`, `receivedAt`/`adjustedAt`).
- **G-CP-01** ✅ `catalog/products/service/impl/mappers/EventsMapper.kt`: new overloads for
  `asProductPublished(product, publishedAt)` and `asProductRetired(product, retiredAt)` that convert entity `id`
  to String and use the injected clock for timestamps. Old auto-mapped versions marked with `@Mapping(target=..., ignore=true)`.
- **G-CP-02** ✅ `ProductCatalogServiceImpl.createProduct`: added SKU uniqueness check → `DomainRejectionException` (409).
  `publishProduct` and `retireProduct` now call the new EventsMapper overloads with clock timestamp.
- **G-FT-02** ✅ `InventoryBackofficeServiceTest` (8 tests: create happy path + duplicate sku; receive happy path + not found;
  adjust happy path + negative result rejected). `ProductCatalogServiceTest` (7 tests: create happy path + duplicate sku;
  publish/retire happy path + exact event assertion; not found).

Build/test results (JDK 25, offline `-o`):
- `mvn -o -DskipTests clean test-compile`: **SUCCESS**. Zero unmapped-property warnings on
  `catalog/inventory/service/impl/mappers/EventsMapper.kt` and
  `catalog/products/service/impl/mappers/EventsMapper.kt` (old overloads marked to ignore). No new warnings
  introduced elsewhere.
- `JMoleculesArchitectureTest`: **4/4 passed**.
- `InventoryBackofficeServiceTest`: **8/8 passed** (was 3/3 empty stubs).
- `InventoryServiceTest`: **12/12 passed** (unchanged).
- `ProductCatalogServiceTest`: **7/7 passed** (was 4/4; added publish/retire tests).
- All other `*ServiceTest` (54/54): **PASSED** (no changes, no regressions).
- **Total**: **76/76 tests passed** (was 64 + 4 architecture = 68 before this phase).

Not touched / deferred: `InventoryBackofficeService` OpenAPI + controller generation (G-CI-08, needs
`zenwave-scripts.zw` run with OpenAPIControllersPlugin), `ProductCatalogService` OpenAPI + controller (G-CP-03),
the `ReservationExpired` scheduler (Q-07, deferred by design).

### Phase 6 - Flow tests (G-FT-01) and fixtures (X-05)

- **G-FT-01** ✅ `src/test/kotlin/com/arcadiaeditions/flows/PlaceOrderFlowIntegrationTest.kt`: `@SpringBootTest`
  (MOCK web environment, required by the generated controller's `NativeWebRequest` field) against the Docker Compose
  Postgres, deliberately **not** `@Transactional` (`@ApplicationModuleListener` fires after commit). Driven through
  `OrdersCheckoutService.startOrderCheckout`, awaiting the full end state with Awaitility. One test per ZFL `end`:
  `completed`, `stockGone`, and `orderCancelled` for payment declined, retries exhausted, fulfillment failed and
  capture failed. Reservation expiry is `@Disabled` (M-08/Q-07 deferred). Fresh SKU per test; stub `forced*`
  outcomes are reset after each test.
- **X-05** ✅ Verified: `data.sql` and `data/jpa/*/*.json` are consistent (ids chain across contexts,
  `expiresAt = reservedAt + 10 min`, position quantities match the reservations). No changes needed.
- Baseline test fix: 6 generated `*RepositoryIntegrationTest.updateTest`s compared timestamps to a second
  `Instant.now()` call (flaky by construction). They now use `assertNotNull`.

Results (JDK 25, `mvn -o clean verify`): surefire **77/77**, failsafe **43 run, 0 failures, 1 skipped** (reservation
expiry). BUILD SUCCESS.

### Still open after phases 1–6

| Id | What | Why open |
|---|---|---|
| M-08 / Q-07 | `ReservationExpired → releaseStock` scheduler | Deferred by decision; the flow test is a `@Disabled` placeholder. |
| M-09 (part) | `PaymentRetried → authorizePayment` edge, `RETRY_EXHAUSTED` transition | `retryPayment` re-authorizes inline and sets `RETRY_EXHAUSTED` directly; the ZDL should declare the transition. |
| M-13 (new) | `Payment` doesn't persist `paymentMethodId` | Re-authorizing on retry can't pass the original payment method to the provider. Add it to `Payment` in the ZDL. |
| M-14 (new) | Spring Modulith `ApplicationModules.verify()` fails (`src/test/kotlin/com/arcadiaeditions/ModularityTest.kt`, 80 violations, left failing on purpose) | Modules are detected at the top-level packages (`catalog`, `orders`, `payments`, `fulfillment`, `notifications`, `common`, `config`), not `<domain>.<subdomain>`. `common.mappers.BaseMapper` isn't exposed. `config.WebExceptionsTranslator` uses each module's `*Transitions.InvalidStateTransitionException`. The saga events form cycles. `JMoleculesArchitectureTest` passes. |
| G-CI-08, G-CP-03 | No REST controllers for `InventoryBackofficeService` / `ProductCatalogService` | Needs OpenAPI files plus `OpenAPIControllersPlugin` entries in `zenwave-scripts.zw` and a generator run. |
| X-07 | Misleading generated TODO comments | Report to the ZenWave templates. |
| minor | `reserveStock` checks availability per line | The same SKU on two lines isn't aggregated before the availability check. |
| minor | 34 `Unmapped target` warnings remain | They are all on `*ServiceMapper` input → entity mappings; the service sets the remaining fields explicitly. |
