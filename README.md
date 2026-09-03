# spring-boot-template

Architecture template for `@vaullet-io` backend services.

Built on **Spring Boot 4.1.1** / **Spring Framework 7** / **Java 21 LTS**, with a working
three-layer REST slice (API → service → DAO) you can copy, and the reasoning for every choice
written next to the code rather than in a wiki nobody opens.

```
./mvnw spring-boot:run     # starts PostgreSQL via Compose, migrates, serves on :8080
./mvnw verify              # 18 unit/slice tests + 11 integration tests on real PostgreSQL
```

Then open <http://localhost:8080/swagger-ui.html>.

---

## Table of contents

1. [What is in here](#what-is-in-here)
2. [Running it](#running-it)
3. [Package structure and why](#package-structure-and-why)
4. [The layers](#the-layers)
5. [Modern practices this template encodes](#modern-practices-this-template-encodes)
6. [What changed in Spring Boot 4 (and what it breaks)](#what-changed-in-spring-boot-4-and-what-it-breaks)
7. [Testing strategy](#testing-strategy)
8. [Configuration and profiles](#configuration-and-profiles)
9. [Security posture](#security-posture)
10. [Observability](#observability)
11. [Deliberate omissions](#deliberate-omissions)
12. [Proposed structures for a fuller service](#proposed-structures-for-a-fuller-service)
13. [Using this as a template](#using-this-as-a-template)

---

## What is in here

| Concern | Choice | Where |
| --- | --- | --- |
| Framework | Spring Boot 4.1.1, Spring Framework 7.0.9 | `pom.xml` |
| Language | Java 21 LTS, records, virtual threads | `pom.xml`, `application.yaml` |
| Web | Spring MVC (`spring-boot-starter-webmvc`) | `customer/api` |
| API versioning | Native `@GetMapping(version = …)`, header-resolved | `CustomerController` |
| Errors | RFC 9457 `application/problem+json` | `common/error` |
| Persistence | Spring Data JPA, Hibernate 7, PostgreSQL 18 | `customer/dao` |
| Schema | Flyway, versioned migrations | `db/migration` |
| Security | OAuth2 resource server (JWT), method security | `config/SecurityConfig` |
| Outbound HTTP | Declarative `@HttpExchange` + `@ImportHttpServices` | `notification` |
| Resilience | Framework 7 `@Retryable`, `@ConcurrencyLimit` | `NotificationSender` |
| Docs | springdoc-openapi 3.1, OpenAPI 3.1 | `config/OpenApiConfig` |
| Observability | Actuator, Micrometer, Prometheus, Brave tracing, ECS JSON logs | `application*.yaml` |
| Testing | JUnit 6, AssertJ, Mockito, Testcontainers 2, ArchUnit | `src/test` |
| Build/ship | Maven, Buildpacks or Dockerfile, GitHub Actions | `pom.xml`, `Dockerfile`, `.github` |

Everything listed is wired up and covered by a passing test — nothing here is aspirational.

---

## Running it

Four ways, depending on what you want. **1** is the everyday one.

### 1. Maven — one command, nothing to set up

```bash
./mvnw spring-boot:run
```

*Needs:* JDK 21+ and a running Docker. Nothing else — no local PostgreSQL, no `docker compose up`.

`spring-boot:run` activates the `local` profile (configured on the plugin in `pom.xml`), and
`spring-boot-docker-compose` starts `compose.yaml`, waits for its health check, points the
datasource at it, and stops the container when you stop the app. Flyway migrates on startup.

Then:

| | |
| --- | --- |
| API | <http://localhost:8080/api/customers> |
| Swagger UI | <http://localhost:8080/swagger-ui.html> |
| OpenAPI JSON | <http://localhost:8080/v3/api-docs> |
| Health | <http://localhost:8080/actuator/health> |
| Metrics | <http://localhost:8080/actuator/prometheus> |

Under `local` no token is needed, so plain `curl` works:

```bash
curl -X POST localhost:8080/api/customers \
  -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.com","displayName":"Ada Lovelace"}'

curl localhost:8080/api/customers
curl -H 'X-API-Version: 1.1' localhost:8080/api/customers/<id>   # richer representation
```

### 2. From the IDE — Testcontainers instead of Compose

Run **`TestTemplateApplication`** (in `src/test/java`), not `TemplateApplication`. Same app, but the
database is a throwaway Testcontainers instance, and it sets the `local` profile for you. Nothing to
clean up afterwards.

Running `TemplateApplication` directly will fail — it gets no profile, and the production security
configuration refuses to start without `OAUTH2_ISSUER_URI`. That is deliberate; see
[Security posture](#security-posture). If your IDE lets you set a run-configuration profile, set
`local` and it behaves like option 1 minus the Compose integration.

### 3. Standalone jar

```bash
./mvnw clean package
docker compose up -d --wait

SPRING_PROFILES_ACTIVE=local \
DB_URL="jdbc:postgresql://localhost:$(docker compose port postgres 5432 | cut -d: -f2)/template" \
java -jar target/spring-boot-template-0.0.1-SNAPSHOT.jar
```

**`DB_URL` is not optional here.** Spring Boot deliberately excludes `spring-boot-docker-compose`
from the repackaged jar — it is a development-time convenience, not something you want a production
artefact doing — so a packaged app never starts Compose for you. Start the database yourself and
tell the app where it is.

Stop with `docker compose down` when finished.

### 4. No Docker at all — your own PostgreSQL

If you already run PostgreSQL locally (many machines do, on 5432), create the database once:

```sql
CREATE ROLE template LOGIN PASSWORD 'template';
CREATE DATABASE template OWNER template;
```

then point the app at it and turn the Compose integration off:

```bash
SPRING_PROFILES_ACTIVE=local \
SPRING_DOCKER_COMPOSE_ENABLED=false \
DB_URL=jdbc:postgresql://localhost:5432/template \
DB_USERNAME=template DB_PASSWORD=template \
./mvnw spring-boot:run
```

Flyway creates the schema on first start.

### A note on port 5432

`compose.yaml` publishes the container on host port **55432**, not 5432. Plenty of machines already
run a native PostgreSQL on the default port, and the failure mode is genuinely confusing: the
container starts fine, the app connects to the *other* database, and the error is
`role "template" does not exist` rather than anything about a port clash. Override with
`DB_HOST_PORT` if 55432 is taken too.

To connect a database tool or `psql`:

```bash
psql -h localhost -p 55432 -U template -d template   # password: template
```

### Tests

```bash
./mvnw test      # fast: unit + slice tests only        (~5s, no Docker needed)
./mvnw verify    # adds *IT tests on real PostgreSQL     (~25s, needs Docker)
```

### Container image

```bash
./mvnw spring-boot:build-image   # Cloud Native Buildpacks — no Dockerfile to maintain
docker build -t template .       # or the checked-in multi-stage Dockerfile
```

### If it will not start

| Symptom | Cause |
| --- | --- |
| `role "template" does not exist` | Connected to a different PostgreSQL — see the port note above. |
| `Parameter 0 … required a bean of type 'JwtDecoder'` | Running without the `local` profile and without `OAUTH2_ISSUER_URI`. Working as designed. |
| `Cannot connect to the Docker daemon` | Docker is not running; options 1, 2 and 3 all need it. |
| Port 8080 in use | `SERVER_PORT=8081 ./mvnw spring-boot:run` |

---

## Package structure and why

```
io.vaullet.template
├── TemplateApplication.java          entry point: capabilities only, no beans
├── package-info.java                 @NullMarked — JSpecify null-safety for the whole tree
│
├── config/                           cross-cutting wiring, one class per concern
│   ├── ApplicationProperties.java    validated @ConfigurationProperties record
│   ├── SecurityConfig.java           JWT resource server (all profiles but `local`)
│   ├── LocalSecurityConfig.java      permissive chain for developer machines
│   ├── MethodSecurityConfig.java     @PreAuthorize, on in every profile
│   ├── PersistenceConfig.java        JPA auditing, auditor from the security context
│   ├── WebMvcConfig.java             CORS
│   └── OpenApiConfig.java            OpenAPI metadata + bearer scheme
│
├── common/                           shared building blocks, no feature knowledge
│   ├── error/                        ErrorType catalogue, exceptions, @RestControllerAdvice
│   ├── web/PageResponse.java         stable pagination envelope
│   └── persistence/AuditableEntity   audit columns + @Version
│
├── customer/                         ← a FEATURE SLICE. Copy this shape.
│   ├── CustomerStatus.java           vocabulary shared by all three layers
│   ├── api/                          controller + request/response DTOs
│   ├── service/                      business rules, transactions, domain model
│   └── dao/                          JPA entity + Spring Data repository
│
└── notification/                     an outbound integration, self-contained
```

**Package by feature, layered inside.** The alternative — top-level `controllers/`, `services/`,
`repositories/` — puts the files that change together in three different places, so every feature
touches every package and no package can be extracted, owned or deleted independently. With slices,
"remove the customer feature" is `rm -r customer/`, and the day one slice becomes its own service,
the boundary already exists.

The layer names `api` / `service` / `dao` are used verbatim because that is the vocabulary the team
already has, and because `architecture/LayeringTest` enforces them.

**The layering is a build failure, not a convention.** `LayeringTest` (ArchUnit) fails the build if
the API reaches into the DAO, if a controller injects a repository, if a JPA entity appears in a
web payload, or if anyone uses field injection. Documented-only architecture decays; the first PR
that skips a layer "just this once" gets approved by someone in a hurry.

---

## The layers

### `api` — translation only

Bind, validate, call one service method, shape the response. No business logic, no transactions, no
`try`/`catch` — errors propagate to the single `@RestControllerAdvice`.

- Plural noun resource paths (`/api/customers/{id}`); the verb is the HTTP method.
- `201` + `Location` on create, `204` on delete.
- `PATCH` with a nullable-field request record, so an omitted field means "unchanged" rather than
  "blank it".
- Every list endpoint is paged. An unbounded collection endpoint is an outage waiting for the table
  to grow.
- Separate request and response records. A caller cannot mass-assign `id` or `status` because the
  request type has no such component.

### `service` — the only layer that owns behaviour

- Constructor injection, `final` fields. The unit test is `new CustomerService(mock, mock)`.
- `@Transactional(readOnly = true)` at class level, overridden on writers: Hibernate skips
  dirty-checking and the driver can route to a replica.
- The transaction boundary lives *here* — not on the controller (which would hold a connection
  while serialising) and not on the repository (which could not compose two writes atomically).
- Side effects fire **after commit** (`TransactionSynchronization#afterCommit`), so a rolled-back
  transaction cannot emit an event claiming a customer exists, and a network call never pins a
  database connection.
- Returns a domain record (`Customer`), not the entity. See the note in `Customer.java` about when
  collapsing that into the response DTO is the right call.

### `dao` — data access

A Spring Data interface *is* the DAO; a hand-written `CustomerDaoImpl` wrapping an `EntityManager`
adds indirection over code that no longer needs writing, and the interface still gives you the seam
to mock. `CustomerRepository` documents when to use a derived query, when to switch to `@Query`,
and when to reach for `Specification` or projections.

Entities never leave this package — enforced by ArchUnit.

---

## Modern practices this template encodes

**Records everywhere data is data.** DTOs, configuration, the domain model. Immutable, no
boilerplate, and no Lombok — see [Deliberate omissions](#deliberate-omissions).

**JSpecify null-safety.** `@NullMarked` on the root package makes every reference non-null unless
marked `@Nullable`. Spring Framework 7 annotates its own API the same way, so an IDE (or NullAway
in CI) can flag a possible NPE at compile time. Kotlin callers get real nullability.

**Virtual threads on.** `spring.threads.virtual.enabled=true`. Blocking JDBC and HTTP calls no
longer occupy a platform thread, which is how plain Spring MVC now scales like a reactive stack for
I/O-bound work without WebFlux's debugging cost. Watch for `synchronized` around blocking calls in
older libraries — that pins the carrier thread.

**RFC 9457 problem details, with a type catalogue.** `ErrorType` enumerates every machine-readable
`type` URI the service can return. Clients branch on the URI, not on prose. Every response carries
the `traceId`, which is what turns a screenshot of an error into a log query. Unexpected exceptions
are logged with a stack trace and answered with a generic body — echoing `ex.getMessage()` is how
table names reach a bug tracker.

**Optimistic locking by default.** A `@Version` column on every entity turns a lost update into a
409 instead of a silent overwrite. Boxed `Long`, deliberately: Spring Data uses "version is null"
to decide an entity is new, which lets the application assign UUID keys up front and still get a
plain `INSERT` instead of a `SELECT`-then-merge.

**Open Session In View off.** OSIV keeps a connection checked out for the whole request so lazy
associations resolve during serialisation. It hides N+1 queries and couples connection lifetime to
response time. Load what you need in the service.

**Flyway owns the schema; Hibernate validates it.** `ddl-auto: validate`. Anything looser means the
production schema is whatever Hibernate inferred that day. Migrations are immutable once merged —
the fix for a mistake is always `V<n+1>`.

**Constraints in the database, not only in the service.** `existsByEmailIgnoreCase` is a courtesy
that returns a nice 409; the unique functional index is what actually prevents two concurrent
requests from both passing that check. There is a test for it.

**Declarative HTTP clients.** An `@HttpExchange` interface plus `@ImportHttpServices`, with base
URL and timeouts in YAML under `spring.http.serviceclient.<group>`. No OpenFeign (and no Spring
Cloud release train), no `RestTemplate` wrapper per integration. **Always set a read timeout** —
the default in most stacks is "forever", and a downstream that accepts a connection then hangs will
exhaust your threads long before it returns an error.

**Retries that do not make outages worse.** `@Retryable` with exponential backoff *and jitter*,
scoped to transport-level exceptions only. Without jitter every instance that failed together
retries together, and the retry storm keeps the struggling downstream down. Retrying a `400` just
wastes time and can duplicate side effects.

**Proxy-aware bean design.** `NotificationSender` is a separate bean from `NotificationService`
specifically because `@Retryable`, `@Transactional` and `@Cacheable` are proxy-based: a call from
one method of a bean to another method of the *same* bean bypasses the proxy and the annotation
silently does nothing. Splitting the beans is the fix that does not rely on anyone remembering.

**Explicit Actuator exposure.** `include: health,info,metrics,prometheus`, never `*`. `env` and
`configprops` disclose configuration.

**Structured logging natively.** `logging.structured.format.console: ecs` in `prod`. Boot ships
this, so log shipping needs neither `logstash-logback-encoder` nor a hand-written
`logback-spring.xml`.

---

## What changed in Spring Boot 4 (and what it breaks)

If you are porting a Boot 3 service, these are the ones that bite:

| Boot 3 | Boot 4 | Notes |
| --- | --- | --- |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` | The monolithic autoconfigure jar was split into focused modules; you now depend on what you use. |
| `spring-boot-starter-test` | per-module `-test` starters | `spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-security-test`, … |
| `@MockBean` | `@MockitoBean` | Deprecated in 3.4, **removed** in 4. |
| `org.testcontainers:postgresql` | `org.testcontainers:testcontainers-postgresql` | Testcontainers 2.0; `PostgreSQLContainer` moved to `org.testcontainers.postgresql`. |
| Jackson 2 | Jackson 3 (`tools.jackson`) | Jackson 2 is deprecated; annotations stay in `com.fasterxml.jackson.annotation`. |
| Undertow | — | Removed. Servlet 6.1 baseline; Tomcat 11 or Jetty 12.1. |
| `/api/v1/...` path duplication | `@GetMapping(version = "1.1")` | Native API versioning; strategy configured via `spring.mvc.apiversion.*`. |
| Spring Retry / Resilience4j | `@Retryable`, `@ConcurrencyLimit` | Now in `spring-context` under `org.springframework.resilience`. |
| OpenFeign | `@HttpExchange` + `@ImportHttpServices` | Note: the `@HttpServiceClient` annotation from the Boot 4 milestones was **removed before GA** — configuration is consumer-driven, and a service exposing and consuming the same interface would otherwise proxy itself at startup. |
| `WebSecurityConfigurerAdapter`, non-lambda DSL | `SecurityFilterChain` bean, lambda DSL | Gone in Security 6/7 respectively. |

Two gotchas this template hit while being built, both preserved as comments in the code:

- **Hikari durations are plain milliseconds.** `spring.datasource.hikari.connection-timeout: 3s`
  fails at startup with a `NumberFormatException` — those properties bind straight onto
  `HikariConfig` and never see Spring's duration converter.
- **PostgreSQL 18 images moved the data directory.** Mounting `/var/lib/postgresql/data` now stops
  the container from starting; mount `/var/lib/postgresql`.

---

## Testing strategy

Four levels, each testing what is cheapest to test at that level:

| Level | Example | Context | Speed |
| --- | --- | --- | --- |
| Unit | `CustomerServiceTest` | none — Mockito | ~80ms for 4 tests |
| Web slice | `CustomerControllerTest` | `@WebMvcTest` + real `SecurityConfig` | ~1.7s for 7 tests |
| Persistence slice | `CustomerRepositoryIT` | `@DataJpaTest` + Testcontainers | ~4s for 6 tests |
| Full stack | `CustomerApiIT` | `@SpringBootTest` + Testcontainers | ~2.5s for 5 tests |
| Architecture | `LayeringTest` | ArchUnit, no Spring | ~0.7s for 7 rules |

Naming binds them to build phases: `*Test` runs under Surefire in `mvn test`, `*IT` under Failsafe
in `mvn verify`. Keeping `mvn test` fast is what makes it a thing people actually run.

Decisions worth copying:

- **Testcontainers, not H2.** Testing persistence against H2 tests H2. Dialect differences —
  functional indexes, `TIMESTAMPTZ`, upsert syntax, case-insensitive collation — are exactly where
  data bugs live. `@ServiceConnection` removes the `@DynamicPropertySource` boilerplate entirely.
- **Security is imported, not disabled.** A `@WebMvcTest` with `addFilters = false` verifies an
  application that will never be deployed. The interesting assertions here are the 401 and the 403.
- **One composed `@IntegrationTest` annotation.** Spring caches contexts by their configuration; a
  stray `@TestPropertySource` on one class silently doubles the number of contexts a build starts,
  which is the usual reason a suite is slow.
- **`@AutoConfigureTestDatabase(replace = NONE)`** on `@DataJpaTest`. Without it the slice swaps in
  an embedded database and quietly ignores your container.
- **`saveAndFlush`, not `entityManager.flush`**, when asserting on constraint violations — only the
  repository proxy applies Spring's persistence exception translation.

---

## Configuration and profiles

`application.yaml` is the base; `application-<profile>.yaml` overrides. No `if (env == …)` anywhere
in the code.

| Profile | Activated by | Posture |
| --- | --- | --- |
| *(none)* | `java -jar` | Secure by default. Requires `OAUTH2_ISSUER_URI`; refuses to start without it. |
| `local` | `./mvnw spring-boot:run` | Compose-managed database, permissive HTTP security, method security still on, debug SQL logging, all Actuator endpoints, 100% trace sampling. |
| `test` | test classes | Testcontainers, notifications disabled, 100% sampling. |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | ECS JSON logs, Compose off, tuned pools. |

Every environment-specific or secret value is an `${ENV_VAR}` reference. Defaults exist only where
the default is safe everywhere — `OAUTH2_ISSUER_URI` deliberately has none, because a service that
starts without token validation is worse than one that refuses to start.

`ApplicationProperties` is a validated record: a bad value stops the context instead of failing on
the first request that touches it, and `spring-boot-configuration-processor` turns it into IDE
autocompletion for `application.yaml`.

---

## Security posture

- **Deny by default** — the chain ends in `anyRequest().authenticated()`, so a new endpoint is
  protected the moment it is written. Opening a path is an explicit, reviewable act.
- **Stateless JWT resource server.** No sessions; CSRF disabled *because* there is no session to
  ride. On a cookie-authenticated app that same line would be a real vulnerability.
- **Authorities from the token** — `SCOPE_*` from the standard `scope` claim plus `ROLE_*` from a
  configurable roles claim (adjust `ROLES_CLAIM` for Keycloak's `realm_access.roles`, Auth0's
  namespaced claim, or Entra's `roles`).
- **URL rules *and* method rules.** They answer different questions: "may this request reach the
  app" versus "may this principal perform this operation". A service with only the former loses its
  authorisation the moment a second entry point — a listener, a job, GraphQL — is added.
- **Actuator beyond health/info requires `ROLE_OPERATOR`.**
- Audit columns take the principal from the security context, never from the request body.

---

## Observability

- `/actuator/health` with Kubernetes `liveness` and `readiness` probe groups; details only for an
  authenticated operator.
- `/actuator/prometheus` — ~200 metric families out of the box.
- Micrometer tracing (Brave), 10% sampled in production, `traceId`/`spanId` in every log line *and*
  in every error response body.
- ECS-structured JSON logging in `prod`.
- Hikari leak detection at 30s, which finds the long-running transaction before it exhausts the
  pool.

---

## Deliberate omissions

Things a reviewer might expect, left out on purpose:

- **Lombok.** Records cover the DTO and configuration cases, the entity exposes intent-revealing
  methods rather than generated setters, and an annotation processor that rewrites your AST is a
  permanent cost on IDE support, debugging and every JDK upgrade. If your team wants it, add it —
  but add it as a decision, not as reflex.
- **MapStruct.** Two mapping methods do not justify a code generator. Add it when a service has
  dozens of mappings over nested graphs. Never add a *runtime reflective* mapper (ModelMapper,
  Dozer): silent field-name matching turns a rename into a production bug instead of a compile
  error.
- **A `CustomerDaoImpl`.** See [`dao`](#dao--data-access).
- **WebFlux.** Virtual threads give the same I/O scalability for this shape of workload without the
  reactive debugging tax. Reactive earns its keep for streaming and backpressure, not for CRUD.
- **Spring Cloud.** Boot 4 covers declarative clients, retries and concurrency limits natively.
  Adopt Spring Cloud when you actually need config server, gateway or bus — it pins you to its own
  release train.

---

## Proposed structures for a fuller service

What a production `@vaullet-io` service is likely to grow, roughly in the order most teams need it.
None of this is in the template; it is the menu.

### 1. Idempotent writes

Any `POST` a client may retry (payments, orders) needs an `Idempotency-Key` header, a table of
`(key, request_hash, response)` and a filter that replays the stored response. Retries and
at-least-once delivery make this mandatory, not optional.

```
common/idempotency/{IdempotencyKeyFilter, IdempotencyRecord, IdempotencyRepository}
```

### 2. Transactional outbox

The template's notification is fire-and-forget on purpose. When a side effect *must* happen, write
the intent to an `outbox` table inside the same transaction and publish from a poller. This is the
only way to make a database write and a message publish atomic without distributed transactions.

```
common/outbox/{OutboxEvent, OutboxRepository, OutboxPublisher}
db/migration/V…__create_outbox.sql
```

### 3. Domain events

`ApplicationEventPublisher` plus `@TransactionalEventListener(phase = AFTER_COMMIT)` decouples
slices without a message broker, and is the natural precursor to the outbox.

```
customer/service/event/{CustomerCreated, CustomerStatusChanged}
```

### 4. Async and scheduled work

`@EnableAsync` with a virtual-thread executor, `@Scheduled` with **ShedLock** so a job does not run
once per instance. Long jobs belong in Spring Batch or JobRunr, not in a `@Scheduled` method that
blocks the scheduler.

```
config/{AsyncConfig, SchedulingConfig}
customer/job/CustomerCleanupJob
```

### 5. Messaging

Kafka (`spring-kafka`) or RabbitMQ (`spring-boot-starter-amqp`), with the consumer as a thin
adapter that calls the same service the controller calls — that is the payoff of keeping the
service free of HTTP types.

```
customer/messaging/{CustomerEventConsumer, CustomerEventProducer}
```

### 6. Caching

`@Cacheable` with Caffeine (single instance) or Redis (shared), TTLs per cache and metrics on hit
ratio. Remember the proxy rule: self-invocation bypasses the cache.

```
config/CacheConfig
```

### 7. Multi-module Maven

Once the service passes roughly 30 classes per slice, split into `app` / `domain` / `infrastructure`
modules so the compiler enforces what ArchUnit currently enforces. Or keep one module and add
**Spring Modulith** (`spring-boot-starter-modulith`), which verifies slice boundaries, documents
them and can publish the event model — a lighter step with most of the benefit.

### 8. API contract management

Publish the generated OpenAPI document as a build artefact and diff it in CI to fail a PR that
breaks the contract. Consider **Spring REST Docs** when you want documentation that cannot compile
unless the tests pass, or Pact for consumer-driven contracts across teams.

### 9. Feature flags

Unleash, LaunchDarkly, or a `feature_flags` table behind a `FeatureFlags` interface. Trunk-based
development needs a way to merge unfinished work safely.

### 10. Multi-tenancy

If services are ever shared across tenants, decide early: schema-per-tenant (Hibernate's
`MultiTenantConnectionProvider`) or a discriminator column with a Hibernate filter. Retrofitting
this is a rewrite.

### 11. Rate limiting and quotas

Bucket4j with Redis, or the ingress/gateway. Cheaper and more reliable at the edge; in-app only
when limits are per-tenant and business-defined.

### 12. Secrets management

Vault, AWS Secrets Manager or Kubernetes secrets via `spring-cloud-vault` or the CSI driver —
replacing `${DB_PASSWORD}` with a reference rather than an environment variable.

### 13. Data lifecycle

Soft deletes (`deleted_at` plus a Hibernate `@SQLRestriction`), retention jobs and PII redaction.
GDPR erasure requests are much easier when the schema anticipated them.

### 14. Supply-chain and quality gates in CI

An SBOM (`spring-boot:build-info` / CycloneDX), OWASP dependency-check or Trivy on the image,
JaCoCo coverage thresholds, and Spotless with `palantir-java-format` so formatting never appears in
a diff. Dependabot is already configured here.

### 15. Performance and failure testing

Gatling or k6 against a deployed environment, plus a chaos test that kills the database mid-request
and asserts the service returns 503 rather than hanging.

---

## Using this as a template

1. `git clone` and rename: `io.vaullet.template` → your package, `spring-boot-template` → your
   artifact, `errors.vaullet.io` → your error-type host.
2. Delete the `customer` and `notification` slices once your first real slice is written — they are
   worked examples, not a starting schema.
3. Keep: `common/`, `config/`, `architecture/LayeringTest`, the profile layout, the test support
   classes, `db/migration` (minus `V1`/`V2`), the CI workflows.
4. Set `OAUTH2_ISSUER_URI`, `DB_*`, `APP_ENVIRONMENT` and `CORS_ALLOWED_ORIGINS` in your deployment.
5. Update `ErrorType` with your service's failure modes before writing the second endpoint — it is
   much harder to retrofit a type catalogue once clients depend on the responses.

The comments in the code are the documentation. If you change a decision, change the comment that
explains it; a template whose rationale has gone stale is worse than no template.
