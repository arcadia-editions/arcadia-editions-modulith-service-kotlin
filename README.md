# Arcadia Editions Modulith (Kotlin)

A Kotlin + Spring Boot service created from the ZenWave archetype. Its structure is generated from a ZenWave domain model (ZDL), and the behavior the generator leaves out is completed with the zenwave-sdd agent skills and GitHub Spec Kit.

1. [Getting Started](#getting-started)
2. [Prerequisites](#prerequisites)
3. [Technologies](#technologies)
4. [Install the ZenWave tooling](#install-the-zenwave-tooling)
5. [Code Generation](#code-generation)
   1. [From the domain model: zenwave-scripts.zw](#from-the-domain-model-zenwave-scriptszw)
   2. [At build time: OpenAPI and AsyncAPI (Maven)](#at-build-time-openapi-and-asyncapi-maven)
6. [Spec-Driven Development with zenwave-sdd](#spec-driven-development-with-zenwave-sdd)
   1. [Install the skills and Spec Kit](#install-the-skills-and-spec-kit)
   2. [Run the skills](#run-the-skills)

## Getting Started

```bash
docker-compose up -d
mvn spring-boot:run
```

SwaggerUI: http://localhost:8080/swagger-ui/index.html

## Prerequisites

* Java 25+ (Kotlin sources, compiled with `kotlin-maven-plugin`)
* Maven 3.6+
* Docker / Docker Compose (local infrastructure and integration tests)
* Git and Git Bash (all commands below are Git Bash)
* JBang and the ZenWave CLI (`jbang zw`), see [Install the ZenWave tooling](#install-the-zenwave-tooling)
* For spec-driven development: `uv` (to install Spec Kit) and an AI coding agent (Claude Code, Codex, Grok…)
* SDKMAN! (optional, recommended for Java and Maven)

## Technologies

* Kotlin and Spring Boot 4.0.x
* Spring Data JPA or MongoDB
* Spring Cloud Stream for Kafka (or other brokers), Spring Modulith (optional)
* Spring Security
* ZenWave SDK for domain modeling and code generation

## Install the ZenWave tooling

Follow **[ZenWave Getting Started](https://www.zenwave360.io/docs/getting-started/)**. It covers installing the ZenWave CLI with JBang and the IDE plugins for editing `.zdl` and `.zw` files and running the generators. Quick reference for the CLI:

```bash
jbang alias add --fresh --force --name=zw release@zenwave360/zenwave-sdk
jbang zw --help list
```

## Code Generation

Code comes from two places: the ZenWave SDK, run from `zenwave-scripts.zw` when the domain model changes, and Maven plugins that run on every build for the API contracts.

### From the domain model: zenwave-scripts.zw

The domain models and the REST API of this modulith are not in this repository. They live in `../arcadia-editions-modulith-api` (one ZDL per bounded context, plus `orders-checkout-openapi.yml`), and the business flow (ZFL) is in `../arcadia-editions-architecture/business-flows/place-order-flow.zfl`. The models reach this project packaged as the Maven artifact `com.arcadiaeditions.modulith.application.arcadia-editions-modulith:service-all:0.0.1`: `zenwave-scripts.zw` imports it with `@import`, and `pom.xml` uses it both as a dependency and as the source of the OpenAPI spec.

To regenerate the code after a model change, follow this sequence:

1. **Package the models** into the local Maven repository (`~/.m2`), from the API repository:

   ```bash
   (cd ../arcadia-editions-modulith-api && ./package-local-service-all.sh)
   ```

2. **Run the eight plugin blocks of `zenwave-scripts.zw`, in file order.** Open the file in the IDE with the ZenWave plugin and press the play button next to each block, or run the equivalent command lines (hovering the button shows the exact one). `jbang zw` doesn't take a `.zw` file directly: each block is one command.

   | # | Plugin | Model | Generates |
   |---|---|---|---|
   | 1 | `BackendApplicationDefaultPlugin` | `catalog-inventory-domain-model.zdl` | the `catalog.inventory` module: domain, repositories, services and implementations, DTOs, mappers, events, listeners, in-memory test configs and tests |
   | 2 | `BackendApplicationDefaultPlugin` | `catalog-products-domain-model.zdl` | the `catalog.products` module |
   | 3 | `BackendApplicationDefaultPlugin` | `fulfillment-shipping-domain-model.zdl` | the `fulfillment.shipping` module |
   | 4 | `BackendApplicationDefaultPlugin` | `notifications-consumer-domain-model.zdl` | the `notifications.consumer` module |
   | 5 | `BackendApplicationDefaultPlugin` | `orders-checkout-domain-model.zdl` | the `orders.checkout` module |
   | 6 | `BackendApplicationDefaultPlugin` | `payments-processing-domain-model.zdl` | the `payments.processing` module |
   | 7 | `OpenAPIControllersPlugin` | `orders-checkout-domain-model.zdl` + `orders-checkout-openapi.yml` | the Spring MVC controller for the orders-checkout REST API |
   | 8 | `SpringWebTestClientPlugin` | `orders-checkout-domain-model.zdl` + `orders-checkout-openapi.yml` | the API tests for the orders-checkout REST API |

   The command the ZenWave extension builds for block 1 (all global `config` options of the script, then the block's own):

   ```bash
   jbang zw -p BackendApplicationDefaultPlugin \
     --deps io.zenwave360.sdk.plugins.customizations:kotlin-backend-application:2.7.1,com.arcadiaeditions.modulith.application.arcadia-editions-modulith:service-all:0.0.1 \
     basePackage=com.arcadiaeditions \
     layout.commonPackage="{{basePackage}}.common" \
     layout.entitiesCommonPackage="{{commonPackage}}.domain" \
     layout.domainEventsCommonPackage="{{commonPackage}}.domain.events" \
     layout.coreImplementationCommonPackage="{{commonPackage}}" \
     layout.coreImplementationMappersCommonPackage="{{commonPackage}}.mappers" \
     layout.infrastructureRepositoryCommonPackage="{{commonPackage}}" \
     layout.infrastructureEventsCommonPackage="{{commonPackage}}" \
     layout.adaptersWebCommonPackage="{{commonPackage}}" \
     layout.adaptersWebMappersCommonPackage="{{commonPackage}}.mappers" \
     layout.adaptersCommandsCommonPackage="{{commonPackage}}.commands" \
     layout.adaptersCommandsMappersCommonPackage="{{commonPackage}}.commands.mappers" \
     layout.adaptersEventsCommonPackage="{{commonPackage}}.events" \
     layout.adaptersEventsMappersCommonPackage="{{commonPackage}}.events.mappers" \
     useJSpecify=true useJMolecules=true useSpringModulith=true implementEventListeners=true includeEmitEventsImplementation=true \
     templates="new io.zenwave360.sdk.plugins.kotlin.BackendApplicationKotlinTemplates()" \
     zdlFile=classpath:/com/arcadiaeditions/modulith/application/arcadia-editions-modulith/catalog-inventory-domain-model.zdl \
     --force
   ```

   Blocks 2 to 6 only change the `zdlFile` (the model in the table above). Blocks 7 and 8 use `-p OpenAPIControllersPlugin` or `-p SpringWebTestClientPlugin` (with `templates="new io.zenwave360.sdk.plugins.kotlin.OpenAPIControllersKotlinTemplates()"` or `templates="new io.zenwave360.sdk.plugins.kotlin.SpringWebTestClientKotlinTemplates()"` instead of the backend templates) and replace `zdlFile` with `zdlFile=classpath:/com/arcadiaeditions/modulith/application/arcadia-editions-modulith/orders-checkout-domain-model.zdl openapiFile=classpath:/com/arcadiaeditions/modulith/application/arcadia-editions-modulith/orders-checkout-openapi.yml openApiModelNameSuffix=DTO`.

3. **Compile and commit.** The Maven build generates the OpenAPI interfaces and DTOs from the same artifact (see below):

   ```bash
   mvn -DskipTests clean test-compile
   git add -A && git commit -m "feat: regenerate backend from the models"
   ```

**Regenerating safely.** Some files are *generated once* and then belong to you: service implementations, event listeners and their MapStruct mappers, tests, `src/test/resources/data*` and `DockerComposeInitializer`. **This script has `--force` in its `config`, so every block overwrites them.** Commit before regenerating, run `git diff` afterwards, and restore the hand-written files you want to keep (`git checkout -- <path>`). Remove the `--force` line from `zenwave-scripts.zw` if you want a run to leave them alone.

### At build time: OpenAPI and AsyncAPI (Maven)

These run in `generate-sources` on every `mvn compile`, from the API contracts packaged in `service-all`. Their output goes to `target/` and is never edited by hand.

**OpenAPI** (`openapi-generator-maven-plugin`, generator `kotlin-spring`):
* reads `orders-checkout-openapi.yml` from the `service-all` artifact: `inputSpec` in `pom.xml` is a classpath resource, and `service-all` is a dependency of the plugin. The `src/main/resources/public/apis/openapi.yml` file is the archetype's sample and the build doesn't read it;
* generates the Spring MVC API interfaces and the `*DTO` models into `target/generated-sources/openapi` (`interfaceOnly`: the controllers from `OpenAPIControllersPlugin` implement them);
* packages are set by the `openApiApiPackage` / `openApiModelPackage` properties in `pom.xml`.

**AsyncAPI** (`zenwave-sdk-maven-plugin`, generator `AsyncAPIGenerator`, profile `asyncapi-code-generation`):
* the profile **activates only when `src/main/resources/public/apis/asyncapi.yml` exists**. Without the file, nothing is generated. This project has no `asyncapi.yml`: events between modules are in-process (Spring Modulith), so the profile is inactive;
* generates, as the API `provider`, the message DTOs and the Spring Cloud Stream producers and consumer interfaces into `target/generated-sources/zenwave`;
* with Spring Modulith, it also wires the transactional outbox (`transactionalOutbox=modulith`) and the application-event listener;
* packages are set by the `asyncApiModelPackage`, `asyncApiProducerApiPackage` and `asyncApiConsumerApiPackage` properties. See the [AsyncAPI generator docs](https://www.zenwave360.io/zenwave-sdk/plugins/asyncapi-generator/).

To serve more API files in SwaggerUI, add them to `application.yml` (`springdoc.swagger-ui.urls`, URLs relative to `src/main/resources/public`).

## Spec-Driven Development with zenwave-sdd

The generator creates the structure: entities, services, events, mappers and test skeletons. It leaves the behavior as `TODO`s. The [zenwave-sdd](https://github.com/ZenWave360/zenwave-sdd) skills find and fill those gaps, and [GitHub Spec Kit](https://github.com/github/spec-kit) gives the work a standard shape (`spec.md` → `plan.md` → `tasks.md`). The full workflow is in the [zenwave-sdd manual](https://github.com/ZenWave360/zenwave-sdd/blob/main/MANUAL.md).

The rule they all follow: **models own structure.** If a behavior needs a field, event, command or state that the ZDL doesn't have, fix the ZDL and regenerate. Never hand-add it to code.

### Install the skills and Spec Kit

Run once, from the project root, after the first generation. It installs a pinned zenwave-sdd release for Claude Code, Codex and Grok:

```bash
ZWSDD=v0.1.0-rc1                                     # zenwave-sdd release tag
tmp=$(mktemp -d)
git clone -q --depth 1 --branch "$ZWSDD" https://github.com/ZenWave360/zenwave-sdd "$tmp"

# the same skills into each agent's folder (copies; symlinks are unreliable on Windows)
for dir in .claude/skills .agents/skills .grok/skills; do
  mkdir -p "$dir" && cp -r "$tmp"/skills/* "$dir"/
done

mkdir -p docs
cp "$tmp"/templates/project-overlay.md docs/zenwave-sdd-overlay.md
cp "$tmp"/speckit/constitution-zenwave.md docs/
echo "$ZWSDD" > docs/zenwave-sdd.version
rm -rf "$tmp"

# Spec Kit, for every agent you use (claude is the default)
uv tool install specify-cli --from git+https://github.com/github/spec-kit.git
specify init --here --integration claude --script sh
specify integration install codex
specify integration install grok
specify integration use claude
```

Then:

1. **Fill in `docs/zenwave-sdd-overlay.md`.** It's how the skills find your ZFL and ZDL files, and learn the deployment shape (microservices or modulith), the packages, and the build and regenerate commands.
2. **Point every agent at it.** Create `AGENTS.md` (read by Codex and Grok) and a `CLAUDE.md` that imports it:

   ```bash
   cat > AGENTS.md <<'EOF'
   # Agent instructions
   - Before running any zenwave-* skill, read docs/zenwave-sdd-overlay.md.
   - Models own structure: never hand-edit generated structure; model gaps go back to the ZDL + regeneration (docs/constitution-zenwave.md).
   - Spec Kit features live in specs/NNN-<feature>/; the constitution is .specify/memory/constitution.md.
   EOF
   printf '@AGENTS.md\n' > CLAUDE.md
   ```

3. **Commit** the skill folders, `docs/`, `.specify/`, `AGENTS.md` and `CLAUDE.md`, so the whole team works with the same versions.

To upgrade, re-run the first block with a new `ZWSDD` tag, and review the `git diff`.

### Run the skills

Claude Code and Grok invoke skills as `/skill-name`, and Codex as `$skill-name`. Any agent also understands "run the zenwave-gap-report skill".

| Step | Run | Result |
|---|---|---|
| ZFL flow → ZDL (only when you start from a ZFL business flow) | `/zfl-to-zdl <flow.zfl>` | one ZDL per bounded context |
| Find what the generator left out | `/zenwave-gap-report` | `docs/zenwave-gap-report.md`: model gaps, missing wiring, unimplemented methods, mapper and test gaps, open questions |
| Fix model gaps | edit the ZDL, regenerate ([Code Generation](#code-generation)), re-run `/zenwave-gap-report` | no model gaps left |
| Generate test data (JPA) | `/zenwave-test-data` | `src/test/resources/data.sql` + `data/jpa/<Entity>/*.json` |
| Spec a flow | `/speckit-constitution` (once), then `/speckit-specify`, `/speckit-clarify`, `/speckit-plan`, `/speckit-tasks` | `specs/NNN-<flow>/` with tasks seeded from the gap report |
| Implement | `/speckit-implement Use the zenwave-fill-gaps skill for each task.`, or `/zenwave-fill-gaps <finding-id>` | code and tests |
| Check drift and close | `/speckit-converge`, `mvn test`, `/zenwave-gap-report` | no blockers or majors left |

To export only the Hibernate DDL (no project configuration needed):

```bash
bash .claude/skills/zenwave-test-data/scripts/export-ddl.sh    # -> target/ddl/schema.sql
```

The prompts for each Spec Kit step, and the reasoning behind them, are in the [zenwave-sdd manual](https://github.com/ZenWave360/zenwave-sdd/blob/main/MANUAL.md).

Happy Coding!! 🚀🚀🚀
