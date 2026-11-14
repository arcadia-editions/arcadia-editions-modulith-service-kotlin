VERDICT: PASS
SLICE: S7 (fix round 2)   RANGE: 7e239c1..5dc4a45 (fix commits: T-46 9f5a5c9, T-45 e497ec8; T-29 9185354, T-30 d1f72e8 from r1)
SUITE: not rerun (per prompt). Full suite at 5dc4a45 (logs/suite-5dc4a45.log; reports in .zenwave/work/verify/target at 5dc4a45): surefire 98/98, failsafe 48/48 (failsafe-summary.xml: 48 completed, 0 errors, 0 failures). PlaceOrderFlowCaptureFailedIntegrationTest tests=1 failures=0 errors=0. PaymentsProcessingServiceTest 25/25 incl. capturePaymentRefusedFromCaptureFailedTest. FulfillmentShippingEventsListenerMapperTest 1/1. No flaky.
BASELINE: untouched (git diff --stat 7e239c1..5dc4a45 -- *.zfl *.zdl *openapi*.yml *asyncapi*.yml src/test/resources/data* -> empty)

PREVIOUS FINDINGS (review-S7.md)
- SHOULD FIX T-29 capture redelivery after CAPTURE_FAILED/VOIDED not idempotent -> RESOLVED.
  PaymentsProcessingServiceImpl.kt:146-154 returns the Payment unchanged for CAPTURED, CAPTURE_FAILED, VOIDED, before the guard and the processor call. PaymentsProcessingServiceTest.kt:344-357 asserts the no-op on C-17: same id, status still CAPTURE_FAILED, failedAt/failureReason kept, capturedAt null, no event. The test proves it: reloadTestData resets captureRefusal to null (ServicesInMemoryConfig.kt:41), and with that the fake approves a capture (FakePaymentProcessor.kt:71-75), so a processor call would leave the Payment CAPTURED.
- NOTE reason after PaymentCaptureFailed passes through -> RESOLVED. The comment is at PaymentsProcessingServiceEventsListenerMapper.kt:34. The mapping is unchanged and still by name.
- NOTE DECLINED/FAILED both go to CAPTURE_FAILED -> unchanged, still correct (impl:175-183).
- NOTE captureRefusal is process-wide -> RESOLVED by T-43. PlaceOrderFlowCaptureFailedIntegrationTest has @DirtiesContext (a fresh context per class), so no other flow class shares this fake. It is reset in @AfterEach (:91-94).
- NOTE (standards) mislabeled C-17 in flow IT -> PARTIAL. KDoc :51-52 fixed ("Input is the C-10 customer ... the capture failure itself is the shape of C-17"). :96 still says `/** C-17 input: the checkout of CUST-010. */`. Should be "C-10 input", as the neighbor flow tests label it.
- NOTE (standards) A-01 marker missing on CAPTURE_FAILED -> RESOLVED at impl:178. But see the CAPTURED NOTE below.

MODEL AXIS (new)
- NOTE  T-46 PaymentsProcessingServiceImpl.kt:150  no test covers the VOIDED no-op branch. C-17 covers only CAPTURE_FAILED. A capturePayment on ORD-010 / C-10 (VOIDED, voidPayment-redelivery chain) would cover it with no test-data change. It is low risk: the condition is one line and the same shape as the tested ones.
- Checked OK: the redelivery check runs before PaymentTransitions.ensureCanCapturePayment, so AUTHORIZATION_REQUESTED still throws (capturePaymentInvalidTransitionTest), and the publication stays incomplete and is retried. zdl:94 `from: AUTHORIZED, to: CAPTURED` is respected, because the no-op persists and emits nothing. A FulfillmentScheduled that finds VOIDED can only follow PaymentCaptureFailed (zfl:209-215): FulfillmentFailed, the other source of a void, excludes FulfillmentScheduled for the same order. The listener chain PaymentCaptureFailed -> voidPayment -> PaymentVoided -> releaseStock -> cancelOrder -> sendOrderCancelledNotification is unchanged since r1 and green in the 5dc4a45 IT. Nothing out of scope added.

STANDARDS AXIS (new)
- NOTE  PaymentsProcessingServiceImpl.kt:168  `// A-01 (P-02): CAPTURED is set here, as the outcome of PaymentCaptured` is wrong. CAPTURED is the declared target (zdl:94 `to: CAPTURED`), so A-01 (outcome state that is the target of no @transition) does not apply. T-45 asked for the marker "on the CAPTURE_FAILED branch (not on CAPTURED)", and it reworded the equivalent VOIDED comment (impl:208, "VOIDED is the target declared in the ZDL ..."). So the two declared-target branches now read differently. Fix: replace :168 with the :208 wording ("CAPTURED is the target declared in the ZDL (capturePayment transition), as PaymentCaptured is the outcome"), or drop it. Comment only, no behavior.
- NOTE  PaymentsProcessingServiceTest.kt:344  the name `capturePaymentRefusedFromCaptureFailedTest` no longer describes the test: it asserts a redelivery no-op, and nothing is refused. The neighbor test is `capturePaymentRedeliveryTest` (:361). A name like `capturePaymentRedeliveryFromCaptureFailedTest` fits. The board told T-46 to "fix" this test by name, so keeping the name is understandable.
- NOTE  PlaceOrderFlowCaptureFailedIntegrationTest.kt:52  after the T-45 edit the KDoc line is 170 chars and was not re-wrapped. Other lines in the file are long too, and spotless/ktlint is declared in pom.xml but bound to no phase, so no build check enforces it. Cosmetic.
- Checked OK: the T-46 condition is formatted like the neighbor code, and its comment fits the existing "redelivery of ..." comments (impl:146, :196). The test uses fully qualified Assertions, as its neighbors do.

PROPOSALS: none written
NOT CHECKED: no class rerun (the 5dc4a45 reports cover every S7 test). The VOIDED no-op branch of capturePayment is not exercised by any test (see NOTE).
