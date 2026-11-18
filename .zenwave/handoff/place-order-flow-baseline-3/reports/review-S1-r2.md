VERDICT: FIX
SLICE: S1   RANGE: 85de5b1..f6bd957 (fix round: T-75 3814b2b, T-76 c67dd27)
SUITE: `JAVA_HOME=$HOME/.sdkman/candidates/java/current bash .zenwave/handoff/place-order-flow/docker-run-it.sh mvn -q verify -Dmaven.test.failure.ignore=true`
       from .zenwave/work/review-S1 (09:29:52-09:37:11, 7:19)
       → surefire 151 tests, 0 failures, 0 errors, 0 skipped (the T-60 / T-67 baseline failures are gone).
         failsafe 59 tests, 0 failures, 3 errors, 0 skipped. All 3 are flaky: each times out in the full suite and passes
         when rerun alone through the lock (13:59 and 14:08):
         - PlaceOrderFlowRetryIntegrationTest.c12PendingPaymentFailedIsRetriedAndEndsInOrderConfirmationSent:218 (alone 4.4 s)
         - PlaceOrderFlowRetryExhaustedIntegrationTest.c13PendingPaymentFailedAtTheLimitEndsInOrderCancelledNotificationSent:242 (alone 2.8 s)
         - PlaceOrderFlowLateAuthorizationIntegrationTest.lateAuthorizationForCancelledOrderIsRejectedAndVoidedAndTheOrderStaysCancelledOnce:161 (alone 6.7 s)
         Cause in each: "ConditionTimeout ... Scenario$When$EventResult ... returned <false> for input of <[]> within 30 seconds".
         PlaceOrderFlowCompletedIntegrationTest green (0.8 s). PlaceOrderFlowReservationExpiredIntegrationTest green (16.1 s + 1.0 s).
BASELINE: untouched (no *.zfl, *.zdl, *openapi*.yml, *asyncapi*.yml, src/test/resources/data* in 85de5b1..f6bd957)

PREVIOUS FINDINGS (review-S1.md)
- FIX FIRST T-52 (a throwing processor leaves AUTHORIZATION_REQUESTED stuck): resolved.
  PaymentsProcessingServiceImpl.kt:81-84 maps ExecutionException to failed(processor_error), so the outcome is FAILED +
  PaymentFailed and is retried (P-10 A, ZFL :111-112). The same path serves reauthorizePayment (:309). New tests
  authorizePaymentProcessorExceptionTest (:495) and reauthorizePaymentProcessorExceptionTest (:511, on JPA semantics) are
  green. They assert status, attemptCount, failedAt = TestClock.NOW, failureReason and the single PaymentFailed against
  fixed values, not recomputed ones.
- NOTE T-52 (reused a FAILED Payment, P-01 D): resolved by T-58. PaymentsProcessingServiceImpl.kt:100-105 is creation
  only (ZDL :119).
- NOTEs T-52 timeout, T-53, T-54, T-55, S3: still hold. The tests are green.
- SHOULD FIX T-54 (refusal reused InvalidStateTransitionException with a message that contradicted itself): resolved.
  FulfillmentShippingServiceImpl.kt:77-81 throws IllegalStateException with "payment not captured yet (paymentCapturedAt
  is null)". InvalidStateTransitionException extends IllegalStateException, and both tests rule it out explicitly with
  isNotInstanceOf (FulfillmentShippingServiceTest.kt:207-213, PlaceOrderFlowCompletedIntegrationTest.kt:229-232).
  FulfillmentTransitions.kt is untouched.
- SHOULD FIX (UNCONFIRMED) connection held during the processor call: no new evidence. The shape is unchanged
  (class-level @Transactional :19-20, call inside the caller's transaction :122/:309). It is still the person's design question.
- NOTE stale FakePaymentProcessor KDoc: partial. The class KDoc is fixed (FakePaymentProcessor.kt:12-14). Still stale:
  FakePaymentProcessor.kt:53, "ends every stalled authorization (each one throws, rolling back its transaction)": since
  T-75 a released call ends FAILED processor_error. Also stale: PlaceOrderFlowReservationExpiredIntegrationTest.kt:73-75 and
  :138, "whose throw would leave AUTHORIZATION_REQUESTED stuck (T-75, P-10)" (a T-68 file).
- NOTE PROCESSOR_TIMEOUT in two places: resolved. Both A-06 codes are on the port (PaymentProcessor.kt:66-69).
  FakePaymentProcessor.kt:36 is an alias of it.
- NOTE executor never shut down: resolved, @PreDestroy shutdownNow (PaymentsProcessingServiceImpl.kt:63-67).
- NOTE merge commits without AI-* trailers: partial. The merges from 48ba129 (T-70) on carry the trailers. The earlier
  ones are history (118f015, 6804660, 3e2c0ec, 04f786c, 7499242, 104f2ea, d72a329, 3c12441, 1de0966, 61ac850).
  New in the range: the board commit 86cdaf2 has no trailers (AGENTS.md).

MODEL AXIS
- SHOULD FIX  (not S1 code: T-59 / T-72 flow tests) the 3 flaky flow ITs above. In the full run each one is the second
  test of its class (with DirtiesContext AFTER_EACH_TEST_METHOD). The flow does run to its end: the c12 log shows
  reauthorizePayment attempt 2 → confirmOrder → … → sendOrderConfirmation, with the notification inserted at 09:36:30.16.
  The Scenario still captures no event in 30 s. So it is the test's event capture or isolation, not the flow. The skill
  requires a green suite for PASS, so the verdict is FIX, but S1 itself needs no change. UNCONFIRMED cause. To settle it:
  run each of the 3 classes alone with both its tests, to see whether the order within the class is enough.
- NOTE  T-75 PaymentsProcessingServiceImpl.kt:85-89 and :100-105. Two cases still leave the Payment
  AUTHORIZATION_REQUESTED for good: an interrupted listener thread (it rethrows), and a crash or restart during the call
  (the caller's transaction is lost). In both, the replayed OrderCreated hits the creation-only no-op. This is what ZDL
  :119 declares ("no-op when a Payment for orderId exists, in any state"), so it is not a code finding. It may be a
  model gap (no recovery for a call in flight) for the person. No proposal written: this role writes only its report.
- NOTE  T-75 :63-67. At shutdown, shutdownNow interrupts the calls in flight. A FakePaymentProcessor stall then throws
  and ends processor_error (FAILED, retried) if the caller still commits. A real processor may have received the call,
  the same as on a timeout. This is consistent with ZFL :111-112.

STANDARDS AXIS
- NOTE  FakePaymentProcessor.kt:53 stale KDoc (see above; it was T-75's item).
- NOTE  PlaceOrderFlowReservationExpiredIntegrationTest.kt:73-75, :138: the comments are stale after T-75. The test still
  waits for the timeout, which is still valid.
- NOTE  86cdaf2: orchestrator board commit without AI-Agent / AI-Model / AI-Orchestrator trailers (AGENTS.md).
- The rest of T-75 / T-76 reads like the code next to it. The A-06 constants sit on the port and the adapter refers to
  them. The refusal is an IllegalStateException with a reason, the same as the generated guards' base type.

PROPOSALS: none written (possible model gap on in-flight recovery noted above, for the person)
NOT CHECKED: the connection hold under load (Hikari metrics or a load run). The cause of the 3 flaky ITs (needs a
class-alone run). The real processor timeout and interrupt behavior (T-33, HITL). data.sql A-06 still lacks
processor_error (known, the person's change). Reviewer claude-opus-5-5. The T-75 / T-76 implementer was claude-sonnet,
so the models differ.
