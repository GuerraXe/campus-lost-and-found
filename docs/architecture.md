# Architecture

## Layers and the dependency rule

```
web ─▶ service ─▶ repo ─▶ domain
        │
        ▼
     matching        (pure; depends only on domain + MatchingProperties)

security, config     cross-cutting; wired into web via the Spring context
```

Compile-time dependencies only ever point downward. `web` never touches a repository
directly; `service` never imports anything from `web` except the `AppPrincipal` record and
the `Exceptions` types (both intentionally in shared-ish packages). `domain` imports
nothing from the project.

### web — `com.campuslostfound.web`

* `api/` — one controller per resource group. Controllers do three things: bind and
  validate the request DTO, apply a coarse role gate (`@PreAuthorize` or a null-principal
  check), and translate between DTOs and the service. They contain no branching business
  logic.
* `dto/` — request and response **records**, grouped by area (`AuthDtos`, `ListingDtos`,
  …). Request records carry Jakarta validation annotations. They are never JPA entities,
  so there is no mass-assignment surface: a `PatchRequest` simply has no `status` or
  `reporterId` component to populate.
* `Mappers` — static `domain → response DTO` functions. The only place that decides
  whether a caller is "privileged" enough to see `privateDetails` / the reporter.
* `Pageables` — turns raw `page` / `size` / `sort` params into a `Pageable` with a hard
  size cap and a per-endpoint allow-list of sortable fields.
* `SecurityHeadersFilter`, `RequestLoggingFilter` — ordered servlet filters. The logging
  filter emits one structured line per request and is explicitly forbidden from touching
  headers or bodies.
* `error/` — `GlobalExceptionHandler` (`@RestControllerAdvice`) plus `ProblemJsonHandlers`
  for the 401/403 that never reach a controller. Both emit the same RFC 7807 shape.

### service — `com.campuslostfound.service`

One class per resource group plus shared helpers:

| Class | Owns |
|---|---|
| `AuthService` | registration, verification, login, logout-all |
| `AccountService` | self-delete (anonymize), admin role change |
| `ListingService` | create, read-with-visibility, search spec, patch, status transitions, attributes |
| `MatchingService` | pre-filter, scoring orchestration, persistence, confirm/reject/unconfirm |
| `ClaimService` | submit, list, decide, withdraw |
| `MessageService` | send, inbox/sent, read state |
| `FlagService` | submit, queue, resolve |
| `AccessGuard` | `requireUser`, `requireVerified`, `requireOwnerOrModerator`, `requireModerator` |
| `support/RateLimiter` | in-process fixed-window limiter, bounded LRU |
| `support/LoginThrottle` | per-account failed-login lockout |
| `support/Tokens` | secure random + SHA-256 hashing for verification tokens |

Services are `@Transactional`. Reads are `@Transactional(readOnly = true)`. Entities loaded
inside a service method are dirty-checked on commit, so most "update" paths have no
explicit `save`.

### repo — `com.campuslostfound.repo`

Spring Data JPA interfaces. Non-trivial lookups use explicit JPQL `@Query` with named
parameters rather than long derived method names — clearer, and it avoids a property-path
clash where a derived query like `findByReporterId` would try to resolve a `reporterId`
attribute that only exists as a convenience getter.

`ListingRepository` also extends `JpaSpecificationExecutor` so the search endpoint can
compose optional filters as a `Specification` built in `ListingService`.

Because `spring.jpa.open-in-view` is **off**, every finder whose result is mapped to a
DTO after the transaction closes (`findDetailById`, `findForListing`, `findByIdWithGraph`,
`findInbox`/`findSent`/`findByIdWithRefs`, …) `join fetch`es exactly the associations the
mapper reads. See [design-decisions.md DD-16](design-decisions.md).

### domain — `com.campuslostfound.domain`

JPA entities (`AuditableEntity` mapped-superclass supplies `id` + audited timestamps).
State machines live on the enums: `ListingStatus.canTransitionTo(...)` is the single
source of truth for legal listing transitions, unit-tested independently of Spring.

`@Version` optimistic-locking columns on `User`, `Listing`, `MatchCandidate`, `Claim`,
`Flag` — the aggregates two actors can race on. A concurrent update surfaces as HTTP 409
`concurrent-update`.

### matching — `com.campuslostfound.matching`

`MatchEngine.score(Listing lost, Listing found)` → `MatchResult(score, reasons)`. Pure and
deterministic: all inputs are the two entities and the injected `MatchingProperties`. This
is what makes the algorithm cheap to unit-test exhaustively. `TextNormalizer` does the
tokenizing and holds the stop-word / common-word lists.

### security — `com.campuslostfound.security`

`JwtService` issues and verifies HS256 tokens. `JwtAuthenticationFilter` runs once per
request: if a valid Bearer token is present it loads the user, checks the account is not
deleted and the token's `passwordChangedAt` is not stale, and populates the
`SecurityContext` with role-derived authorities (`ADMIN` implies `MODERATOR` implies
`USER`). A missing or bad token simply leaves the request anonymous; the authorization
rules decide the response.

## Request lifecycle (a write)

```
client
  → SecurityHeadersFilter            add nosniff / DENY / CSP / no-store
  → RequestLoggingFilter             start timer
  → JwtAuthenticationFilter          Bearer -> SecurityContext (or anonymous)
  → Spring Security authorization    path rules + @PreAuthorize
  → Controller                       validate DTO, null-principal check
  → Service (@Transactional)         AccessGuard ownership check, business rules
  → Repository / entity dirty-check
  ← Mapper                           domain -> response DTO (privileged?)
  ← GlobalExceptionHandler           on any throw -> problem+json
  ← RequestLoggingFilter             one line: METHOD path -> status (ms) user=id
```

## Persistence portability

The single Flyway migration is written to run unchanged on PostgreSQL and on H2 in
PostgreSQL mode: `BIGINT GENERATED BY DEFAULT AS IDENTITY`, `CHECK ... IN (...)` lists
(immutable), explicit `ON DELETE` clauses, no partial indexes, no function indexes, and
no `now()` inside a `CHECK` (PostgreSQL rejects non-immutable functions there — "not in
the future" is enforced in `ListingService` instead). Hibernate runs with
`ddl-auto=validate`, so a mismatch between an entity and the migrated schema fails the
build via `ApplicationContextIT`.

## Known scaling edges (documented, not hidden)

* Matching runs inside the `POST /listings` transaction. The pre-filter + top-K cap keep
  it bounded, but a production deployment would move it to a queue. See
  [design-decisions.md DD-9](design-decisions.md).
* The rate limiter and login throttle are per-instance and reset on restart — correct for
  a single node, not for a horizontally-scaled fleet. A shared store (Redis) is the
  production answer. See [DD-11](design-decisions.md).
