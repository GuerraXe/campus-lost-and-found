# Matching methodology

The goal is **high recall with an honest, legible score** — surface every plausible
counterpart and let a human judge, never assert a match.

## Pipeline

1. **Trigger.** `MatchingService.onListingCreated` runs synchronously after a listing is
   saved. `POST /listings/{id}/matches/rescan` re-runs it on demand (owner/moderator,
   rate-limited).
2. **Pre-filter (SQL).** `ListingRepository.findMatchPrefilter` returns only listings that
   are: the opposite `kind`; `OPEN` or `MATCHED`; and share the `category`, **or** share
   the `building` (case-insensitive), **or** have an `event_date` within
   `campus.matching.prefilter-days` (default 21). This is what keeps the create path
   bounded — without it every create would score against the entire open corpus.
3. **Score** each survivor with `MatchEngine.score(lost, found)`.
4. **Keep** candidates scoring `>= campus.matching.suggest-threshold` (default 45), take
   the top `max-candidates-per-listing` (default 25) by score, and upsert a
   `match_candidate` (+ its `match_reason` rows). A pair already `REJECTED` is skipped —
   rejection is sticky across rescans.

## The five signals

Each returns a sub-score in `[0, 1]`. `contribution = round(weight × sub × 100)`;
`score = Σ contribution`. Weights sum to exactly 1.0, so the maximum score is 100 and the
reasons always add up to it.

### Category — weight 0.30
Exact enum equality → `1.0`. If either side is `OTHER` → `0.25` (a weak signal: "unknown"
shouldn't fully block or fully credit). Otherwise `0.0`.

### Keywords — weight 0.30
Tokenize `title + description + privateDetails` on both sides (`TextNormalizer`:
lower-case, split on non-alphanumerics, drop tokens < 2 chars and a small English
stop-word list; **no stemming** in v1). Let `A`, `B` be the token sets and `S = A ∩ B`.

```
overlapCoef      = |S| / min(|A|, |B|)          # not Jaccard: doesn't punish a detailed description
distinctiveShare = |{ t ∈ S : t not generic }| / |S|
sub              = clamp( 0.7 * overlapCoef + 0.3 * distinctiveShare , 0 , 1 )
```

"Generic" = a curated common-word list (`lost`, `found`, `black`, `small`, `campus`,
`reward`, …). Sharing *kryptonite* counts for more than sharing *black*.

The reason names only the shared words (up to 6, alphabetical) — never a full description,
and by construction those words are a subset of what the viewer's own listing already
contains.

### Location — weight 0.15
Same `building` (both present, case-insensitive) → `1.0`. Else same `area` only → `0.6`.
Else overlap of `location_text` tokens → `0.5 × overlapCoef`, capped at `0.5`. Else `0.0`.

### Date — weight 0.15
```
Δ    = |lost.event_date − found.event_date|  in days
base = max(0, 1 − Δ / date-decay-days)        # date-decay-days default 14
if found.event_date < lost.event_date − 1 day:  base *= 0.5
sub  = base
```
The half-penalty encodes "you cannot find something before it is lost" without hard-
excluding same-day/next-day noise in the reported dates.

### Attributes — weight 0.10
Over the structured attribute keys present on **both** listings, the fraction whose values
agree (case-insensitive). No shared keys → `0.0`. Serial numbers are deliberately **not**
an attribute key — a serial is proof of ownership, handled in the claim workflow, not a
public match signal.

## Worked example

LOST *"Lost silver Dell laptop"* / *"Silver Dell XPS 13 with a rainbow vinyl sticker on
the lid"*, Main Library, 2026-03-10.
FOUND *"Found a Dell laptop"* / *"Dell laptop, rainbow sticker, left on a desk in the
library"*, main library, 2026-03-12.

| signal | sub | contribution |
|---|---:|---:|
| category | 1.0 (LAPTOP = LAPTOP) | 30 |
| keywords | shared {dell, laptop, rainbow, sticker, silver…} | ~22 |
| location | same building | 15 |
| date | Δ = 2 → 1 − 2/14 ≈ 0.86 | ~13 |
| attributes | no shared structured keys | 0 |
| **score** | | **~80** |

Reasons returned: *"Same category: Laptop"*, *"5 shared keywords: dell, laptop, rainbow,
silver, sticker"*, *"Both in building 'Main Library'"*, *"Reported 2 days apart"* — and the
standard disclaimer.

## Tuning &amp; configuration

Everything is in `campus.matching.*` (`MatchingProperties`), validated at startup (weights
must sum to 1.0, threshold in `0..100`):

```
suggest-threshold            45
prefilter-days               21
max-candidates-per-listing   25
scorer-version               v1
weight-category / keywords / location / date / attributes   0.30 / 0.30 / 0.15 / 0.15 / 0.10
date-decay-days              14
```

The threshold of 45 means *category alone* (30) or *category + a weak date* is **not**
surfaced — on a busy campus that combination is noise. A confident pair typically lands
70–90. `scorer_version` is stamped on every candidate so a future weight change is
detectable and the affected rows can be re-scored.

## Known limitations (v1)

* No stemming or synonyms — "charger" ≠ "charging cable" ≠ "power adapter" on the keyword
  signal (category partly compensates).
* Tokenizer assumes English; non-English descriptions score mostly on category / location
  / date.
* Scoring is in-request. Fine at portfolio scale with the pre-filter; a queue is the
  production choice ([design-decisions.md DD-9](design-decisions.md)).
* A tiny crafted listing can still score on keywords via the overlap coefficient; the
  residual probing risk is mitigated by auth-scoped match visibility, email verification,
  and rate limits, and is documented in [security.md](security.md).
