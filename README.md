# Campus Lost &amp; Found

A centralized lost-and-found platform for a university campus. One place to report a
lost or found item, search and filter listings, receive **scored, explained** suggestions
of which lost report and which found report might be the same object, prove ownership
through a claim workflow, contact the other party without exchanging personal contact
details, and flag suspicious listings for moderators.

> **Status:** v1.0. Built through a SOLO-style process (inspect → requirements → domain →
> schema → API → matching → security → testing → incremental build). `./mvnw verify` is
> green on JDK 26 — **43 unit + 43 integration tests**. One framework: Spring Boot.

---

## The problem it solves

On a real campus, lost-item reports are scattered across group chats, Instagram and
Discord servers, department mailing lists, and three or four physical front desks. A
person who lost a backpack has to broadcast to all of them; a person who *found* one has
nowhere canonical to post it. Items and owners fail to reunite **even when both reports
already exist somewhere.**

This service is the single place both sides post, and it does the cross-referencing work
that a human scanning six channels cannot: for every new report it scores every plausible
counterpart and explains *why* each one might match.

## What it deliberately does not do

Photo upload / image matching, email or SMS notifications, real-time chat, a mobile app,
campus SSO, GPS coordinates, and any "reward" or payment flow are all out of scope for
v1.0. Correctness and safety of the core loop came first.

---

## Architecture

A conventional layered Spring backend. Dependencies point one way only:

```
web  (controllers, DTOs, filters, RFC 7807 error handling)
 │      transport + coarse role gating only
 ▼
service  (all business rules: ownership, state machines, matching orchestration)
 │
 ▼
repo  (Spring Data JPA interfaces; @Query for anything non-trivial)
 │
 ▼
domain  (entities + enums; the state-transition tables live on the enums)

matching   pure, deterministic scoring engine — used by service, depends on nothing else
security   JWT filter, principal, access guard — cross-cutting
config     typed @ConfigurationProperties + startup validation
```

* **API / controller** — `com.campuslostfound.web.api`. Thin: parse, authorize by role,
  map to/from DTOs. No business logic.
* **Service** — `com.campuslostfound.service`. Every rule (who may edit a listing, which
  status transitions are legal, when a listing may be marked recovered, one unresolved
  flag per user, …) is enforced here and nowhere else, so a different transport could sit
  on top unchanged.
* **Repository** — `com.campuslostfound.repo`. Spring Data JPA. Derived-name queries are
  avoided in favour of explicit `@Query` for clarity and to sidestep property-path
  ambiguity with helper getters.
* **Domain / model** — `com.campuslostfound.domain`. Records-free JPA entities with
  behaviour (e.g. `ListingStatus.canTransitionTo`), `@Version` optimistic locking on the
  mutable aggregates, Spring Data auditing for timestamps.
* **Auth / authz** — `com.campuslostfound.security` + `SecurityConfig`. Stateless JWT;
  method-level `@PreAuthorize` for role gates; `AccessGuard` for ownership checks in
  services.
* **Matching engine** — `com.campuslostfound.matching`. See below.
* **Database** — PostgreSQL in production; H2 in PostgreSQL-compatibility mode runs the
  same Flyway migration and the same queries in tests.

Full detail: [docs/architecture.md](docs/architecture.md). Every non-obvious call is
logged in [docs/design-decisions.md](docs/design-decisions.md).

---

## Matching methodology

For a new listing, a cheap SQL **pre-filter** selects only active listings of the
opposite kind that share a category, or a building, or fall within a date window
(default ±21 days). Each survivor is scored by five independent signals, each producing a
sub-score in `[0, 1]` combined with fixed weights that **sum to 1.0**:

| Signal | Weight | How the sub-score is computed |
|---|---:|---|
| **Category** | 0.30 | exact enum match → 1.0; one side `OTHER` → 0.25; else 0 |
| **Keywords** | 0.30 | overlap coefficient of normalized tokens (`\|A∩B\| / min(\|A\|,\|B\|)`), tilted 70/30 toward the share of shared words that are *distinctive* (not generic lost-and-found vocabulary) |
| **Location** | 0.15 | same building → 1.0; same area only → 0.6; shared free-text location words → up to 0.5; else 0 |
| **Date** | 0.15 | linear decay to 0 at `date-decay-days` (default 14) apart; **halved** when the found date precedes the lost date — you cannot find something before it is lost |
| **Attributes** | 0.10 | fraction of shared structured attribute keys (colour, brand, …) whose values agree |

`contribution = round(weight × sub × 100)` and **`score = Σ contributions`**, so the
explanation always reconstructs the number. A candidate is stored only if it scores at or
above the threshold (default **45**), and only the top *K* (default 25) are kept per
listing.

Every response carries this text and the API never auto-links anything:

> *Suggested by the matching algorithm. This is not a confirmed match — a person must
> verify ownership before an item changes hands.*

Only an explicit `confirm` moves the two listings to `MATCHED`, and it can be reversed
with `unconfirm`. Known limitations (no stemming or synonyms, English-only tokenizing,
in-request scoring) and the weight-tuning rationale are in
[docs/matching.md](docs/matching.md).

---

## Security &amp; privacy

| Concern | Approach |
|---|---|
| **Passwords** | BCrypt, cost 12. Never stored, logged, or returned in any DTO. |
| **Authentication** | Stateless JWT (HS256). Secret from `CAMPUS_JWT_SECRET`; startup fails if it is shorter than 32 bytes. 30-minute tokens carry a millisecond `passwordChangedAt` claim — a password change **or an explicit logout-all** bumps that value and every earlier token stops verifying. |
| **Authorization** | Role gate (`USER` / `MODERATOR` / `ADMIN`) at the controller via `@PreAuthorize`; ownership re-checked in the service (`AccessGuard`). A role change invalidates the target's existing tokens. |
| **Ownership disclosure** | A found item's `privateDetails` (a scratch, an engraving, what was in the pocket) and the reporter's identity are returned **only** to the reporter and to moderators. Claimants prove ownership by describing those details through the claim workflow — the details are never echoed, not even inside a match explanation. |
| **Match visibility** | `GET /listings/{id}/matches` requires authentication and is scoped to the two involved reporters + moderators, because a match reveals a counterpart's building and date proximity. |
| **Input validation** | Jakarta Bean Validation on every request DTO; unknown JSON properties rejected; request bodies are separate records (no entity binding, no mass assignment). `@SafeText` **rejects** control and bidi-override characters — it never strips or rewrites, because hand-rolled sanitizing corrupts legitimate text (`size < 10cm`) and invites bypasses. Output encoding is the client's job; the API only ever emits JSON. |
| **Safe queries** | JPA / parameterized `@Query` only. Free-text search binds each term as a parameter. Sort fields are checked against an allow-list — `?sort=passwordHash` is a 400. |
| **Abuse resistance** | Email verification required before creating listings, messaging, or claiming. Per-account login lockout after 5 failures. In-process fixed-window rate limits on create / contact / claim / flag / rescan. Uniform `invalid email or password` (no user enumeration on login). |
| **Error responses** | RFC 7807 `application/problem+json` for every failure, including filter-chain 401/403. No stack traces, SQL, or internal messages reach the client; unexpected errors get a log reference id. |
| **Account deletion** | `DELETE /users/me` anonymizes in place (tombstone email/name, random password, tokens invalidated, open listings closed) rather than cascading a row delete, keeping other people's message threads intact. |
| **PII minimization** | Only an email (login + recovery, never shown to other users) and a chosen display name. No phone, address, student ID, DOB, or photo. |
| **Actuator** | Only `/actuator/health` is exposed and it hides component detail; everything else under `/actuator` is denied. |
| **CSRF** | Disabled deliberately — auth is a Bearer header, never an ambient cookie, so there is no CSRF surface. |

Full write-up: [docs/security.md](docs/security.md).

---

## Build &amp; run

Requires a **JDK 21+** (developed on the bundled Oracle JDK 26; language level is 21). The
Maven wrapper is committed.

```bash
./mvnw verify                 # compile + 43 unit + 43 integration tests (H2)
./mvnw -q spring-boot:run     # start on :8080 against the configured PostgreSQL
./mvnw -q package             # build target/campus-lost-and-found.jar
```

PostgreSQL connection and secrets come from the environment (defaults are dev-only):

```
CAMPUS_DB_URL         jdbc:postgresql://localhost:5432/campus_lost_found
CAMPUS_DB_USER        campus
CAMPUS_DB_PASSWORD    campus
CAMPUS_JWT_SECRET     (>= 32 bytes; required in production)
CAMPUS_BOOTSTRAP_ADMIN / CAMPUS_ADMIN_EMAIL / CAMPUS_ADMIN_PASSWORD   optional first admin
```

Flyway creates the schema on first start. With no mail server configured
(`campus.auth.expose-verification-token=true`, the default), `POST /api/v1/auth/register`
returns the email-verification token in its response so the flow is exercisable end to
end; wire a mailer and set that flag to `false` before any real deployment.

* **OpenAPI:** `http://localhost:8080/v3/api-docs` — **Swagger UI:** `/swagger-ui.html`
* **Example requests:** [docs/api-examples.http](docs/api-examples.http) (curl + IntelliJ/VS Code HTTP client)
* **Coverage:** `./mvnw -Pcoverage verify` (JaCoCo, 80% line gate on `matching` + `service`).
  Run it on a **JDK 21–25** — as of 2026-09 no released JaCoCo instruments Java 26 bytecode;
  the default build stays clean on 26 (see [DD-13](docs/design-decisions.md)).

---

## Project layout

```
pom.xml, mvnw, .mvn/                     Maven build (wrapper committed)
src/main/resources/db/migration/V1__…    the schema, one portable migration
src/main/java/com/campuslostfound/
  domain/      entities + enums (state machines on the enums)
  repo/        Spring Data JPA repositories
  service/     business rules, matching orchestration, rate limiter, throttle
  matching/    the scoring engine (pure, unit-tested)
  security/    JWT service + filter, principal
  config/      typed properties + validation, SecurityConfig, OpenAPI, admin bootstrap
  web/         controllers, DTOs, mappers, security-headers + logging filters, error handling
src/test/java/…   *Test = unit (Surefire), *IT = full-context API tests (Failsafe)
docs/            architecture, schema, matching, security, design-decisions, api-examples
```

## Licence

Provided as a portfolio project; no warranty.
