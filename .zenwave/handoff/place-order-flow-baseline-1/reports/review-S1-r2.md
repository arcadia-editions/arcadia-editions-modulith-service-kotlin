VERDICT: PASS
SLICE: S1   RANGE: 0c1fddd..5dc4a45 (fix round 2; round 1 at 2c897e8)
SUITE: not rerun, per orchestrator. Used .zenwave/handoff/place-order-flow/logs/suite-5dc4a45.log. Its reports are in
       .zenwave/work/verify/target, checked out at 5dc4a45. I tallied the XML reports: surefire 98 run, 0 fail, 0 err,
       0 skip. Failsafe 48 run, 0 fail, 0 err, 0 skip. The 8 PlaceOrderFlow*IntegrationTest are in it, and so are
       OrdersCheckoutApiIntegrationTest (5/5), NotificationsConsumerServiceTest (11/11), FakeAdaptersGatingTest (3/3)
       and ExternalizationConfigurationTest (1/1). Not flaky as far as one run shows.
BASELINE: untouched (`git diff --stat 0c1fddd..5dc4a45 -- '*.zfl' '*.zdl' '*openapi*.yml' '*asyncapi*.yml' 'src/test/resources/data*'` empty)

ROUND 1 FINDINGS
- FIX FIRST T-13/T-31 job races flow test + API IT: RESOLVED (T-38 28201ef).
  - ReservationExpiredJob.kt:21-25 now has @ConditionalOnProperty catalog-inventory.reservation-expired-job.enabled,
    matchIfMissing=true. application-test.yml:15 sets it to false.
  - Every Spring test context runs with profile "test": the 8 flow ITs directly, the others through
    BaseWebTestClientTest:13 and BaseRepositoryIntegrationTest:8.
  - PlaceOrderFlowReservationExpiredIntegrationTest:154-156 builds the job itself and runs it, as T-38 says.
  - Green at 5dc4a45: PlaceOrderFlowCompletedIntegrationTest 1/1, OrdersCheckoutApiIntegrationTest 5/5.
- NOTE T-07/T-23 OrderCreated redelivered over a FAILED Payment: RESOLVED as a model gap. P-05.md:19-22 now
  describes it.
- NOTE T-09 (a) PaymentAuthorized after CANCELLED throws; (b) confirmStockReservation runs silently over RELEASED:
  NOT RESOLVED in code (OrdersCheckoutServiceImpl.kt:88-99, InventoryServiceImpl.kt:140-145 unchanged).
  - This is S8 scope.
  - T-48 closes the reverse race: expiry no longer releases CONFIRMED lines.
  - (b) is the P-06 window, which is open for the person. Not re-raised.
- NOTE T-23 PaymentRetried with no Payment created one from placeholders: RESOLVED by T-44
  (PaymentsProcessingServiceImpl.kt:58-64, logged no-op). S4 review owns the details.
- NOTE T-14 noRollbackFor not proven by an IT: RESOLVED. T-17 PlaceOrderFlowStockGoneIntegrationTest is green and
  reaches StockUnavailableNotificationSent, so the UNAVAILABLE transaction commits its publication.
- NOTE T-12 a refused e-mail threw UnsupportedOperationException: RESOLVED (T-42 5651f66).
  - NotificationsConsumerServiceImpl.kt:57, :88, :117 call deliveryFailed (:129-136). It sets FAILED, failedAt and
    failureReason, then emits NotificationDeliveryFailed only, which matches ZDL notifications:90/94/98.
  - EventsMapper maps failureReason -> reason. The other event fields have the same names as the entity's.
- SHOULD FIX fakes ungated in src/main: RESOLVED (T-39 4a0765b).
  - All three fakes are @ConditionalOnProperty arcadia.fake-adapters.enabled=true, with no matchIfMissing.
  - FakeEmailSender.sent is capped at 100.
  - FakeAdaptersGatingTest proves the gate with an ApplicationContextRunner.
  - One part remains for the person (worker flagged it too); see the standards NOTE below.
- NOTE jMolecules hexagonal Secondary* on fakes and ports inside layered/SimpleDomain modules: NOT RESOLVED. No fix
  task covered it; it is still a NOTE.

MODEL AXIS (new)
- NOTE T-42 NotificationsConsumerServiceTest.kt:193-206  assertFailed does not check NotificationDeliveryFailed.skus
  for STOCK_UNAVAILABLE (ZDL notifications:138). By reading, MapStruct maps it by name; the test does not prove it.
- NOTE T-42 NotificationsConsumerServiceImpl.kt:42  A FAILED notification is never retried, because a redelivery
  returns the FAILED one (test redeliveryAfterFailedDoesNothingTest). This is consistent with the models, which
  declare no retry for notifications.
- NOTE T-43 PlaceOrderFlowCompletedIntegrationTest.kt:100-104,152-153
  - The SKU-001 counters are now read before the stimulus and only the -1/+1 deltas are asserted.
  - With @DirtiesContext plus ddl create plus data.sql, a fresh context always starts from the 89/6/5 of
    data/jpa/InventoryPosition/1.json, so the fixture literals could still be asserted.
  - As it stands, a wrong or missing fixture load passes unnoticed.
- NOTE T-38 InventoryServiceTest.kt:40-52  reservationExpiredJobIsGatedByPropertyTest only reads the annotation back
  by reflection, which proves nothing about the running context. The suite's green flow ITs are the real proof. An
  ApplicationContextRunner test, like FakeAdaptersGatingTest, would show it.
- Checked, OK:
  - The S1 path is unchanged since round 1 apart from the T-42 refusal branch.
  - T-47: resubmitIncompletePublicationsOlderThan(PT5M default) keeps in-flight publications from being resubmitted.
    The flow ITs still mock ExternalizationConfiguration (8 classes). That is harmless, but it is now redundant.
  - Nothing out of scope added.

STANDARDS AXIS (new)
- NOTE application-default.yml:6-7  The fakes are on in the default profile.
  - That file is the de facto local profile (localhost datasource, ddl update), which matches T-39's "local profile".
  - But a deployment that sets DATASOURCE_URL etc. by env and no profile still runs with the fakes and approves every
    card.
  - Decision for the person: a named `local` profile, or keep default. Worker raised the same question.
- NOTE property prefixes differ across the fix tasks: catalog-inventory.reservation-expired-job.*,
  arcadia.fake-adapters.enabled, app.events.resubmit-incomplete-publications-older-than. No convention is written
  down; pick one when T-33 adds real adapter config.
- NOTE ExternalizationConfiguration.kt:28-30  The companion `log` is unused since T-47 removed the debug callback.
  Dead code.
- NOTE DockerComposeInitializer.kt (T-43 14876a9)  isTestComposeRunning() now detects a reused compose by TCP ports
  15432/29092 only, no longer by compose project name.
  - Every worktree under .zenwave/work uses the same ports, so a run reuses whatever compose another worktree
    started. If two runs overlapped, ddl create plus data.sql would wipe each other's data.
  - Today docker-run-it.sh's lock serializes the runs.
  - The header comment of docker-compose-test.yml:3 ("Its own project name, so a running dev compose is never taken
    for it") still holds for the dev compose, but the name no longer plays any part.

PROPOSALS: none written.
NOT CHECKED: suite not rerun (used the 5dc4a45 log/reports). Generated MapStruct impl for asNotificationDeliveryFailed
  not read (only .class in verify/target). Behavior with arcadia.fake-adapters.enabled unset in a full app start
  (expected fail fast: no PaymentProcessor/Warehouse/EmailSender bean). Only the ApplicationContextRunner slice
  proves the gate.
