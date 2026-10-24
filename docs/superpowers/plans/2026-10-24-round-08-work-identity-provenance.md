# Round 08 — Conservative Work Identity and Provenance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` as available; execute one testable task at a time and record real RED→GREEN evidence. The user has authorized bounded local implementation via the established Codex workflow, not release or production activation.

**Goal:** Implement the T05 candidate normalization, field provenance, stable source/work identity and conservative association layer that later multi-source queries and UI screens can trust without inventing metadata or losing personal records.

**Architecture:** Preserve the existing Kotlin/Compose/Media3/DataStore/Room feature-domain-data separation. Add pure normalizers and conservative identity decisions, then persist source items, evidence and reversible audited relations in a **candidate-only** Room data path. Reuse the exact Round 01 domain contracts and Round 07 Room v3 schema/legacy handoff authority; do not activate catalog reconciliation in ordinary application flows.

**Tech Stack:** Existing project Kotlin, kotlinx serialization, Room 2.8.5, current KSP and Android SDK toolchain as actually resolved in the unpushed worktree; JUnit and Android instrumentation using the proven disposable API 30 ARM64 test-only gate.

**Spec:** `docs/superpowers/plans/2026-10-23-tv-experience-rebuild.md`, especially sections 7, 8, 11, T05 and A13/A16/A18. Review `docs/architecture/tv-data-contracts.md`, `tv-storage-contracts.md`, `tv-playback-checkpoints.md`, `tv-legacy-handoff.md` and the existing progress ledger. Do not supersede these with this attachment.

**Reported starting checkpoint (verify locally):** worktree `<existing-isolated-worktree>`, branch `codex/contract-foundation`, HEAD `4097694bef7107ca1c6953d55bf9c78c3914b8ac`, clean tree. `main` and `origin/main` reportedly remain on `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`. Current unpushed source is not in this package: inspect it directly before writing implementations.

## Global Constraints

- Keep all user-facing product requirements: Simplified Chinese, no in-app brand title, original icons, TV remote focus, trustworthy metadata, durable progress/favorites. This round introduces **no UI**, external icons or demo catalog in production.
- Never infer film year, country, work language, genre, season, episode or media version from title/poster appearance alone. Source-declared metadata stays source-declared, not automatically verified.
- A label describing subtitle or dub cannot rewrite the original work language or production country. Multi-country and multi-genre records remain multi-valued.
- Multiple source items must remain separately addressable after grouping. URL, title, and year are **not** stable work IDs. Bare CMS IDs are not globally unique.
- A title+year match is at most a **candidate**, never an automatic merge; proof of a shared work must be explicit, auditable and revocable. Do not automatically trust a source-asserted external identifier as verified without its provenance/namespace rule.
- Preserve original typed evidence, unknown/known-empty/conflict semantics, provenance, rule version and reversible link history. Never overwrite an old year with an untraceable majority vote.
- Preserve the existing T03/T04 data, favorites, browse snapshots, per-episode checkpoints and the Round 07 unresolved staging. Correcting an erroneous grouping cannot silently delete or reassign them.
- Do not change active DataStore/Room writer authority or old single-writer handoff semantics. Candidate catalog tables and APIs remain unbound to ordinary runtime, DI, navigation and player.
- No live internet source probing, external APIs, automatic subscription, scraped film lists, unauthorised metadata or access to real user records. Use clearly marked synthetic fixtures only.
- Keep all existing Room schema snapshots 1/2/3 byte-for-byte. Add explicit v3→v4 migration and actual schema 4 only if new persisted representation requires it; never use destructive fallback.
- Keep package identity, `0.9.8`/code 18, minSdk 24, targetSdk 28, regular ARMv7 ABI, signing freeze and regular Debug/Release output unchanged. No push, merge, rebase, publish, tags, source catalog defaults, physical TV or personal device use.
- Verification claims require actual commands/results, not test compilation or a generated file alone. The original main tree remains untouched.

## Review Focus — concrete failures to exercise

1. Two unrelated CMS sources both use item ID `17` and matching display titles: remain distinct without explicit proof (`WorkIdentityTest`).
2. A source changes its endpoint: source identity and prior personal records survive **only** an explicit verified alias; similar hostname alone never creates alias (`WorkIdentityTest`, Android persistence test).
3. A purported link bridges conflicting identities or creates a transitive false merge: fail closed with conflict evidence, preserve all source items (`WorkIdentityTest`, Android persistence test).
4. One source claims 1994, another 1995; or the same source later corrects its field: preserve both raw values and conflict/history, never erase personal data (`CatalogNormalizationTest`, Android persistence test).
5. Misleading language metadata (`日语` work vs `国语配音` media vs `中文字幕` subtitles), two production countries, and ambiguous genres never silently cross-populate dimensions (`CatalogNormalizationTest`).

---

## R08-1 — Preflight: actual contract, v3 ownership and fixture design

**Files:** Read the actual `domain/model/catalog/`, `data/local/`, `data/userdata/`, `data/remote/MaccmsApi.kt`, `data/MaccmsSource.kt`, `Movie.kt`, Room migration and tests. Write `docs/architecture/tv-catalog-identity.md` (interface inventory and test fixture rules) and this round's evidence working notes.

**Interfaces:** Consumes existing `Catalog*`, `Source*`, `Fact*` or similarly named T02 types, the current Room schema and Round 07 stable handoff semantics. Produces an exact local interface reconciliation table before implementation; later tasks must reuse actual signatures rather than duplicate them.

- [ ] **Step 1:** Verify worktree, branch, starting HEAD, changes, existing commits and current test counts. If drift exists, document it; do not force reset, discard or override modified files.
- [ ] **Step 2:** Read exact model signatures, database entities, DAO queries, migration numbering and auth gates. Identify existing IDs and evidence types, the stable opaque ID allocation strategy and all user-record foreign-key relationships; pin actual names in the architecture document.
- [ ] **Step 3:** Build **synthetic** test fixtures for distinct sources A/B, same-title different work, explicitly verified shared ID, false/conflicting ID, URL alias, year conflict, cross-lingual dub/subtitle, dual-country movie, genre aliases and correction/split. Mark every fixture as synthetic; use no real playback URLs or copyrighted content.
- [ ] **Step 4:** Run focused baseline JVM tests and, when available, the current Android native suite on a pinned disposable AVD. Record genuine results and baseline warnings. Do not re-audit the whole repo.
- [ ] **Step 5:** Review the previous round's three repaired Important concurrency findings in the actual current code (old I/O drain, media revalidation, episode/retry revalidation). Request a limited independent re-review if available; record whether conducted. This is a carry-forward quality gate, not scope to redesign the player.

**Expected:** Contract and schema owner map backed by actual code, with all ambiguous fields and source claims classified before new tables/normalizers are written.

## R08-2 — Test-first pure normalization and evidence preservation

**Files:** Create focused pure helpers under `app/src/main/kotlin/com/ichichuang/cinema/data/catalog/` such as `MaccmsCatalogNormalizer.kt` and `TaxonomyRuleMapper.kt` (adapt file names to avoid existing equivalents). Reuse existing remote DTO and contract types. Add `app/src/test/kotlin/com/ichichuang/cinema/data/catalog/CatalogNormalizationTest.kt` (or the current test package equivalent).

**Interfaces:** Consumes a source-scoped raw CMS DTO and explicit versioned mapping rules; produces a candidate normalized source item with separate work facts, playback facts and original field evidence. No network requests, side effects, UI data or global unverified lookups.

- [ ] **Step 1 — RED:** Test raw year `1987`/`1994` remains source-declared with its original value and source, malformed year remains unknown, 1994 versus 1995 remains a conflict after association, and empty-vs-unknown is preserved. A multi-valued country remains two values; vague `欧美` is not converted to `美国`.
- [ ] **Step 2 — RED:** Test that `vod_lang` or a dub label cannot be asserted to equal original work language without explicitly defined provenance, and `中文字幕` is not audio. A Japanese-origin work with a Mandarin dub remains Japanese-origin with a separately recorded dub claim.
- [ ] **Step 3 — RED:** Test exact, versioned taxonomy aliases: safe variants of `动作片`/`動作片`/`动作` map to a canonical action tag while keeping each original; `惊悚` does **not** become `恐怖`; a CMS category `type_id` is scoped to its source and does not map across services by coincident numeric ID.
- [ ] **Step 4 — GREEN:** Implement minimum pure transformation and evidence objects using existing contract semantics. Ambiguous or compound labels stay unclassified until a rule proves their meaning; no text classification guesses and no loss of raw values.
- [ ] **Step 5:** Rerun focused tests and full JVM suite. Save failure→pass evidence; make a local commit for this independent pure-normalizer unit if tests actually pass.

**Expected:** For every assigned normalized fact, the record exposes original source, field, raw value and mapping-rule version. Absence of evidence remains absence.

## R08-3 — Persist source-scoped IDs, evidence, and auditable association

**Files:** Add/reuse entities and DAO under `data/local/`, and a candidate adapter under `data/catalog/`. Only if necessary, modify Room database registration and add explicit v3→v4 migration with schema `app/schemas/com.ichichuang.cinema.data.local.CinemaDatabase/4.json`. Tests: `WorkIdentityTest.kt` and `WorkIdentityStoreInstrumentedTest.kt` using the actual local test package structure.

**Interfaces:** `SourceIdentity` is an opaque stable ID independent of endpoint URL; `SourceItemKey` includes that stable source identity plus source-local item ID; `WorkIdentity` is a distinct opaque local work ID; `AssociationDecision` must distinguish linked/only-candidate/conflict/unresolved, and retained field evidence stores original claims. Reconcile names with actual T02 contracts, do not introduce parallel semantically identical types.

- [ ] **Step 1 — RED:** Two sources with the same item ID and title remain distinct by default. Reingesting the same source item keeps its stable identity and is idempotent. Similar title+year produces an unresolved **candidate** only, not a committed association.
- [ ] **Step 2 — RED:** A trusted, explicitly fixture-verified, fully namespaced matching identifier allows a recorded association while retaining both independent source items and playback entries. Identifiers asserted by an unknown source alone remain claims. Conflicting namespaced IDs or inconsistent evidence fail closed and record conflict instead of transitive accidental merging.
- [ ] **Step 3 — RED:** A source endpoint change preserves stable source ID only through an explicit authorized alias proof; raw URL similarity, redirects or normalizing hostnames cannot grant source identity. Old ID/source pairs from legacy staging remain unresolved unless independently verified.
- [ ] **Step 4 — RED:** A mistaken grouping can be undone without deleting either source item, original evidence, the existing opaque work key, favorite, browse snapshot, or any episode/version checkpoint. Association history must permit audit; any user-record reassignment requires separate explicit verified action, not hidden cascade.
- [ ] **Step 5 — GREEN:** Implement real candidate Room persistence, uniqueness and foreign-key rules. Use transactions for association/split; no fake in-memory repository may stand in for native tests. If schema changes, keep `1.json`, `2.json`, `3.json` bytes fixed and add explicit migration and `4.json` with no destructive fallback. An unchanged schema is acceptable only with evidence that existing tables truly fulfill requirements.
- [ ] **Step 6 — Native check:** Seed version-three Room database with personal records, run actual migration to next schema when applicable, close/reopen, verify old rows byte/field preservation, association idempotency, link conflict handling, source URL alias and per-episode progress survival. Commit only after execution evidence or explicitly mark native verification blocked.

**Expected:** Durable source and work identity, reversible audited links and untouched personal state across data changes. New candidate catalog tables have no effects on active app writers.

## R08-4 — Candidate Apple CMS projection and correction behavior

**Files:** Reuse `data/remote/MaccmsApi.kt` remote DTO, existing `MaccmsSource.kt`, and new `data/catalog/` adapter. Add `CatalogProjectionTest.kt` plus native persistence cases. No runtime `MaccmsMovieRepository` replacement.

**Interfaces:** Converts **synthetic** CMS list/detail fixtures into source-scoped source items and evidence; preserves multiple versions and playback entries. Does not query the internet; doesn't change `MovieRepository`, existing `Movie` keys or production DI.

- [ ] **Step 1 — RED:** Import synthetic partial list followed by richer detail; preserve provenance for newer known fields while retaining old evidence. Conflicting field update stays visible as conflict/history, not rewritten consensus. Source item with missing year stays unknown.
- [ ] **Step 2 — RED:** Two distinct movie versions or episodes with similar titles do not collapse. Link same work across two sources only if the explicit association gate passes; keep both playback entries after linking.
- [ ] **Step 3 — RED:** Update a source's endpoint via verified alias; stored favorite/history and playback pointers continue to refer to their existing stable local IDs. Mark source temporarily unavailable without clearing catalog items or user data.
- [ ] **Step 4 — GREEN:** Provide a local candidate projection adapter, backed by persisted source item/evidence records, with no network ingestion and no user-facing exposure. Keep data conversion deterministic and cancellable; protect against malformed inputs with preservation, not fabricated defaults.
- [ ] **Step 5:** Execute JVM and genuine file-backed Android tests with close/reopen, and collect red/green proof. Do not silently backfill unverified links from the Round 07 pending migration records.

**Expected:** A small but genuine multi-source, provenance-preserving catalog candidate, entirely offline and isolated from production paths. Not a fully functional T06 query coordinator.

## R08-5 — Full verification, independent review, and local evidence

**Files:** Add `docs/verification/2026-10-24-round-08-work-identity.md`, update `docs/architecture/tv-catalog-identity.md` and existing `docs/superpowers/progress/2026-10-23-tv-rebuild.md`. Keep scoped plan under `docs/superpowers/plans/` without overriding other plans.

**Interfaces:** All prior tests and native database seams, ordinary gate-OFF builds, existing single-writer authority.

- [ ] **Step 1:** Inventory exact targeted JVM methods and native test methods before execution. Run targeted host and actual native tests, recording pass/fail/skipped/unrun separately. Verify a disposable owned emulator, expected serial, `ro.kernel.qemu=1`, API=30, ARM64, no personal data, and explicit `adb -s` before every mutating command. Stop rather than using a physical TV or broad device broadcast.
- [ ] **Step 2:** Run `python3 -m unittest discover -s scripts/tests -v`, `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleRelease`, relevant AndroidTest compilation and two lint tasks with `--no-daemon --no-configuration-cache`. Record actual counts, exit codes, baseline warning differences and ordinary gate-OFF package/version/ABI/signing constraints; require Release unsigned.
- [ ] **Step 3:** Explicitly test old catalog/legacy paths remain unchanged when all candidate gates are OFF: no new API calls, imports, Room writer, DI module or runtime route. Verify old progress/favorite/browse and legacy staging are not altered by taxonomy/metadata changes.
- [ ] **Step 4:** Check generated Room implementation, exported schema, SQL uniqueness/FK behavior and all old schema hashes. Stage only intended files, run `python3 scripts/check_repository_security.py` (index-only scope), review staged and unstaged diffs, and check no credentials, personal paths, real URLs, remote executable content or fake film data entered production assets.
- [ ] **Step 5:** Obtain independent read-only review if available, specifically covering false merges, transitive conflicts, alias spoofing, evidence loss, split behavior, cache eviction versus favorites/progress and migration safety. Important findings require failing tests and minimal fixes; record whether the original reviewer re-reviewed. Commit locally in cohesive verified slices and include exact HEAD, commits, changed files, commands, test counts, AVD identity, results and remaining limitations in Simplified Chinese.

**Expected:** Evidence-based handoff, worktree clean, no push/merge/release, no release or physical-device action.

## Acceptance and stop conditions

**Allowed to mark:** Verified T05 **candidate slices only**: normalization/provenance and conservative identity/association where host and relevant native assertions actually executed. Note partial A13/A16/A18 synthetic subcases without marking those whole acceptance scenarios complete.

**Stop/restrict:** Head drift or unreviewed user changes; ambiguous identities; untrusted source alias; inability to preserve user records on split; incompatible migrations; no exclusively owned native test device; the first indication of runtime writer changes or new production sources. Preserve data and report blocked/partial. Do not solve by collapsing unknown fields, guessing links, skipping tests, muting lint, deleting old schema, or using destructive fallback.

**Never claim:** All of T05/T03/T04 or M02 complete; production multi-source catalog or strict filter complete; real source metadata verified; all real user records mapped; ARMv7 TV or real device certified; production migrations enabled; default media sources available; UI/graphics redesigned; signed APK published.

**Source of truth:** Existing **unpushed** Round 07 code and ledgers on the user's actual Mac. This package contains instructions and test ideas only and cannot override verified local types or persisted schema facts.
