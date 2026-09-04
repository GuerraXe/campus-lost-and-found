# Security model

## Authentication

* **Passwords** hashed with BCrypt cost 12 (`PasswordEncoder` bean). Plaintext is never
  persisted, never logged, never present in a DTO.
* **Tokens** are stateless HS256 JWTs (`jjwt`). Claims: `sub` = user id, `role`, and
  `pca` = the user's `passwordChangedAt` in **epoch milliseconds** at issue time. TTL 30
  minutes (`campus.auth.jwt-ttl-seconds`).
* On every request `JwtAuthenticationFilter` verifies the signature and expiry, loads the
  user, and rejects the token if the account is deleted **or**
  `token.pca < user.passwordChangedAt`. Therefore:
  * a password change invalidates every earlier token;
  * `POST /auth/logout` ("logout everywhere") bumps `passwordChangedAt` with no password
    change — a client-side discard is not required for correctness;
  * an admin role change bumps it too, so a demoted user cannot keep acting on an old
    role-bearing token.
* Millisecond (not second) precision on `pca` matters: a logout-all happening in the same
  wall-clock second as the token's issuance must still invalidate it.
* The signing secret comes from `CAMPUS_JWT_SECRET`. `AppConfig` fails startup if it is
  absent or shorter than 32 bytes.
* **No refresh tokens** by choice — revocation is expiry + the `pca` cutoff. `/auth/logout`
  exists so its absence isn't mistaken for an oversight.

## Login abuse resistance

* Uniform failure: a wrong password and a non-existent account both return
  `401 { detail: "Invalid email or password." }` — no user enumeration.
* `LoginThrottle` locks an account for `login-lockout-minutes` (15) after
  `login-max-failures` (5) consecutive failures; a success clears the counter. In-process,
  bounded LRU.
* Registration with an existing email returns a generic `409` — a small enumeration
  surface accepted for UX; the sensitive path (login) stays uniform.

## Authorization

* Roles `USER ⊂ MODERATOR ⊂ ADMIN` (the filter grants implied authorities).
* Controller gates: `@PreAuthorize("hasRole('MODERATOR')")` on `/moderation/**`,
  `hasRole('ADMIN')` on `/admin/**`; a null-principal check on the rest.
* Ownership is re-checked in the service via `AccessGuard`
  (`requireOwnerOrModerator`, `requireModerator`, `requireVerified`). Controllers cannot
  be trusted alone; the service is the enforcement point.
* Path rules in `SecurityConfig`: public = register/login/verify, `GET` on
  `/listings/**` and `/categories`, the OpenAPI docs, `/actuator/health`. Everything else
  authenticated. `GET /listings/*/matches` is carved out as **authenticated** even though
  listing reads are public.

## Data exposure control

* **`privateDetails`** and the **reporter identity** on a listing are returned only when
  the caller is the reporter or a moderator (`Mappers.detail(listing, privileged, …)`).
  A `REMOVED` listing 404s for everyone else.
* **Match explanations** never contain a counterpart's `privateDetails`. Shared-keyword
  reasons list only the intersection, which is a subset of the viewer's own tokens.
* **Match visibility** (`GET /listings/{id}/matches`) is limited to the two involved
  reporters + moderators, because the reasons disclose the counterpart's building and
  date proximity. Residual risk: a small crafted listing can still score on keywords and
  category and thus see a real found listing's building/date. Mitigations: email
  verification before creating listings, per-user create rate limit, and the moderator
  view of a user's listing history. Accepted for v1; a stricter design would require the
  found reporter to opt in to revealing.
* **Ownership proof** flows through claims: the claimant *describes* the withheld details
  in `answer_text`; the finder or a moderator compares and approves. An `APPROVED` claim
  is the precondition for a non-moderator marking the listing `RECOVERED`.

## Input handling

* Jakarta Bean Validation on every request DTO (`@NotBlank`, `@Size`, `@Email`,
  `@NotNull` on enums). Jackson `fail-on-unknown-properties=true` — an unexpected field is
  a 400.
* Request DTOs are records with only the fields a client may set. No entity is ever bound
  to a request body, so there is no mass-assignment path to `status`, `reporterId`,
  `role`, or `createdAt`.
* `@SafeText` **rejects** ISO control characters (tab/newline/CR excepted) and Unicode
  bidi-override characters. It does **not** strip or rewrite anything: hand-rolled HTML
  stripping breaks legitimate text (`size < 10cm`) and is a bypass magnet. The API emits
  only JSON; correct output encoding is the client's responsibility. `nosniff` +
  `Content-Security-Policy: default-src 'none'` are set on every response so a browser
  cannot be talked into executing a response body.
* Free-text search binds each term as a JPQL parameter. Sort fields are validated against
  a per-endpoint allow-list (`?sort=passwordHash` → 400). Request body / header size caps
  are set in `application.yml`.

## Rate limiting

`RateLimiter` — in-process fixed-window counter keyed by `(bucket, userId|IP)` with a
bounded LRU map (`max-tracked-keys`, default 20000) so an attacker cycling identifiers
cannot grow it. Buckets: `CREATE_LISTING`, `CONTACT_MESSAGE`, `SUBMIT_CLAIM`,
`SUBMIT_FLAG`, `RESCAN`. A 429 carries `Retry-After`. **Per-instance and non-durable** —
correct for a single node; a shared store is the multi-node answer.

## Error responses

Single `@RestControllerAdvice` maps everything to `application/problem+json` (RFC 7807):
a stable `type` URI, a `title` slug, a safe `detail`, and — for body validation — an
`errors` map of field → message. Filter-chain 401/403 get the same shape via
`ProblemJsonHandlers`. Unexpected exceptions log a stack trace with a hex `reference` id
and return a bare `500 { detail: "An unexpected error occurred.", reference }`. No stack
trace, SQL, or Hibernate message ever reaches a client. `server.error.include-stacktrace`
and `include-message` are `never`.

## Logging

`RequestLoggingFilter` writes one line per request: method, path, status, duration, acting
user id (or `anonymous`). It is explicitly forbidden from logging the `Authorization`
header, request/response bodies, passwords, tokens, or message contents. Verification
tokens are logged nowhere.

## CSRF

Disabled in `SecurityConfig`, deliberately: the API authenticates with a Bearer token in
the `Authorization` header and never with an ambient cookie or session, so there is no
cross-site request-forgery surface. `SecurityIT.writeRequestsSucceedWithoutAnyCsrfToken`
pins this.

## Actuator

`management.endpoints.web.exposure.include=health` and `show-details=never`; `SecurityConfig`
permits `/actuator/health` and `denyAll()` on `/actuator/**`. `ApplicationContextIT`
verifies `/actuator/env` and `/actuator/beans` are not reachable.

## PII minimization

Collected: email (login + account recovery, never shown to other users) and a chosen
display name. Not collected: phone, address, student ID, date of birth, photos. In-app
messaging means the two parties never exchange real contact details. `DELETE /users/me`
anonymizes the row (tombstone email/name, random password, tokens invalidated, open
listings closed) instead of a cascading delete, so other users' threads survive while the
personal data is gone.

## Deployment notes

* Terminate TLS in front of the app; these headers assume HTTPS.
* Set `CAMPUS_JWT_SECRET` to a real 32+ byte secret.
* Wire an SMTP mailer and set `campus.auth.expose-verification-token=false` so the
  verification token stops being returned in the register response.
* Create the first admin with `CAMPUS_BOOTSTRAP_ADMIN=true` +
  `CAMPUS_ADMIN_EMAIL`/`CAMPUS_ADMIN_PASSWORD` (idempotent `ApplicationRunner`, not a
  migration).
