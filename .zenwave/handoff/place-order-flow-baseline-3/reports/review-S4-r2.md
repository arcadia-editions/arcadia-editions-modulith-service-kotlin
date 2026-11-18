VERDICT: PASS
SLICE: S4   RANGE: 104f2ea..ef86b04   TASKS: T-64, T-65, fix T-85 (claude-sonnet)   REVIEWER: claude-opus   ROUND: r2
SUITE: from .zenwave/work/review-S4 (ef86b04, clean tree), `docker-run-it.sh mvn -q verify -Dmaven.test.failure.ignore=true`, one run, exit 0
  - waited on the shared lock 13:22-13:51, ran 13:51-13:59; every report file is from this run
  - surefire: 151 run, 0 failed, 0 errors, 0 skipped
  - failsafe: 59 run, 0 failed, 0 errors, 0 skipped. All 15 PlaceOrderFlow*IntegrationTest pass, among them CaptureFailed (22 s), the 5 Retry* tests and the 2 LateAuthorization* tests. No StaleObjectStateException in the log.
  - flaky: none seen (no failure to rerun)
BASELINE: untouched (`git diff --stat 104f2ea..ef86b04` on *.zfl, *.zdl, *openapi*.yml, *asyncapi*.yml and src/test/resources/data* is empty)

PREVIOUS FINDINGS (review-S4.md)
- SHOULD FIX T-65 "cancelled once" not proven: resolved. PlaceOrderFlowCaptureFailedIntegrationTest.kt:96-109 records OrderCancelled the same way as FulfillmentCancelled, and :182 asserts exactly one for the orderId (zfl:296-310).
- NOTE T-64 FulfillmentShippingServiceImpl.kt:108-129 matches the models: still true. No change to fulfillment main or unit-test code since 8148f39.
- NOTE T-64 cancelledAt from the Clock: still the model's choice, nothing to do.
- NOTE T-64 :116 ensureCanCancelFulfillment can never fail: unchanged, harmless (it was not part of T-85).
- NOTE recorder as a class-local nested @TestConfiguration: unchanged, still acceptable.
- NOTE import order (CopyOnWriteArrayList): resolved, :34 sits among the java.* imports.
- NOTE fully-qualified @Bean, annotation and fun on one line, lines over 120 columns: resolved. @Bean is imported (:45), annotations sit on their own lines (:100, :105, :113), and no line is over 120 columns.
- OTHER SLICES, Retry* ITs failing with StaleObjectStateException (S2/T-57): resolved by T-80. All 5 pass in this run.

MODEL AXIS (new)
- NOTE  T-85 PlaceOrderFlowCaptureFailedIntegrationTest.kt:182  The count is taken as soon as the ORDER_CANCELLED notification row exists (:168-171), not at the end of the test. A late second OrderCancelled would slip past. In practice the risk is small: OrdersCheckoutServiceImpl.kt:132 is the only place that emits it, after cancelOrder, which only StockReleased triggers (CatalogInventoryEventsListener.kt:22). Moving the assertion after the fulfillment await (:212) would make it stronger. Optional.
- No out-of-scope fields, events or endpoints in T-85 (test-only change).

STANDARDS AXIS (new)
- NOTE  PlaceOrderFlowCaptureFailedIntegrationTest.kt:100-108  The plain @EventListener records each event at publish time, inside the transaction. A publish that is rolled back and retried would be counted twice. That gives a false failure, never a false pass, so it is acceptable for a test.
- T-85 reads like the surrounding flow tests: same recorder pattern, comments cite the zfl lines, imports sorted.

OTHER SLICES (breakage in range, not S4's to judge)
- none. The suite is green at ef86b04, and the known open items (T-81, A-06 processor_error, T-84) cause no failure in it.

PROPOSALS: none
NOT CHECKED: Kafka externalization of FulfillmentCancelled and OrderCancelled (ExternalizationConfiguration is mocked in the ITs).
