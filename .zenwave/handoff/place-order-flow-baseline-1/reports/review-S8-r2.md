VERDICT: PASS
SLICE: S8 (fix round 2)   RANGE: 7e239c1..5dc4a45. Fix commits: 91f8067 (T-48), 1b36d28 (T-43, the S8 IT part), 4edf99e (T-47)
SUITE: not rerun, per prompt. logs/suite-5dc4a45.log (mvn -q verify at 5dc4a45): surefire 98/98, failsafe 48/48 per orchestrator. The log has no ERROR/FAILURE lines. PlaceOrderFlowReservationExpiredIntegrationTest started (14:43:17) and its context closed with "No publications outstanding!". "payment processor gave no answer" appears once only (:4997), so no resubmission loop. No flaky test seen.
BASELINE: untouched (`git diff --stat 7e239c1..HEAD -- '*.zfl' '*.zdl' '*openapi*.yml' '*asyncapi*.yml' 'src/test/resources/data*'` lists nothing)

ROUND-1 FINDINGS
- FIX FIRST T-31 (expiry check-then-act releases CONFIRMED lines): RESOLVED by T-48.
  InventoryServiceImpl.kt:97-107: when reason == RESERVATION_EXPIRED, releaseStock keeps only lines that are RESERVED with expiresAt <= clock.instant(). The check runs on rows read inside the releaseStock transaction. CONFIRMED is never released by expiry, and nothing due means no event (:108-110).
  A confirm that is still in flight, where both sides read RESERVED, is covered by @Version on StockReservation (StockReservation.kt:23): one commit fails. If expiry loses, the job logs the failure and retries on the next tick, and that run is a no-op. If confirm loses, that is the P-06 window, which stays open for the person.
  Tests: InventoryServiceTest:291-302 (C-03 CONFIRMED, past its deadline: still CONFIRMED, no event, sold 5) would fail on the old code. :305-313 covers C-01 before its deadline.
- SHOULD FIX T-32 (stalled authorization resubmitted forever): RESOLVED in tests by T-43 and T-47. Partial for local runs (see the NOTE below).
  The IT mocks ExternalizationConfiguration (IT:75), so there is no resubmit tick, and it sets republish-outstanding-events-on-restart=false (:64). @AfterEach (:103-116) releases the latch, waits for stalledOrderIds to be empty, and deletes the incomplete event_publication rows that carry pm_card_processorNoAnswer. Other contexts resubmit only publications older than PT5M (ExternalizationConfiguration.kt:18-25). The suite log shows the stall once.
  The UNCONFIRMED in-flight resubmission question from round 1 no longer applies, because of the age gate.
- NOTE T-32 C-11 Payment row absent: NOT CHANGED, still for the person (IT:54, :208).
- NOTE T-32 other fixtures released by the same timer run, and the expected stock recomputed from the job's own query: PARTIAL.
  Isolation is resolved: @DirtiesContext plus ddl-auto create and data.sql reload a fresh state per flow class, and the suite is green.
  Still open: the KDoc (IT:54-55) names only C-14, although C-01, C-02, C-12, C-13 and C-15 are also due at 12:10:01. IT:146-148 still derives releasedSku001 from findByStatusAndExpiresAtLessThanEqual, the same query the job uses.
- NOTE T-38 gating test reflects on the annotation (InventoryServiceTest:41): NOT CHANGED. Stays a NOTE.
- NOTE T-31 derived query not exercised on PostgreSQL: RESOLVED. The green flow IT calls it (IT:146) and runs the job against PostgreSQL.
- Standards NOTEs (no index on stock_reservation(status, expires_at), no cross-instance lock): NOT CHANGED, still NOTE.

MODEL AXIS (new)
- NOTE T-48 InventoryServiceImpl.kt:99: the guard depends on the reason. Only ReservationExpiredJob.kt:45 passes RESERVATION_EXPIRED (grep src/main), so no other caller depends on the old behavior. It matches zfl:235 ("not PaymentAuthorized ...") and zdl:116-117. The ZDL `@transition(from: [RESERVED, CONFIRMED])` still holds for the listener paths.
- NOTE T-47 FakePaymentProcessor.kt:53-61 with application-default.yml (fakes on): a local run that uses pm_card_processorNoAnswer still loops. The first resubmit comes after 5 min, then one every 30 s, and each holds a thread for 120 s and throws. This is narrower than in round 1 and affects the fake and local runs only.

STANDARDS AXIS (new)
- NOTE ExternalizationConfiguration.kt:27-29: the companion `log` is now unused, since T-47 removed its only use (dead code).
- NOTE ExternalizationConfiguration.kt:18: app.events.resubmit-incomplete-publications-older-than lives only as an @Value default and does not appear in application.yml next to the other app settings (application.yml:37 holds the Modulith ones). It is easy to miss when tuning.
- NOTE InventoryServiceImpl.kt:96: the comment "redelivery: only RESERVED / CONFIRMED ..." now sits above `val now` and the expiry branch. It describes only the non-expiry branch. Cosmetic.
- NOTE InventoryServiceTest.kt:315-321: the job helper builds its own InventoryServiceImpl with the job's clock, so a test clock that differs from context.clock reaches the re-check. This is justified, and the comment explains it.
- NOTE IT:110-115: the raw SQL DELETE on Modulith's event_publication, matched by the payment-method substring, is test-only cleanup. It is acceptable, and documented inline.

PROPOSALS: none. P-06 is not re-raised: the leftover race between expiry and a confirm still in flight belongs to it.
NOT CHECKED: suite not rerun (per prompt). Local default-profile behavior with pm_card_processorNoAnswer not run.
