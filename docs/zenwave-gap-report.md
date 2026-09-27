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
