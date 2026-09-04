# Database schema

One Flyway migration: `src/main/resources/db/migration/V1__initial_schema.sql`. It runs
unchanged on PostgreSQL (production) and H2 in PostgreSQL mode (tests). All timestamps are
UTC `TIMESTAMP`; Hibernate is configured with `jdbc.time_zone=UTC`.

## Entity-relationship overview

```
users 1───* listings 1───* listing_attributes
  │             │  │ │
  │             │  │ └─* flags            *───1 users (reporter, resolved_by)
  │             │  └───* contact_messages *───1 users (sender, recipient)
  │             └─────* claims            *───1 users (claimant, decided_by)
  │
  1───* email_verification_tokens

listings (LOST) 1───* match_candidates *───1 listings (FOUND)
                          │
                          1───* match_reasons
                          │
claims *───0..1 match_candidates
```

## Tables

### `users`
| column | type | notes |
|---|---|---|
| `id` | bigint identity PK | |
| `email` | varchar(254) | `UNIQUE`; `CHECK (email = LOWER(email))` — the app lowercases, the DB enforces it, so no case-variant duplicate can exist |
| `display_name` | varchar(60) | `CHECK` length ≥ 2 after trim; not unique (real names aren't) |
| `password_hash` | varchar(255) | BCrypt now; width leaves room for Argon2/PHC later |
| `role` | varchar(20) | `CHECK IN ('USER','MODERATOR','ADMIN')`, default `USER` |
| `email_verified` | boolean | default `false`; required for create/message/claim |
| `password_changed_at` | timestamp | token cutoff; bumped by password change **and** logout-all / role change |
| `deleted_at` | timestamp null | set by anonymization; such accounts cannot log in |
| `created_at`, `updated_at` | timestamp | Spring Data auditing |
| `version` | bigint | optimistic lock |

### `email_verification_tokens`
`id`, `user_id` → users `ON DELETE CASCADE`, `token_hash` varchar(64) `UNIQUE`
(SHA-256 hex of the opaque token — the raw value is never stored), `expires_at`,
`consumed_at` null, `created_at`.

### `listings`
| column | type | notes |
|---|---|---|
| `id` | bigint identity PK | |
| `reporter_id` | bigint → users | **no** `ON DELETE` — users are anonymized, never row-deleted |
| `kind` | varchar(10) | `CHECK IN ('LOST','FOUND')` |
| `title` | varchar(120) | |
| `description` | varchar(2000) | |
| `category` | varchar(40) | `CHECK IN (…23 values…)` — mirrors the `Category` enum |
| `location_text` | varchar(200) null | free-text "approximate location" |
| `building`, `area` | varchar(80) null | optional structured location; used by the matcher |
| `event_date` | date | date lost/found; "not in the future" enforced in the service |
| `private_details` | varchar(1000) null | withheld identifying info — owner/moderator only |
| `status` | varchar(15) | `CHECK IN ('OPEN','MATCHED','RECOVERED','CLOSED','REMOVED')`, default `OPEN` |
| `created_at`, `updated_at`, `version` | | |

Indexes: `(kind, status)`, `(category)`, `(building)`, `(event_date)`, `(reporter_id)`.

### `listing_attributes`
`id`, `listing_id` → listings `ON DELETE CASCADE`, `attr_key` varchar(20)
`CHECK IN ('COLOR','BRAND','MATERIAL','SIZE','MODEL','PATTERN','OTHER')`,
`attr_value` varchar(60). `UNIQUE (listing_id, attr_key, attr_value)`. This is the
public, matchable description surface — deliberately no serial-number key.

### `match_candidates`
| column | type | notes |
|---|---|---|
| `id` | bigint identity PK | |
| `lost_listing_id`, `found_listing_id` | bigint → listings `ON DELETE CASCADE` | `CHECK` distinct; `UNIQUE (lost_listing_id, found_listing_id)` |
| `score` | integer | `CHECK 0..100`; equals `SUM(match_reasons.contribution)` |
| `status` | varchar(12) | `CHECK IN ('SUGGESTED','CONFIRMED','REJECTED')`, default `SUGGESTED` |
| `scorer_version` | varchar(16) | which weight set produced the score (re-score marker) |
| `created_at`, `updated_at`, `version` | | |

### `match_reasons`
`id`, `candidate_id` → match_candidates `ON DELETE CASCADE`, `signal` varchar(20)
`CHECK IN ('CATEGORY','KEYWORDS','LOCATION','DATE','ATTRIBUTES')`, `detail` varchar(300)
(human sentence; never contains a counterpart's private details), `contribution` integer
`CHECK 0..100`. Rows for one candidate sum to `match_candidates.score`.

### `claims`
`id`, `listing_id` → listings `ON DELETE CASCADE`, `claimant_id` → users,
`match_candidate_id` → match_candidates `ON DELETE SET NULL` (nullable),
`answer_text` varchar(1000) (the claimant's description of the withheld details),
`status` `CHECK IN ('PENDING','APPROVED','REJECTED','WITHDRAWN')` default `PENDING`,
`decided_by` → users null, `decision_note` varchar(1000) null, `decided_at` null,
`created_at`, `updated_at`, `version`.

### `contact_messages`
`id`, `listing_id` → listings `ON DELETE CASCADE`, `sender_id` → users,
`recipient_id` → users, `body` varchar(2000), `read_at` timestamp null, `created_at`.
`CHECK (sender_id <> recipient_id)`. Indexes on `recipient_id`, `sender_id`, `listing_id`.

### `flags`
`id`, `listing_id` → listings `ON DELETE CASCADE`, `reporter_id` → users,
`reason` `CHECK IN ('SPAM','SCAM','OFFENSIVE','WRONG_INFO','PROHIBITED_ITEM','OTHER')`,
`details` varchar(1000) null,
`status` `CHECK IN ('OPEN','REVIEWED','ACTIONED','DISMISSED')` default `OPEN`,
`resolved_by` → users null, `resolution_note` varchar(1000) null, `resolved_at` null,
`created_at`, `updated_at`, `version`. Index on `status`, `listing_id`.

"One unresolved flag per user per listing" is enforced in the service, not as a partial
unique index (H2 has no partial indexes) — see [design-decisions.md DD-2](design-decisions.md).

## Referential-integrity strategy

* Child rows that belong wholly to a listing (`listing_attributes`, `match_*`, `claims`,
  `contact_messages`, `flags`, `email_verification_tokens`) cascade on delete of their
  parent.
* `*_id` columns pointing at `users` have **no** `ON DELETE`. A user is never hard-deleted;
  `DELETE /users/me` anonymizes the row in place. This keeps other people's message and
  claim threads readable while removing the personal data.
