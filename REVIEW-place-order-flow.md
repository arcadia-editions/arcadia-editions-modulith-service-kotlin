# Independent review: place-order-flow (`afk/place-order-flow` vs `baseline/place-order-flow-1`)

Unit tests were run in a scratch clone with `mvn -q -o test`: 102 tests, 0 failures (JDK 25). Integration tests were not run, so every race, redelivery and end-to-end finding below is deduced by reading and marked UNCONFIRMED where it matters.

Short names used for models: `zfl` = [place-order-flow.zfl](../arcadia-editions-architecture/business-flows/place-order-flow.zfl); `orders.zdl`, `inventory.zdl`, `payments.zdl`, `fulfillment.zdl`, `notifications.zdl` = the `*-domain-model.zdl` files in [arcadia-editions-modulith-api](../arcadia-editions-modulith-api/).

Code links point at the files as they are on `afk/place-order-flow`.

---

## PART 1 - MODELS

**Verdict.** The happy path and the declined, exhausted and voided cancellations are coherent. The models are weak where payment and the timer overlap, on outcome states, and on anything about redelivery. Several steps receive fewer fields than they need, so the code has to guess.

### MAJOR

1. **Expiry does not compensate payment.** `zfl:224` releases stock on `ReservationExpired`, and `zfl:247` cancels the order, but nothing voids the payment.
   - `payments.zdl:99` allows `voidPayment` from `AUTHORIZATION_REQUESTED` and `FAILED`, yet no listener can reach it in those states (`payments.zdl:97-98` only fire after `AUTHORIZED` or `CAPTURE_FAILED`). Either these are dead from-states or a listener is missing.
   - Scenario: authorization is in flight or being retried at minute 10, the order is cancelled, the processor then approves, and the hold on a cancelled order is never voided.

2. **Timer stop condition is not observable by its owner.** `zfl:236` stops the timer on `PaymentAuthorized`, `PaymentDeclined` or `PaymentRetryExhausted`.
   - The timer lives in inventory (`inventory.zdl:116`), which has no listener for `PaymentAuthorized`. It only learns of it through `confirmOrder` → `confirmStockReservation` (`zfl:146-158`), which is asynchronous.
   - `confirmStockReservation` has no failure outcome for "already released" (`inventory.zdl:121-122`), so the window between `PaymentAuthorized` and confirmation is undefined.
   - The `@time` expression "not A or B or C" is also ambiguous in precedence.

3. **`PaymentRetried` cannot feed `authorizePayment`.** `zfl:132` and `payments.zdl:85` route `PaymentRetried` into `authorizePayment`.
   - The event (`payments.zdl:133-138`) lacks `customerEmail`, `amount`, `currency` and `paymentMethodId`, all required by `AuthorizePaymentInput` (`payments.zdl:56-63`).
   - `@transition(to: AUTHORIZATION_REQUESTED)` has no `from` (`payments.zdl:86`), while `orderId` is unique (`payments.zdl:27`), so the retry must reuse the FAILED row. That transition is undeclared.

4. **Outcome states have no transitions.**
   - Payment: `AUTHORIZED`, `DECLINED`, `FAILED` and `CAPTURE_FAILED` are never a `to:` target (`payments.zdl:86,94`), yet they are used as `from` (`payments.zdl:90,94,99`).
   - Fulfillment: `FAILED` is never a target (`fulfillment.zdl:54,83`).
   - Notification: `PENDING` is entered and never left; `SENT` and `FAILED` are unreachable (`notifications.zdl:55-59,89-98`).

5. **Capture failure leaves fulfillment scheduled.** `zfl:211` compensates `authorizePayment` only. After `PaymentCaptureFailed` the Fulfillment stays `SCHEDULED`, and no command moves it (`fulfillment.zdl:83-87`). The warehouse can ship a cancelled, unpaid order.

6. **Capture trigger contradicts its own text.** `zfl:173-175` says capture happens "only when the order is physically ready to leave our warehouse", but the trigger is `FulfillmentScheduled` (`zfl:178`). `markReadyToShip` / `FulfillmentReadyToShip` (`fulfillment.zdl:86-87,108`) are not in the flow.

7. **Client-supplied price.** `unitPrice` comes in the start command (`zfl:78`, `orders.zdl:36,69`, with a comment admitting it). It has no `min`, so a buyer sets the amount that is authorized.

8. **Delivery failure is a dead end.** `NotificationDeliveryFailed` (`notifications.zdl:90-98,134`) is absent from the ZFL. No retry and no end is defined (`zfl:262-266`), so the flow never terminates on that branch.

9. **Idempotency is not expressible.** No event carries an id.
   - `PaymentFailed.attemptCount` (`payments.zdl:127`) is dropped by `RetryPaymentInput` (`payments.zdl:65-68`), so a duplicate `PaymentFailed` is indistinguishable from a new one.
   - The retry limit and backoff are unspecified ("within the reservation window", `zfl:119-121`).

### MINOR

- `ReleaseStockInput.reason` is required (`inventory.zdl:68`), but `ReservationExpired` carries only `orderId` (`zfl:237-239`), and payment events carry a free-text `reason` (`payments.zdl:119,145,173`). The mapping to `StockReleaseReason` is implicit.
- `@timer(api: SchedulerApi …)` (`inventory.zdl:116`) references an API not declared in `apis {}` (`inventory.zdl:19-21`). `Scheduler` is not in `systems` (`zfl:5-40`).
- `startOrderCheckout` returns `Order` for both outcomes (`orders.zdl:96-98`), yet the ZFL says no order record exists on `StockUnavailable` (`zfl:196-197`). The event still carries an `orderId` (`orders.zdl:127`).
- `zfl:85-86` names `StockReservedResult` / `StockUnavailableResult`; the ZDL command returns the wrapper `ReserveStockResult` (`inventory.zdl:76-84,111`).
- `voidPayment` has no refused outcome (`payments.zdl:100`); `capturePayment` has no "already voided" outcome.
- `@failure` appears on `zfl:114` but not on `zfl:138` for the same outcome.
- The timer is "after OrderCreated" (`zfl:236`), but the deadline is stored on the reservation, which is created before the order (`inventory.zdl:33`).

### NOTE

- `zfl:70` mentions `OrderRequested`, which does not exist. Typos "emitsting" / "emitsted" / "emits" used as a noun: `zfl:70,121,220,244`.
- `StockReservationConfirmed` (`zfl:157`) has no consumer.
- Order has no terminal state for `completed`; it stays `CONFIRMED`.
- `OrderCancelled.reason` is a `StockReleaseReason` name. `PAYMENT_VOIDED` hides whether fulfillment or capture failed, although `zfl:253` promises the customer "why".

---

## PART 2 - GENERATED CODE (baseline)

**Verdict.** Structure is faithful to the ZDL: entities, enums, events, transition guards, one listener per `@listener`, and four layouts respected per module. The risky parts are template defaults: stubs that run and publish every alternative event, resubmission of everything every 30 s, one wrong root module descriptor, and permissive security and logging config. Nearly all findings belong to the generator, not this project.

### MAJOR (all template)

1. **Stubs are runnable and emit every alternative event.** In the baseline service implementations, `withEvents [A|B|C]` becomes unconditional publication of A, B and C on an unsaved entity. For example, baseline `authorizePayment` publishes Authorized, Declined and Failed, and `startOrderCheckout` publishes `OrderCreated` and `StockUnavailable`.
   - With the listeners wired, a baseline app runs a nonsense flow instead of failing.
   - Transition stubs load `emptyList<X>().firstOrNull()` and always throw `NoSuchElementException`.

2. **Resubmit-all default.** Baseline [ExternalizationConfiguration.kt](src/main/kotlin/com/arcadiaeditions/config/ExternalizationConfiguration.kt) calls `resubmitIncompletePublications { true }` every 30 s, including in-flight publications.
   - Every slow listener runs twice. The AFK tests record the symptom ([PlaceOrderFlowReservationExpiredIntegrationTest.kt:74-75](src/test/kotlin/com/arcadiaeditions/PlaceOrderFlowReservationExpiredIntegrationTest.kt#L74-L75)).
   - There is no attempt cap, dead-letter or purge of completed publications. `republish-outstanding-events-on-restart: true` ([application.yml:41](src/main/resources/application.yml#L41)) is unsafe with more than one instance.

3. **Module metadata.** The baseline has a single `com/arcadiaeditions/ModuleMetadata.kt` named "Payments Processing" with `allowedDependencies = ["common"]` for the whole modulith (the last ZDL wins).
   - The generated `ArchitectureTest` assumed `..core..` packages for all modules, while the ZDLs declare four layouts.
   - Module verification was meaningless or failing until AFK restructured it.

4. **Security and config defaults in the base profile** (hunks unchanged from baseline).
   - Static `admin/password` user: [application.yml:28-32](src/main/resources/application.yml#L28-L32).
   - Actuator `include: "*"`: [application.yml:16-17](src/main/resources/application.yml#L16-L17).
   - `com.arcadiaeditions: DEBUG`: [application.yml:1-3](src/main/resources/application.yml#L1-L3).
   - CSRF and frame options disabled, single role: [SecurityConfiguration.kt:25-26](src/main/kotlin/com/arcadiaeditions/config/SecurityConfiguration.kt#L25-L26).
   - `ddl-auto: update` and a DB password under the `default` profile: [application-default.yml](src/main/resources/application-default.yml).
   - `getOrder` by sequential DB id with no ownership check: [OrdersCheckoutApiController.kt:60](src/main/kotlin/com/arcadiaeditions/orders/checkout/adapters/web/OrdersCheckoutApiController.kt#L60).

5. **Service-level constraints are never enforced.** Input DTOs carry `@NotNull` / `@Min` (for example [OrderItemInput.kt:13](src/main/kotlin/com/arcadiaeditions/orders/checkout/core/inbound/dtos/OrderItemInput.kt#L13)), but no service is `@Validated`. ZDL `min(1)` is decoration unless the REST DTO repeats it (see Part 3, finding 2).

### MINOR (template)

- **PII in logs.** Every service method and the controller log the full input at debug, with DEBUG as the default level (email, address, payment method id). [LoggingAspectConfiguration.kt:44-47](src/main/kotlin/com/arcadiaeditions/config/LoggingAspectConfiguration.kt#L44-L47) points at packages that do not exist, so the aspect is dead.
- **Kotlin type choices.** Entities are `data class` with `Integer?` (the Java boxed type), which forces `as Integer` casts in all hand code. `@Cache(READ_WRITE)` is set with no cache provider in the pom (effect UNCONFIRMED).
- **`@timer` generates nothing** (no job stub). `@calls` generates only a TODO comment, with no injected dependency.
- **Event granularity.** The stub emits one `StockReleased` per reservation, while the ZDL event is per order with `items[]` (`inventory.zdl:126-135`).
- **Duplication.** `InvalidStateTransitionException` is copied per aggregate, and `naturalId` is always null ([PaymentTransitions.kt:38](src/main/kotlin/com/arcadiaeditions/payments/processing/domain/PaymentTransitions.kt#L38)).
- **Controller leftovers.** Unused injected `NativeWebRequest` and `pageOf` ([OrdersCheckoutApiController.kt:45-46,72](src/main/kotlin/com/arcadiaeditions/orders/checkout/adapters/web/OrdersCheckoutApiController.kt#L45)).
- **Externalization without events.** Externalization and Kafka are enabled, no event is `@Externalized`, and a template binding `on-customer-event` is left over ([application.yml:64-71](src/main/resources/application.yml#L64-L71)). Kafka appears to be required at boot for nothing (UNCONFIRMED).
- **Generated tests.**
  - Empty TODO tests pass green (still present: [FulfillmentShippingServiceTest.kt:130](src/test/kotlin/com/arcadiaeditions/fulfillment/shipping/FulfillmentShippingServiceTest.kt#L130)).
  - In-memory repositories have no `@Version`, constraints or transactions.
  - The API integration test uses random data.

### NOTE

- [pom.xml](pom.xml) (unchanged by AFK): Java 24 is non-LTS; Boot 4.0.0 and Modulith 2.0.0 are `.0` releases.
- `avro.version` 1.11.1 has known CVEs fixed in 1.11.3 / 1.11.4 (whether it is on the classpath is UNCONFIRMED).
- springdoc 2.8.3 on Boot 4 (compatibility UNCONFIRMED).
- A testcontainers dependency sits alongside a compose-based initializer.

---

## PART 3 - AFK CODE

**Verdict.** Every ZFL step, outcome and end is implemented and has a test, and the code reads consistently with its surroundings. It is not safe to merge as is: the timer-versus-payment race corrupts orders, quantity is unvalidated and lets a caller mint stock, and fakes are on under the `default` profile. Redelivery handling is per-command and partial, and the tests never exercise it end to end.

### BLOCKER

1. **Expiry vs `PaymentAuthorized` race (UNCONFIRMED).**
   - The job releases at the deadline ([InventoryServiceImpl.kt:99-107](src/main/kotlin/com/arcadiaeditions/catalog/inventory/service/impl/InventoryServiceImpl.kt#L99-L107)); `cancelOrder` then runs asynchronously.
   - If `PaymentAuthorized` is handled first, `confirmOrder` sees `CREATED` ([OrdersCheckoutServiceImpl.kt:93-98](src/main/kotlin/com/arcadiaeditions/orders/checkout/core/application/OrdersCheckoutServiceImpl.kt#L93-L98)) and `confirmStockReservation` silently returns when nothing is `RESERVED` ([InventoryServiceImpl.kt:142-145](src/main/kotlin/com/arcadiaeditions/catalog/inventory/service/impl/InventoryServiceImpl.kt#L142-L145)).
   - Result: the order is `CONFIRMED` with released stock, then fulfilled and captured. `cancelOrder` then cancels the `CONFIRMED` order (allowed). The customer is charged, gets both e-mails, and the stock is resold.
   - The test file acknowledges the race as "P-06" and skips it ([PlaceOrderFlowReservationExpiredIntegrationTest.kt:58-60](src/test/kotlin/com/arcadiaeditions/PlaceOrderFlowReservationExpiredIntegrationTest.kt#L58-L60)).
   - Root cause is Part 1 findings 1 and 2, but `confirmOrder` not checking that anything was confirmed is code.

2. **Negative or zero quantity and price accepted (UNCONFIRMED, not executed).**
   - Nothing validates `quantity` or `unitPrice`: the REST DTO has no minimum, and the service DTO's `@Min` is never enforced.
   - `quantity: -5` passes the shortage check and is "reserved" ([InventoryServiceImpl.kt:40-68](src/main/kotlin/com/arcadiaeditions/catalog/inventory/service/impl/InventoryServiceImpl.kt#L40-L68)): available +5, reserved −5. Any authenticated caller mints stock in a drop.
   - A negative `unitPrice` gives a negative total ([OrdersCheckoutServiceImpl.kt:56-59](src/main/kotlin/com/arcadiaeditions/orders/checkout/core/application/OrdersCheckoutServiceImpl.kt#L56-L59)), which is sent to the processor.
   - Contradicts `orders.zdl:35,68`.

### MAJOR

3. **Late authorization on a cancelled order.** If cancel wins the race, or a stalled `OrderCreated` publication is resubmitted after expiry, `authorizePayment` creates and authorizes a Payment for a `CANCELLED` order.
   - `confirmOrder` then throws on `CANCELLED` ([OrdersCheckoutServiceImpl.kt:93](src/main/kotlin/com/arcadiaeditions/orders/checkout/core/application/OrdersCheckoutServiceImpl.kt#L93)).
   - The publication is resubmitted every 30 s forever ([ExternalizationConfiguration.kt:20-23](src/main/kotlin/com/arcadiaeditions/config/ExternalizationConfiguration.kt#L20-L23)), and the hold is never voided.
   - Same pattern in `retryPayment`: a redelivered `PaymentFailed` after the status moved on throws ([PaymentsProcessingServiceImpl.kt:122](src/main/kotlin/com/arcadiaeditions/payments/processing/application/service/PaymentsProcessingServiceImpl.kt#L122)), which makes it a poison publication.

4. **Fakes enabled under the `default` profile.** [application-default.yml:6-7](src/main/resources/application-default.yml#L6-L7) turns them on whenever no profile is set.
   - A deployment that forgets the profile approves every payment and sends no mail.
   - Magic ids from the request drive behavior. `pm_card_processorNoAnswer` blocks a listener thread and a DB transaction for 120 s ([FakePaymentProcessor.kt:58-67](src/main/kotlin/com/arcadiaeditions/payments/processing/adapter/driven/processor/FakePaymentProcessor.kt#L58-L67)); a handful of requests stalls the flow.
   - Public mutable test hooks live in `src/main` ([FakePaymentProcessor.kt:39-42](src/main/kotlin/com/arcadiaeditions/payments/processing/adapter/driven/processor/FakePaymentProcessor.kt#L39-L42)).
   - Good: with any other profile and no property, there is no bean, so startup fails fast (covered by `FakeAdaptersGatingTest`).

5. **`authorizePayment` re-entry gate is too weak.** "An existing FAILED Payment means run an authorization" ([PaymentsProcessingServiceImpl.kt:49-57](src/main/kotlin/com/arcadiaeditions/payments/processing/application/service/PaymentsProcessingServiceImpl.kt#L49-L57)) cannot tell `PaymentRetried` from a redelivered `OrderCreated` or a duplicate `PaymentRetried`.
   - Extra, uncounted processor calls go beyond `MAX_PAYMENT_ATTEMPTS`.
   - Two concurrent deliveries both call the processor before `@Version` rejects one, leaving two holds (UNCONFIRMED).
   - A duplicate `PaymentFailed` while still `FAILED` double-increments `attemptCount` and emits two `PaymentRetried` ([PaymentsProcessingServiceImpl.kt:126-131](src/main/kotlin/com/arcadiaeditions/payments/processing/application/service/PaymentsProcessingServiceImpl.kt#L126-L131)).

6. **External side effects inside the DB transaction, with no idempotency key.** Authorize, capture, void ([PaymentsProcessingServiceImpl.kt:72,158,201](src/main/kotlin/com/arcadiaeditions/payments/processing/application/service/PaymentsProcessingServiceImpl.kt#L72)) and e-mail ([NotificationsConsumerServiceImpl.kt:56](src/main/kotlin/com/arcadiaeditions/notifications/consumer/service/impl/NotificationsConsumerServiceImpl.kt#L56)) happen before commit.
   - A rollback after processor approval (optimistic lock, crash) leaves a hold or capture with no row, and the resubmission repeats the call.
   - The port ([PaymentProcessor.kt:23-46](src/main/kotlin/com/arcadiaeditions/payments/processing/application/port/driven/processor/PaymentProcessor.kt#L23-L46)) carries no idempotency key, so a real adapter cannot deduplicate.

7. **`reserveStock` under contention (UNCONFIRMED).** It reads, checks and writes guarded only by `@Version` ([InventoryServiceImpl.kt:41-69](src/main/kotlin/com/arcadiaeditions/catalog/inventory/service/impl/InventoryServiceImpl.kt#L41-L69)).
   - There is no oversell, but concurrent buyers of one SKU get an optimistic-lock 500 instead of success or 409, with no retry. That is the core scenario of a drop (`zfl:89-93`).
   - Multi-SKU orders update positions in request order, so deadlock is possible.

8. **Failed notification is final.** The redelivery guard returns the existing `FAILED` record ([NotificationsConsumerServiceImpl.kt:42,72,102](src/main/kotlin/com/arcadiaeditions/notifications/consumer/service/impl/NotificationsConsumerServiceImpl.kt#L42)). The mail is never re-sent, and no end event is emitted.
   - This is pinned by a test (`redeliveryAfterFailedDoesNothingTest`).
   - There is no unique `(orderId, type)` constraint, so concurrent duplicates both send.

9. **Architecture guard rewritten by the agents.** [ArchitectureTest.kt:104-144](src/test/kotlin/com/arcadiaeditions/ArchitectureTest.kt#L104-L144) whitelists four module cycles and jMolecules layer violations by message matching.
   - `detection-strategy: explicitly-annotated` was also added ([application.yml:34](src/main/resources/application.yml#L34)).
   - The tolerance is tight and self-tested, and the cycles come from the generated layout, but an unattended agent relaxing the check it is measured by needs explicit human sign-off.

### MINOR

- **Retries have no delay.** Three attempts run back to back ([PaymentsProcessingServiceImpl.kt:39,126](src/main/kotlin/com/arcadiaeditions/payments/processing/application/service/PaymentsProcessingServiceImpl.kt#L39)), so a 2-second processor blip exhausts them. The limit of 3 and the 10 minutes ([InventoryServiceImpl.kt:62](src/main/kotlin/com/arcadiaeditions/catalog/inventory/service/impl/InventoryServiceImpl.kt#L62)) are hardcoded.
- **`voidPayment` refusal throws and retries forever** ([PaymentsProcessingServiceImpl.kt:204-207](src/main/kotlin/com/arcadiaeditions/payments/processing/application/service/PaymentsProcessingServiceImpl.kt#L204-L207)). Meanwhile the stock stays held and the order stays `CONFIRMED`, with no cap or alert.
- **`authorizePayment` returns an empty `Payment()`** when it ignores the input ([PaymentsProcessingServiceImpl.kt:61-63](src/main/kotlin/com/arcadiaeditions/payments/processing/application/service/PaymentsProcessingServiceImpl.kt#L61-L63)). Placeholder mapping (`""`, `ZERO`) is used as the signal ([PaymentsProcessingServiceEventsListenerMapper.kt:21-24](src/main/kotlin/com/arcadiaeditions/payments/processing/adapter/driving/event/processing/mapper/PaymentsProcessingServiceEventsListenerMapper.kt#L21-L24)).
- **`ReservationExpiredJob` is unbounded** ([ReservationExpiredJob.kt:35-50](src/main/kotlin/com/arcadiaeditions/catalog/inventory/events/scheduler/ReservationExpiredJob.kt#L35-L50)). It loads all expired rows each run, an order that keeps failing is retried and logged forever, and two instances collide on `@Version` (safe but noisy). Expiry can be up to 30 s late.
- **Dead code left from stubs.** The `inventoryServiceMapper` and `eventsMapper` fields are unused in `InventoryServiceImpl`, as are `asStockReleased`, `asStockReservationConfirmed` and `asStockUnavailable(order)`.
- **Duplication.** Three near-identical `send*` methods.
- **Opaque comments.** Comments cite ids (A-01, P-02, T-23, C-17) that are unreadable without the docs.
- **Data exposure.** The 409 body exposes exact `availableQuantity` per SKU ([OrdersCheckoutApiExceptionHandler.kt:34](src/main/kotlin/com/arcadiaeditions/orders/checkout/adapters/web/OrdersCheckoutApiExceptionHandler.kt#L34)). The cancel e-mail prints the raw enum ([NotificationsConsumerServiceImpl.kt:113](src/main/kotlin/com/arcadiaeditions/notifications/consumer/service/impl/NotificationsConsumerServiceImpl.kt#L113)).

### NOTE

- Clock handling is correct: an injected `Clock` everywhere and a mutable test clock. Style is mixed (`Instant.now(clock)` vs `clock.instant()`).
- No injection surface was found (derived queries only). No new secrets were added. `pom.xml` was not touched by AFK.
- `noRollbackFor` on the stock-gone path is right: the event commits and the caller gets 409 ([OrdersCheckoutServiceImpl.kt:40](src/main/kotlin/com/arcadiaeditions/orders/checkout/core/application/OrdersCheckoutServiceImpl.kt#L40)).
- Narrowing resubmission to publications older than 5 minutes is a real improvement over the baseline.

### Tests

- **Unit (102 pass).** They assert model outcomes against the approved fixtures: state, timestamps, exactly one event and its fields, and no-op redeliveries. They do not merely re-run the code. Limit: in-memory repositories have no versioning, constraints or rollback, so nothing transactional is proven.
- **Integration (9 classes, not run).** One scenario per ZFL path, asserting the end event and the end state of all five contexts.
  - Every class mocks `ExternalizationConfiguration` and disables republish, so resubmission and redelivery are never exercised end to end.
  - `@DirtiesContext` per class on a shared compose DB is slow. Cleanup is a `DELETE … LIKE` on `event_publication`.
  - The expiry test also releases unrelated fixtures (coupled to `data.sql`) and builds the job by hand, so `@Scheduled` wiring is untested.
- **Gaps.** Race 1; late authorization; negative quantity or price; concurrent reserve; duplicate `PaymentFailed` / `PaymentRetried`; `OrderCreated` redelivery on a `FAILED` payment; void refused; notification retry. The 400 test covers only an over-long currency.
- Approved test data is untouched (only `application-test.yml` changed under `src/test/resources`).

---

## Completeness matrix (Part 3)

Abbreviations: `Pay` = `PaymentsProcessingServiceImpl.kt`, `Inv` = `InventoryServiceImpl.kt`, `Ord` = `OrdersCheckoutServiceImpl.kt`, `Ntf` = `NotificationsConsumerServiceImpl.kt`, `Ful` = `FulfillmentShippingServiceImpl.kt`. IT = `PlaceOrderFlow<Name>IntegrationTest` (not run).

| ZFL / ZDL element | Implemented | Test | Status |
|---|---|---|---|
| `startOrderCheckout` → `OrderCreated` (`zfl:82-85`) | Ord:41-63 | `startOrderCheckoutTest`, IT Completed | OK |
| `startOrderCheckout` → `StockUnavailable` (`zfl:86`) | Ord:65-80, handler 409 | `…StockUnavailableTest`, IT StockGone | OK; no Order persisted |
| `reserveStock`, both results (`zfl:95-99`) | Inv:35-89 | `reserveStock*Test` (3) | OK; findings 2, 7 |
| `authorizePayment` on `OrderCreated`: authorized / declined / failed | Pay:42-113 | `authorizePayment*Test`, IT Completed, Declined | OK; outcome states undeclared in ZDL |
| `retryPayment` → retried / retry-exhausted (`zfl:124`) | Pay:115-138 | `retryPayment*Test`, IT Retry, RetryExhausted | OK; limit and no-delay guessed |
| `authorizePayment` on `PaymentRetried` (`zfl:132`) | Pay:49-57, mapper placeholders | `…OnPaymentRetried*Test` (5), IT Retry, RetryDeclined | Diverges by need (Part 1, finding 3); finding 5 |
| `confirmOrder` + `confirmStockReservation` (`zfl:146-158`) | Ord:84-100, Inv:136-166 | `confirmOrder*Test`, `confirmStockReservation*Test` | OK; finding 1 |
| `scheduleFulfillment` → Scheduled / Failed (`zfl:166`) | Ful:34-66 | `scheduleFulfillment*Test`, IT FulfillmentFailed | OK |
| `capturePayment` → Captured / CaptureFailed (`zfl:178`) | Pay:140-187 | `capturePayment*Test` (7), IT CaptureFailed | OK |
| `voidPayment` on `FulfillmentFailed`, `PaymentCaptureFailed` (`zfl:211`) | Pay:189-215 | `voidPayment*Test` (7), mapper test | OK; refusal throws |
| `releaseStock` on Declined / RetryExhausted / Voided (`zfl:224`) | Inv:91-134, listener 20-33 | `releaseStock*Test` (5) | OK |
| `ReservationExpired` timer (`zfl:235-239`) | `ReservationExpiredJob`:35-50, Inv:99-104 | `reservationExpiredJob*Test` (4), IT ReservationExpired (stalled only) | Implemented; stop condition approximated by reservation status; race untested |
| `cancelOrder` on `StockReleased` (`zfl:247`) | Ord:102-118 | `cancelOrder*Test` (4) | OK |
| `sendOrderConfirmation` → end `completed` | Ntf:37-65 | unit, IT Completed, Retry | OK |
| `sendStockUnavailableNotification` → end `stockGone` | Ntf:67-95 | unit, IT StockGone | OK |
| `sendOrderCancelledNotification` → end `orderCancelled` | Ntf:97-126 | unit; IT Declined, RetryExhausted, RetryDeclined, FulfillmentFailed, CaptureFailed, ReservationExpired | OK, all six variants |
| `NotificationDeliveryFailed` (`notifications.zdl:90-98`) | Ntf:129-136 | `*DeliveryFailedTest` (3) | Emitted, no consumer, no retry |
| `markReadyToShip` (`fulfillment.zdl:86`) | Ful:68-81 (generated) | empty test | Not in this flow; `readyAt` never set |
| Outcome states (`AUTHORIZED`, `DECLINED`, `FAILED`, `CAPTURE_FAILED`, Fulfillment `FAILED`, Notification `SENT` / `FAILED`) | set in the services | asserted | Present in code, absent from ZDL transitions |
| Notification `PENDING` (`notifications.zdl:89`) | never persisted | none | Diverges |

---

## Generated vs AFK, and where model gaps surfaced

**Where the generated code helped.**
- Transition guards, events, listeners, mappers and the in-memory test harness were ready. AFK mostly filled method bodies and fixtures, and style stayed uniform.
- `@Version` on every aggregate prevented oversell for free.

**Where it hurt.**
- Stubs that emit all events had to be fully rewritten.
- The wrong root module descriptor and the one-layout `ArchitectureTest` pushed the agents into rewriting the architecture guard.
- `Integer?` forces `as Integer` casts everywhere.
- Unenforced DTO constraints made finding 2 invisible.
- The resubmit-all default caused duplicate deliveries, which the agents worked around in tests instead of testing idempotency.

**Model gap → code problem.**

| Part 1 finding | Surfaced as |
|---|---|
| 1, 2 (no void on expiry, timer stop not observable) | Part 3 findings 1 and 3 |
| 3 (`PaymentRetried` lacks fields) | Placeholder mapping, empty `Payment()` return, finding 5 |
| 4 (no outcome transitions) | Hand-set states justified only by comments |
| 5 (no fulfillment compensation) | Fulfillment stays `SCHEDULED` after capture failure |
| 7 (client price) | Finding 2 |
| 8 (delivery failure dead end) | Finding 8 |
| 9 (no idempotency in model) | Per-command guards of uneven strength |

---

## What I could not check

- **Integration tests and Docker**: not run, per instructions. All end-to-end, race, transaction, outbox and resubmission behavior is by reading only.
- **Baseline build**: I did not build or run it, so I do not know whether it compiled or which tests failed there.
- **Unproven by execution**: findings 1 and 2 (no test written, in order to change nothing); optimistic-lock and deadlock behavior on PostgreSQL; the effect of `@Cache`; whether Kafka is required at boot.
- **Framework semantics**: Spring Modulith 2 / Boot 4 resubmission and completion behavior is from knowledge, not verified against docs.
- **Ids in comments** (P-xx, A-xx, T-xx, C-xx): not resolved, since docs were excluded.
- **Models**: read from the sibling working copies as they are now, not pinned to the commit the code was built against.
- **Environment**: ran on JDK 25 (pom says 24), with Maven offline.
- **OpenAPI contract**: out of scope. I only looked at what the generated REST DTOs validate.
