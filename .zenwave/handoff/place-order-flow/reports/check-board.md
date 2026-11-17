CHECK: place-order-flow, zenwave-tasks-from-code-and-models check mode
CODE: .zenwave/work/integration @ d79214e (afk/place-order-flow), read-only; tree clean
SUITE: not run (per prompt). Orchestrator: dc60846 99 unit + 48 IT green
EXCLUDED (known, decided): P-02, P-05, P-06, T-33

## Steps 2-4 evidence
- lint --strict, all ZDLs: 0 errors; 4 known `zdl.data-lineage` warnings payments-processing:85 (P-01 D). Exit 0.
  (lint wrote gitignored target/zdl-lint/ in the worktree; untracked, git status clean)
- ZFL/ZDL identities: all 13 commands, all events, @compensates(authorizePayment/reserveStock/startOrderCheckout) resolve.
- compile (copy of HEAD in scratchpad, offline): OK. Unmapped MapStruct targets only where code fills fields by hand
  (StockReleased/StockReservationConfirmed items, StockUnavailable.unavailableItems, `update(...)` entity mappers) or
  out of flow (backoffice, products). No runtime-breaking gaps.
- TODO markers in src/main: only InventoryBackofficeServiceImpl, ProductCatalogServiceImpl (out of flow).
- every @listener wired (11 listeners); timer = ReservationExpiredJob; web 201/400/409/200/404 covered.
- every command: load, guard, mutate, persist, emit-only-outcome, redelivery no-op implemented and unit-tested.
- flow ends with flow ITs: completed (S1, S4), stockGone (S2), orderCancelled via declined (S3), retry-exhausted (S5),
  fulfillment failed (S6), capture failed (S7), reservation expired (S8).
- T-49 (VOIDED capture redelivery test) merged at cc5e638: closed.

## Missing

### M-1 Retry then declined: no test on any level
- model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:132-140 (`when PaymentRetried do
  authorizePayment ... @outcome(declined) emits PaymentDeclined`), zfl:223-260;
  ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:84-87
- path: OrderCreated > authorizePayment[failed] > PaymentFailed > retryPayment[retried] > PaymentRetried
  > authorizePayment[declined] > PaymentDeclined > releaseStock > StockReleased > cancelOrder > OrderCancelled
  > sendOrderCancelledNotification > end orderCancelled
- missing: code is generic (PaymentsProcessingServiceImpl.authorizePayment from FAILED -> DECLINED), but no unit test
  and no flow test take the declined branch after a retry; S4 covers only [authorized], S5 only [failed]. The
  focused fail-again test (zfl:138-139 `@outcome(failed)` on PaymentRetried) is also absent (S5 IT covers it end to
  end; review-S4-r2 NOTE). FakePaymentProcessor has no "fails then declines" method and no authorize test hook.
- test data: no FAILED Payment fixture with a declining method (C-12/C-13/C-14 all pm_card_visa). A fake hook avoids a
  fixture change; otherwise the person adds one (HITL, test data is part of the gate).
- proposed tasks:
```
- [ ] T-50 bounded AFK authorizePayment on PaymentRetried, declined and failed-again branches: FakePaymentProcessor authorize test hook (as captureRefusal) and method pm_card_processorErrorThenDecline (fails attempt 1, declines after); unit tests from C-12: declined -> DECLINED (declinedAt, failureReason), only PaymentDeclined; failed again -> FAILED (failedAt, attemptCount kept), only PaymentFailed
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:132-140, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:84-87, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:113-130
      test data: data.sql C-12
      tests: PaymentsProcessingServiceTest
      blocked by: T-23
- [ ] T-51 bounded AFK Flow test retry then declined: checkout with pm_card_processorErrorThenDecline ends in OrderCancelledNotificationSent with PAYMENT_DECLINED, payment DECLINED (attempt 2), stock back to available
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:108-140, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:223-260, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:262-266
      test data: data/jpa/InventoryPosition/1.json
      tests: PlaceOrderFlowRetryDeclinedIntegrationTest
      blocked by: T-50, T-21, T-24
```

### M-2 Model gap: NotificationDeliveryFailed reaches no end
- model: ../arcadia-editions-modulith-api/notifications-consumer-domain-model.zdl:88-98 (`withEvents [...Sent |
  NotificationDeliveryFailed]`) vs zfl:189-202, zfl:257-260 (only `...Sent`) and zfl:262-266 (no end for it)
- missing: when the e-mail provider refuses (T-42), the flow stops with no `end` outcome: no listener, no retry
  (a redelivery over FAILED is a no-op, review-S1-r2 NOTE), customer never told. ZFL and ZDL disagree; code cannot
  decide (retry policy, terminal outcome name).
- proposed task (proposal, no code):
```
- [ ] T-52 bounded HITL Proposal P-07 (zenwave-model-gap): NotificationDeliveryFailed has no ZFL branch or end outcome; options: (A) ZFL end `notificationFailed: NotificationDeliveryFailed` + emits on the three send* steps; (B) retry policy (attempt count on Notification) then A; (C) accept, ops alert only
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:189-202, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:257-266, ../arcadia-editions-modulith-api/notifications-consumer-domain-model.zdl:88-98
      tests: none (proposal)
```

### M-3 Model gap: voidPayment refused by the processor has no outcome
- model: zfl:204-215 (`@compensates(authorizePayment) ... emits PaymentVoided`),
  ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:97-100 (`withEvents PaymentVoided` only)
- missing: PaymentsProcessingServiceImpl.voidPayment throws IllegalStateException on a refused void
  (impl ~:204-207), so the publication is resubmitted every 30 s after PT5M forever; stock is never released, order
  never cancelled, no end outcome. Reviews (S6, S6-r2) filed it under P-05, but P-05 is about PaymentFailed
  redelivery and names no void outcome. Untestable today (fake always approves).
- proposed task (proposal, no code):
```
- [ ] T-53 bounded HITL Proposal P-08 (zenwave-model-gap): a refused void (compensation of authorizePayment) has no outcome; options: (A) VOID_FAILED state + PaymentVoidFailed event that releaseStock also listens to (cancel anyway, ops follow-up); (B) bounded retry then A; (C) keep throwing, alert on stale publications
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:204-227, ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:97-100
      tests: none (proposal)
```

## Not missing (checked, out of flow)
- markReadyToShip / FulfillmentReadyToShip, backoffice and products TODOs: not in place-order-flow ZFL.
- StockReservationConfirmed has no consumer: ZFL declares none.

RESULT: 3 items (1 AFK test gap -> T-50, T-51; 2 model gaps -> HITL proposals T-52, T-53). Flow not done until
M-1 is implemented and M-2/M-3 decided.
