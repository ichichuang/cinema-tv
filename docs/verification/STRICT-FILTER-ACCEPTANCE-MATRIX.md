# Round 10 — T07 Strict Multi-Facet and Year Filtering Acceptance Matrix

Only synthetic user-owned input fixtures are authorized. All expected results describe a **candidate** filtering engine, not the current UI or live source behavior. Record exact test method, actual execution command, suite, pass/fail/blocked status and the provenance of each synthetic field. Compiling a test never counts as executing it.

| ID | Synthetic case | Required assertion | Red-line failure |
|---|---|---|---|
| YEAR-01 | Year=1994 confirmed; query exact 1994 | Match with supporting year evidence | Missing/foreign year accepted |
| YEAR-02 | Year=1995; query exact 1994 | Rejected | Ignored server filter admitted 1995 |
| YEAR-03 | Year null/known-empty/unparseable | Indeterminate; not returned as exact match | Unknown silently matches |
| YEAR-04 | 1994 and 1995 contradictory year evidence | Conflict preserved, indeterminate | One disputed value chosen automatically |
| YEAR-05 | 1980s decade | 1980 and 1989 included; 1979 and 1990 excluded | Off-by-one year boundaries |
| YEAR-06 | Range 1985..1995 | Endpoints included; 1984 and 1996 rejected | Exclusive or silently clamped range |
| YEAR-07 | 1895 / 1962 / 1987; no indexed items | Positive years selectable without 2000 floor; no inventory claim | UI option generator uses lower bound of 2000 or fake count |
| YEAR-08 | Inverted/zero year selection | Typed validation failure | Silently normalized or coerced |
| YEAR-09 | `vod_year` 1994 vs update date 2026 | Source-declared 1994 retains provenance, update date cannot supply year | Last-update timestamp mislabeled release |
| FAC-01 | Country set `{日本, 泰国}` | A work confirmed with either matches | Multi-select requires both countries |
| FAC-02 | Genre set `{动作, 犯罪}` | A proven matching genre passes | Multi-select requires both genres |
| FAC-03 | Japan or Thailand + horror + 1990s | Across dimensions AND; all must match | Result passes only one selected dimension |
| FAC-04 | Coproduced Japan + USA | Japan selector matches with country-membership proof | Assumes unique country or discards co-production |
| FAC-05 | Source describes `欧美` or `惊悚` | Does not imply `美国` or `恐怖` | Broad heuristic manufactures classification |
| FAC-06 | Chinese work language + English work language | 华语 matches; multilingual fact retained | Other language cancels Chinese evidence |
| FAC-07 | Credible complete non-Chinese work-language fact | 非华语 matches, 华语 does not | Group based on dub/subtitle rather than work language |
| FAC-08 | Only Chinese dubbed audio or Chinese subtitles | Work-language group unknown, not 华语 | Chinese audio/subtitle reclassifies work |
| FAC-09 | Partial/known-empty/conflicting work language | Unknown unless scope-complete and nonconflicting | Unknown automatically treated 非华语 |
| FAC-10 | Known mismatch and unknown on two AND facets | Mismatch dominates, not match | Unknown or one matching facet overrides proven mismatch |
| FAC-11 | An OR dimension has one unknown, one mismatch | Indeterminate, not match | Unknown treated as true or false prematurely |
| FAC-12 | No active constraint for a dimension | Neutral for AND | Unselected facet excludes items |
| QRY-01 | Upstream ignores year=1994; sends 1995 | Post-filter drops false matches; source capability not falsely verified | HTTP 200 alone used as filter proof |
| QRY-02 | First page has 0 strict matches; later page has 1994 | Interim result remains Partial and fetching is resumable | First empty matched page marks Complete |
| QRY-03 | A success, B times out | Keep A rows, B failure and partial scope; global total null | False no-results or summed total |
| QRY-04 | Same verified work from two sources | Count once but preserve both playback entries | Count twice or lose an entry |
| QRY-05 | Same title/year but unrelated provenance | Not auto-merged | Collision merges different works |
| QRY-06 | Partial observed result sorted by year | Deterministic sort limited to observed data; no complete-catalog claim | Claims all-library chronological order |
| QRY-07 | Every selected source exhausted; no matching rows | Scoped complete-empty only with evidence; unknown-field caveat retained | Global empty claim ignoring missing facts |
| QRY-08 | Budgets exhausted, duplicate/cyclic pages | Partial, bounded, source cursor stable | Hang, unbounded scan, or false Complete |
| QRY-09 | Cancel Q1, Q2 active, Q1 reply arrives | Q1 cannot change Q2 filter, cursors or results | Late stale page leaks into new query |
| REL-01 | Ordinary zero-result or source error | Filter remains unchanged; no relaxation | Implicit fallback to broad listing |
| REL-02 | Explicit drop-year action | New selection drops only year and records action; prior selection unchanged | Other facets silently removed |
| DB-01 | Native file-backed candidate projection | Reopen retains saved selection, coverage and work links if persisted | Mock-only pass or lost record |
| DB-02 | Only if schema bump: v5→v6 | Full seeded v5 migration; schemas 1..5 unmodified | Destructive fallback or schema rewrite |
| OFF-01 | Candidate mode not activated | Production legacy filter/options/DI, DataStore, APK ABI and signing unchanged | New engine silently becomes normal runtime |

## Reporting and sign-off

- A test method that was compiled but not run remains `UNEXECUTED`.
- `A12`, `A13`, `A14`, `A15` receive **specific synthetic subcase evidence only**, not full product-level sign-off.
- All-source completeness, real CMS accuracy, phone/TV D-pad UX, real ARMv7/24 compatibility and production resource access remain unverified.
- If untrusted facts are missing, stop rather than adding guessed fields to make a test pass.
