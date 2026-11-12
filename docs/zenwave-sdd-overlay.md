# Arcadia Editions Modulith (Kotlin): ZenWave SDD overlay

What every zenwave-sdd skill and agent needs to know about this project: where the models are, its shape, its
commands, and where its test data is. `AGENTS.md` points here. Replace every `<placeholder>`; the skills stop when
this file is incomplete.

The placeholders marked `<after generation: ...>` and `<after test data: ...>` are the exception. They name facts that
do not exist at install time. The skill that produces the fact fills them in (`zenwave-test-data` and
`zenwave-test-scenarios` the test data row; `zenwave-gate-check` reports the duration of the full suite as missing and
suggests the line). Skills do not stop for them. Each one is a pointer or a number on one line, never a copy of what
it points at.

## Where things are

Paths are relative to this repository's root. Models in sibling repositories are fine (`../<repo>/...`): say which
repositories must be cloned next to this one.

| What | Path |
|---|---|
| Business flows (ZFL) | `../arcadia-editions-architecture/business-flows/*.zfl` |
| Domain models (ZDL), one per bounded context | `../arcadia-editions-modulith-api/*-domain-model.zdl` |
| API contracts | `../arcadia-editions-modulith-api/*openapi*.yml` (no AsyncAPI: modulith, in-process events) |
| Glossary and ADRs | none yet; decisions in `../arcadia-editions-architecture/docs/case-study-decisions.md` |
| Domain summary | none yet: `../arcadia-editions-architecture/README.md` and `zenwave-architecture.yml` |
| Board of the flow in progress | `docs/tasks.md` (archived boards in `docs/tasks/`) |
| Model gap proposals | `docs/proposals/` |
| Test data and scenario matrix | `<after test data: src/test/resources/data.sql, and what its header holds: the chains, the fixed test "now" and how tests read it (a Clock)>` |
| Generation script | `zenwave-scripts.zw` (projects generated with ZenWave SDK) |

Required sibling clones: `../arcadia-editions-architecture` (ZFL), `../arcadia-editions-modulith-api` (ZDL, OpenAPI).

**This project reads the models from a packaged artifact:**

- artifact `com.arcadiaeditions.modulith.application.arcadia-editions-modulith:service-all:0.9.0` (GitHub Packages,
  repo id `github`), folder `com/arcadiaeditions/modulith/application/arcadia-editions-modulith/`;
- extract: `unzip -o -q -j ~/.m2/repository/com/arcadiaeditions/modulith/application/arcadia-editions-modulith/service-all/0.9.0/service-all-0.9.0.jar 'com/arcadiaeditions/modulith/application/arcadia-editions-modulith/*' -d target/models`;
- staleness (prints nothing when in sync): `for f in ../arcadia-editions-modulith-api/*.zdl ../arcadia-editions-modulith-api/*.yml; do diff -q --strip-trailing-cr "$f" "target/models/$(basename "$f")"; done`;
- repackage: release a new version from `arcadia-editions-architecture` CI, or locally `bash ../arcadia-editions-modulith-api/package-local-service-all.sh`, then bump the version in `zenwave-scripts.zw` and `pom.xml` (`arcadia-models.version`).

**The ZFL's `@zdl(...)` paths do not match** (they point at the per-context microservice repos):

| `@zdl` path | ZDL here |
|---|---|
| `orders-checkout-api/domain-model.zdl` | `../arcadia-editions-modulith-api/orders-checkout-domain-model.zdl` |
| `catalog-inventory-api/domain-model.zdl` | `../arcadia-editions-modulith-api/catalog-inventory-domain-model.zdl` |
| `payments-processing-api/domain-model.zdl` | `../arcadia-editions-modulith-api/payments-processing-domain-model.zdl` |
| `fulfillment-shipping-api/domain-model.zdl` | `../arcadia-editions-modulith-api/fulfillment-shipping-domain-model.zdl` |
| `notifications-consumer-api/domain-model.zdl` | `../arcadia-editions-modulith-api/notifications-consumer-domain-model.zdl` |

`catalog-products-domain-model.zdl` is not part of `place-order-flow` (product backoffice).

## Shape

- Deployment: **modulith** (bounded contexts are Spring Modulith modules; in-process events, transactional outbox;
  ZDL consumers use `@listener`).
- Code already in place: yes, generated with ZenWave SDK (`zenwave-tasks-from-code-and-models`).
- Stack: Kotlin 2.3, Spring Boot 4.0, Spring Modulith 2.0, MapStruct (kapt), jMolecules; JDK 25 (jvmTarget 24).
- Persistence: `jpa` (PostgreSQL; Testcontainers via docker-compose in integration tests).
- Organization prefix and packages: `com.arcadiaeditions`, modules `catalog.inventory`, `catalog.products`,
  `fulfillment.shipping`, `notifications.consumer`, `orders.checkout`, `payments.processing`; shared `common`.

## Commands

```bash
# JDK and tools
export JAVA_HOME=$HOME/.sdkman/candidates/java/current   # JDK 25; no mvnw: Maven from PATH
# regenerate after a model change (the person runs it; never the orchestrator or its workers):
# run each plugin of zenwave-scripts.zw (IDE play button, or the `jbang zw -p` command each one shows), with --force
# compile, and collect MapStruct warnings
mkdir -p target && mvn -q -DskipTests clean compile 2>&1 | tee target/compile.log
# focused tests of a task (the implementer runs only these)
mvn -q test -Dtest='TestClass'   # the task's tests:
# full suite with integration tests and containers (once per slice review)
# <after generation: full suite takes about N minutes, needs Docker / Testcontainers / ...>
mvn -q verify
# lint the models
bash .zenwave/bin/lint-zdl.sh --strict ../arcadia-editions-modulith-api/*-domain-model.zdl
# export the Hibernate DDL (JPA)
bash .claude/skills/zenwave-test-data/scripts/export-ddl.sh
```

The orchestrator gives the workers the focused command (with the task's `tests:`) and the reviewer the full one.

## Project rules

- Naming conventions (ids, channels, topics): business ids are `String` (`orderId`, `reservationId`, `sku`); events named in past tense, see the ZDLs
- Decisions already taken that the models don't show: `../arcadia-editions-architecture/docs/case-study-decisions.md`
