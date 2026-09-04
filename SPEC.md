# Requirements specification — Campus Lost &amp; Found v1.0

## Problem

Lost-item reports on a campus are spread across group chats, social media, department
emails, and multiple physical desks. Items and owners fail to reunite even when both a
lost report and a found report exist. This service is the single place both sides post,
and it actively cross-references them.

## Actors

| Actor | |
|---|---|
| **Visitor** | unauthenticated; may browse and search public listings only |
| **User** | registered; after email verification may report items, search, contact, claim, flag |
| **Moderator** | User + flag queue, resolve flags, take down listings, see any listing in full |
| **Admin** | Moderator + change user roles |

## Functional requirements

| ID | Requirement |
|---|---|
| FR-1 | Register with email + password + display name; verify email; log in for a bearer token; log out everywhere |
| FR-2 | Report a **LOST** or **FOUND** listing: title, description, category, approximate location (free text + optional building/area), event date, withheld identifying details, and controlled structured attributes (colour, brand, material, size, model, pattern) |
| FR-3 | Search listings by free-text query; filter by kind, category, status, building, date range; paginated, sorted (allow-listed fields) |
| FR-4 | View listing detail with attributes and a suggested-match count; withheld details and reporter identity are shown only to the reporter and moderators |
| FR-5 | Reporter or a moderator edits mutable fields and moves the listing through a validated status lifecycle |
| FR-6 | Mark an item **RECOVERED** — permitted for a non-moderator only after a claim on that listing has been approved |
| FR-7 | On listing creation (and on demand) compute potential matches against open listings of the opposite kind, each with a 0–100 score and a list of human-readable reasons whose contributions sum to the score |
| FR-8 | An involved reporter or a moderator **confirms** or **rejects** a suggested match; confirming moves both listings to MATCHED; confirming is reversible; rejecting is permanent |
| FR-9 | Submit an ownership **claim** (a description of the withheld details); the finder or a moderator approves or rejects; the claimant may withdraw a pending claim |
| FR-10 | Send an **in-app message** to a listing's reporter; view inbox and sent; mark received messages read — without either party revealing email or phone |
| FR-11 | **Flag** a listing (spam, scam, offensive, wrong info, prohibited item, other); one unresolved flag per user per listing; moderators work a queue and resolve or take down |
| FR-12 | Delete your own account — anonymized in place, tokens invalidated, open listings closed |
| FR-13 | Public reference endpoints: category list; health |
| FR-14 | OpenAPI 3 document + Swagger UI + example requests |

## Non-functional requirements

| ID | Requirement |
|---|---|
| NFR-1 | Correctness and security take priority over feature breadth |
| NFR-2 | Layered architecture; business rules confined to the service layer |
| NFR-3 | Relational database with FK + CHECK constraints; all queries parameterized |
| NFR-4 | Deterministic, independently unit-testable matching engine; all knobs in config, validated at startup |
| NFR-5 | RFC 7807 `application/problem+json` for every error; no stack traces, SQL, or internal messages to clients; no user enumeration on login |
| NFR-6 | Passwords hashed (BCrypt); no plaintext stored or logged; tokens invalidated on password/role change and logout-all |
| NFR-7 | Minimal PII: email + display name only; in-app contact; no phone/address/ID/photo |
| NFR-8 | `./mvnw verify` green on JDK 26; automated unit + integration tests for every FR area |
| NFR-9 | Single relational migration that runs on PostgreSQL and on H2 (PostgreSQL mode) |

## Out of scope for v1.0

Photo upload / image matching, email or SMS notifications, real-time chat, mobile app,
campus SSO/LDAP, geospatial coordinates, rewards or payments, multi-campus tenancy.

## Design decisions

Locked decisions and their rationale are in
[docs/design-decisions.md](docs/design-decisions.md) (DD-1 … DD-15).
