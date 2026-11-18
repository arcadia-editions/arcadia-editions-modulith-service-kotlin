# PlaceOrderFlow: tasks

flow: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl
baseline: baseline/place-order-flow-4
branch: afk/place-order-flow

<!-- Built on 2026-11-18: the delta from baseline/place-order-flow-3 (board archived in docs/tasks/place-order-flow-baseline-3.md,
     T-52..T-87, all done but T-81) to models service-all 0.10.3 (0.10.1 + enum values with ids, stored as integers by the
     generated converters; P-11 A, reauthorizePayment FAILED -> AUTHORIZATION_REQUESTED -> outcome, which the code already does;
     P-12 A, retryPayment and reauthorizePayment return Payment?). Code regenerated in scratch from 0.10.1 and 0.10.3 and only the
     difference merged: the enums, the @Convert of the entities, the PaymentsProcessingService interface and PaymentTransitions.
     Test data: data.sql enum columns hold the ZDL ids, chains C-01..C-30 unchanged. Generator quirk: with two @transition steps
     the generated ensureCan... guard checks the from of the last step (ensureCanReauthorizePayment checks AUTHORIZATION_REQUESTED);
     nothing calls it, do not start. -->

## S1 retry loop: no Payment (P-12 A)
path: PaymentFailed or PaymentRetried for an order with no Payment > retryPayment or reauthorizePayment > no-op, no event
review: [ ]

- [ ] T-88 mechanical AFK retryPayment and reauthorizePayment return null, log and emit no event when no Payment exists for orderId, as voidPayment (carries T-81 of baseline 3, unblocked by P-12 A); retryPaymentNotFoundTest and reauthorizePaymentNotFoundTest assert null and no event instead of NoSuchElementException. Do not guard reauthorizePayment with PaymentTransitions.ensureCanReauthorizePayment (see header)
      model: ../arcadia-editions-modulith-api/payments-processing-domain-model.zdl:129-148
      test data: none (orderId ORD-MISSING has no row on purpose)
      tests: PaymentsProcessingServiceTest
      fixes: .zenwave/handoff/place-order-flow-baseline-3/reports/review-S2.md (SHOULD FIX no-op without Payment)
