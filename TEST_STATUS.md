# Test status

`./mvnw verify` — **green on Oracle JDK 26**. 43 unit (Surefire) + 40 integration
(Failsafe, full Spring context on Flyway-migrated H2) = **83 tests**.

Last run: v1.0.

## Unit tests (`*Test`, no Spring context)

| Class | n | Covers |
|---|---:|---|
| `matching/MatchEngineTest` | 10 | per-signal sub-scores and boundaries, `Σ contribution == score`, category `OTHER` partial, date decay at 0/7/14/21 days, found-before-lost penalty, building > area, distinctive-keyword weighting, attribute agreement, reason names only shared words |
| `matching/TextNormalizerTest` | 4 | lower-case + split + punctuation + short-token drop, stop-words, null/blank parts, distinctive vs common |
| `domain/ListingStatusTest` | 16 | full transition table (`@CsvSource`), terminal states |
| `security/JwtServiceTest` | 4 | issue/parse round-trip, tampered token, expired token, wrong-secret token |
| `service/support/RateLimiterTest` | 4 | allow-to-limit-then-429 (+ `Retry-After`), per-caller isolation, window reset, disabled = never throws |
| `web/validation/SafeTextValidatorTest` | 4 | ordinary text incl. `<`, control chars rejected, bidi-override rejected, angle brackets never stripped |
| `SmokeTest` | 1 | toolchain |

## Integration tests (`*IT`, `@SpringBootTest` + MockMvc on H2)

| Class | n | Covers (mapped to the required list) |
|---|---:|---|
| `ApplicationContextIT` | 3 | context loads = **schema ↔ entity `validate` + Flyway on H2**; `/actuator/health` public & detail-hidden; other actuator endpoints not reachable |
| `AuthIT` | 9 | **registration** (201, no password echo, dup → 409, short pw → 400, unknown field → 400); **authentication** (login after verify, wrong pw = uniform 401, no enumeration); account lockout after 5 fails (+ `Retry-After`); logout-all invalidates the token; protected route → problem+json 401 |
| `ListingIT` | 9 | **creating listings** (verified-email gate, future date → 422, missing fields → field errors); **redaction** (private details / reporter hidden from anonymous + stranger, shown to owner); **searching & filtering** (q, kind, category, building, date range, empty result); invalid sort field → 400; **authorization** (non-owner patch → 403); **updating status** (illegal transition, REMOVED needs moderator, RECOVERED gated); attributes add/remove + duplicate → 409 |
| `MatchingIT` | 5 | **matching** auto-suggested on create with `Σ contribution == score` and score ≥ threshold; private details absent from the explanation; weak pair → no suggestion; **unauthorized access** (stranger → 403, anonymous → 401); confirm → both MATCHED, unconfirm → OPEN; rejected pair not recreated by rescan |
| `ClaimIT` | 3 | claim submit → decision → RECOVERED gate; can't claim own / a LOST listing; duplicate pending claim → 409; claim list visible only to finder/moderator |
| `MessageIT` | 4 | delivery to reporter inbox + sender sent; mark-read for recipient only; can't message your own listing; third party can't read someone's message → 403 |
| `ModerationIT` | 3 | flag → one-unresolved-per-user (409) → moderator queue (non-mod → 403) → resolve → takedown → removed listing 404s publicly; owner can't take down own listing; admin role change (non-admin → 403, invalidates target's token) |
| `SecurityIT` | 4 | defensive headers present; **write works with no CSRF token**; not-found = problem+json, no stack trace; malformed JSON → 400 |

## Database operations covered

CRUD + constraint enforcement via the migrated H2 schema on every IT; `ON DELETE CASCADE`
exercised implicitly by the per-test `TRUNCATE ... RESTART IDENTITY`; `UNIQUE`/`CHECK`
violations surface as 409 not 500 (dup email, dup attribute, dup flag, dup pending claim).

## Coverage

`./mvnw -Pcoverage verify` — JaCoCo, 80% line gate on `com.campuslostfound.matching.*` and
`com.campuslostfound.service.*`. Must run on a JDK 21–25 (see design-decisions DD-13); the
default build is clean on JDK 26.
