# Design decisions

Numbered log of the non-obvious calls made during the build. Referenced from the code and
the other docs.

### DD-1 — Spring Boot, PostgreSQL, JWT (the three chosen forks)
Spring Boot 3 (Web, Security, Data JPA, Validation, Actuator) for a portfolio-standard
layered REST service; PostgreSQL as the production database with H2 in PostgreSQL mode for
tests; stateless JWT access tokens, no refresh tokens. These were confirmed with the
requester before implementation.

### DD-2 — Some invariants are service-enforced, not DB constraints
"`event_date` not in the future" cannot be a `CHECK` — PostgreSQL rejects non-immutable
functions (`now()`) in a check constraint, and the migration must also run on H2. "One
unresolved flag per user per listing" would need a partial unique index, which H2 lacks.
Both live in the service layer, which is the enforcement point for every other business
rule anyway, and are covered by tests.

### DD-3 — `Category` is an enum + CHECK, not a lookup table
The taxonomy is ~23 values that change rarely. Compile-time safety and simpler mapping win
over the flexibility of a table; adding a category is a one-line enum change plus a
`V2` migration to widen the CHECK. Revisit if categories ever need per-item counts,
deactivation, or localization.

### DD-4 — `privateDetails` is a first-class, access-controlled field
The identifying detail a finder withholds (a scratch, an engraving, what was in the
pocket) is exactly what proves ownership, so it must not appear in the public listing.
It is a dedicated column returned only to the reporter and moderators, read by the
matching engine server-side, and never echoed — not even inside a match reason. Structured
`listing_attributes` are the separate, public, matchable surface.

### DD-5 — A claim workflow, not an "algorithm says so" handover
`match_candidates` only ever reach `SUGGESTED`; a human `confirm`s. Recovery is gated
separately: a claimant submits a `claim` describing the withheld details, the finder or a
moderator approves it, and only an `APPROVED` claim lets a non-moderator move the listing
to `RECOVERED` (a moderator can override). Multiple people may claim the same found item;
approving one does not auto-reject the others.

### DD-6 — Listing status transitions live on the enum
`ListingStatus.canTransitionTo` is the single source of truth, unit-tested without Spring.
`RECOVERED` and `REMOVED` are terminal; `CLOSED` is reversible to `OPEN`; `REMOVED`
requires a moderator. There is no `DELETE` that hides a listing behind a fake 204 — a user
"closes", a moderator "removes".

### DD-7 — `confirm` is reversible; `reject` is sticky
Confirming a match promotes both listings `OPEN → MATCHED`. `unconfirm` reverts, and if no
other confirmed candidate involves a listing it drops back to `OPEN` — so a mistaken
confirmation does not permanently strand other potential owners of a look-alike item.
A `REJECTED` pair is never re-created by a rescan.

### DD-8 — Timestamps via Spring Data auditing, not DB triggers
`@CreatedDate` / `@LastModifiedDate` on a mapped superclass. Portable across PostgreSQL
and H2 with no trigger dialect differences. The tradeoff: raw SQL that bypasses the
persistence context will not touch `updated_at`. Such writes are declared out of contract.

### DD-9 — Matching runs in-request, behind a SQL pre-filter + top-K cap
`POST /listings` scores synchronously. To keep it bounded, `findMatchPrefilter` restricts
candidates to the opposite kind, active status, and (same category OR same building OR
date within ±`prefilter-days`); only the top `max-candidates-per-listing` by score are
persisted. A production system would hand this to a queue; the structure (a single
orchestration method, all knobs in `MatchingProperties`) makes that a small change.

### DD-10 — `match_reasons` is a child table; `score = Σ contribution`
Rejected a `jsonb` column: H2 in PostgreSQL mode does not add `jsonb`, and it would be the
one part of the schema the test database could not exercise. A child table is queryable
and portable. Every signal's `contribution` is `round(weight × sub × 100)` and the score
is their sum, so a reader can always reconstruct the number — which the "explain the
match" requirement demands. Match visibility is authenticated and scoped to the involved
reporters + moderators because the reasons disclose the counterpart's building/date.

### DD-11 — Rate limiting and login lockout are in-process
`RateLimiter` and `LoginThrottle` are fixed-window counters in bounded LRU maps. Correct
and dependency-free for a single node; they reset on restart and would under-count across
a horizontally-scaled fleet. A shared store (Redis) is the documented production upgrade.
The LRU bound stops an attacker cycling identifiers from growing the map.

### DD-12 — User deletion anonymizes; admin bootstrap is a runner
`DELETE /users/me` replaces email/name with tombstones, randomizes the password,
invalidates tokens, and closes still-open listings — it does not cascade a row delete, so
other users' message and claim threads stay intact and `reporter_id` FKs stay valid.
The first admin is created by an idempotent `ApplicationRunner` reading env vars, not by a
Flyway migration (migrations are checksummed and must not carry per-environment secrets).

### DD-13 — Coverage profile targets JDK 21–25
As of 2026-09 no released JaCoCo instruments Java 26 (class-file major 70) bytecode.
`./mvnw -Pcoverage verify` (JaCoCo, 80% line gate on `matching` + `service`) must run on a
JDK in the 21–25 range; the code targets Java 21 regardless. The default `./mvnw verify`
uses no JaCoCo and stays clean on the bundled Oracle JDK 26.

### DD-14 — `passwordChangedAt` cutoff in milliseconds
The JWT `pca` claim and the filter comparison use epoch **milliseconds**. With
second-granularity, a logout-all or role change occurring in the same wall-clock second as
a token's issuance would fail to invalidate that token.

### DD-15 — Explicit `@Query` over long derived method names
Repository lookups that traverse an association (`listing.reporter.id`, …) use JPQL
`@Query` with named parameters. Clearer at a glance, and it avoids a Spring Data property-
path resolver clash with convenience getters such as `Listing.getReporterId()`.

### DD-16 — `open-in-view=false`, explicit fetch graphs, and a test profile that proves it
The Open Session In View anti-pattern is disabled. Every read path that maps an entity to
a response DTO after its transaction closes uses a dedicated repository finder that
`join fetch`es exactly the associations the mapper touches (`Listing` → reporter +
attributes; `MatchCandidate` → both listings + reasons; `ContactMessage` → listing +
sender + recipient; `Claim` → claimant; `Flag` → listing). Identifier getters on a
detached proxy are safe, so associations only id-accessed are left lazy. Tests activate
`@ActiveProfiles("test")` and keep the production `application.yml` in effect
(`application-test.yml` only overrides the datasource, secret, and rate-limit toggle), so
a missing fetch surfaces as a failing test instead of only a production 500. An earlier
version of this project shipped with the test config accidentally shadowing the main one
and re-enabling OSIV, which hid this class of bug — hence the profile split.

### DD-17 — Spring MVC framework exceptions get explicit 4xx handlers
`GlobalExceptionHandler` has a catch-all `@ExceptionHandler(Exception.class)` that returns
500. Without explicit handlers it would also swallow `NoResourceFoundException` (unknown
route), `HttpRequestMethodNotSupportedException` (wrong verb), and
`HttpMediaTypeNotSupportedException` and report them as 500 with a logged stack trace.
Each now maps to its correct status (404 / 405 / 415) as `problem+json`.
