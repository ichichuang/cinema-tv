# Round 09 — Source Capability, Bounded Query Coordination, and Safe Subscription Candidate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` when available, otherwise `superpowers:executing-plans`. Work task by task with test-first RED→GREEN evidence and local commits. This round is authorized for bounded local development, **not** production activation or release.

**Goal:** Implement and actually test the T06 candidate layer that can interpret what each enabled source *can prove*, coordinate independent bounded pagination without fabricating results or totals, and prepare safe, explicitly consented subscription refresh without losing existing settings or user data.

**Architecture:** Keep the T02 contracts, T05 stable source/item/work identity and field provenance, and Round 07 single-writer authority. Separate (1) source capabilities from their transient health, (2) independently resumable source query cursors from UI results, and (3) externally fetched directory candidates from committed subscription configuration. Only an isolated candidate Room database and synthetic/local HTTP fixtures are used in this round; normal application startup, DI, screen, player, DataStore and existing source pathways remain unchanged.

**Tech Stack:** Existing Kotlin, coroutines, OkHttp, kotlinx.serialization, Room 2.8.5, KSP 2.3.12, JUnit and Android instrumentation, using the current verified build and disposable API 30 ARM64 test-only setup. Do not introduce new general-purpose networking or dependency stacks solely for testing.

**Spec:** `docs/superpowers/plans/2026-10-23-tv-experience-rebuild.md`, especially sections 7–9, 11, T06, A14/A15/A17/A18/A21. Read `docs/architecture/tv-data-contracts.md`, `tv-storage-contracts.md`, `tv-catalog-identity.md`, and current progress ledger. Reuse all actual Round 08 public types, not guessed definitions.

**Reported starting checkpoint (verify on the Mac):** `<isolated-worktree>`, branch `codex/contract-foundation`, clean tree; starting HEAD `96962f6a5255431bf9d316a4bfacac0d55ef7ca3`. `main` remains at `7f3e06318ddec8385e7d8be698a922a9cd85c5dd` per prior handoff. The unpushed Round 08 implementation is not supplied in this package: local source, interfaces, migrations and tests are the authoritative source.

## Global Constraints

- User-visible app language stays Simplified Chinese, no in-app software branding, and original icon requirement remains. This round adds **no new production UI** and no mock posters or purported catalog inventory.
- Never invent or infer a film's year, language, country, genre, availability or playback success. Catalog normalization preserves provenance, unknown, known-empty and conflict states. Cross-source identity is only verified under the proven Round 08 rules; no title/year automatic association.
- Exact filtering stays strict: an ignored remote filter parameter must not mark server filtering as verified. Unknown facts cannot pass as matching; the full `T07` strict UI and predicate implementation is **out of scope** here.
- No live internet scan, public source probing, new default media URLs or arbitrary public directory discovery. Only synthetic fixtures and a locally bound test HTTP server or injected test transport, on explicitly owned test environments.
- Production `update/sources.json` stays unchanged and empty. Local-network user-supplied endpoints remain a separate, explicit-consent pathway, not an exception smuggled into public catalog trust rules.
- Subscription consent, publisher trust and local candidate configuration commit are separate. A downloaded directory, a saved configuration and a playable media entry are three different states; never label any of them “已可播放” without an actual verified media check, which is outside this round.
- Refresh errors, temporary outages, disabled sources or cancellation do not erase the previous active candidate configuration, favorites, watch history, browsing snapshots, provenance or unresolved legacy staging. Never re-enable a user-disabled source automatically.
- Preserve existing Room schemas `1.json`–`4.json` **byte-for-byte**. If actual storage needs new tables/columns, perform explicit v4→v5 migration, export genuine schema `5.json`, and run old-data-preservation tests; no destructive fallback. If current persisted tables are demonstrably sufficient, omit version bump with evidence.
- Keep ordinary Debug and Release paths identical to pre-round semantics: package identity, version `0.9.8` / code `18`, minSdk 24, targetSdk 28, regular ARMv7 ABI, signing freeze, application navigation/player, and legacy writer authority unchanged. Any new candidate code is unbound from ordinary app DI and startup.
- No real user profile or physical television; no push, merge, rebase, publish, tag, signed distribution, workflow changes or real repository visibility changes. Use an explicitly owned disposable emulator only for native verification.

## Review Focus (must have precise tests)

1. **Ignored remote filtering:** a source returns 1995 items for a 1994 request. Its year-filter capability stays unsupported/unverified with *evidence*; no such item is claimed to match 1994, and an empty first locally filtered page is not “complete” (`SourceCapabilityTest`, `QueryCoordinatorTest`).
2. **Partial multi-source failure:** A succeeds, B times out. A items are returned with B failure and incomplete coverage; stale observations do not permanently disable B or display a global total (`QueryCoordinatorTest`).
3. **Independent pagination:** A and B have different cursor positions; duplicate/cyclic pages and exhausted request/time budgets stop with `Partial`, preserve retryable cursor evidence, and do not loop indefinitely (`QueryCoordinatorTest`, Android reopen test).
4. **Cancellation race:** old query Q1 is cancelled after Q2 starts. A late Q1 page cannot mutate Q2 results, source cursors or subscription selection; cancellation is propagated, not disguised as an empty page or network error (`QueryCoordinatorTest`).
5. **Malicious subscription directory:** empty, malformed, oversized, duplicate, unsigned/untrusted, replayed, redirected-to-private-network or remote-executable input fails closed; no commit, no access to private targets and no credentials in diagnostics (`SubscriptionRefreshTest`, native state preservation).

---

## R09-1 — Reconcile Round 08 contracts, fix exact two Minor coverage gaps

**Files:** Read current `data/catalog/`, `data/local/`, domain query and subscription contracts, existing DAO/schema, relevant tests. Add focused regression tests to existing Round 08 test classes; update `docs/architecture/tv-query-subscription.md` with the exact actually existing type/function map.

**Interfaces:** Consumes Round 08 source item/work records and correction transactions, and T02 catalog query/subscription types. Produces a concrete interface inventory for the remaining tasks; do not define parallel types merely because the plan uses an example name.

- [ ] **Step 1 — Verify:** Confirm current branch/worktree/HEAD, clean or dirty state, `1.json`–`4.json` digests and baseline unit/native counts; report drift without reset. Read the actual two Minor findings from `docs/verification/2026-10-24-round-08-work-identity.md` and tests.
- [ ] **Step 2 — RED:** Add native test with **actual pre-seeded disposable cache rows and separate user rows**, evict cache, close/reopen, and assert evidence/user records survive according to the exact schema's cache and evidence ownership rules. Test must fail meaningfully before fixing the old fixture/test behavior.
- [ ] **Step 3 — RED:** Add native test with a **three-member association A–B–C**, split the proven group, close/reopen and assert all three retain their original source/work keys, all link proofs are revoked as specified, no transitive re-join occurs, and all favorites/checkpoints remain assigned exactly as before. Confirm meaningful failing behavior before any implementation change; if existing production logic passes the new test immediately, record coverage added rather than invent a code defect.
- [ ] **Step 4 — GREEN:** Minimal fixture or code fixes only if failures reveal real defects. Rerun targeted and relevant existing tests, and record independently reviewable evidence. Never change identity association semantics to make test counts pass.
- [ ] **Step 5 — Inventory:** Identify the actual `CatalogQuery`, `CatalogResult`, `LoadOutcome`, `SourceCapability`, `SubscriptionRefreshRequest` and `SubscriptionState` signatures or their exact existing equivalents, DAO ownership, and source URL policy. Document only resolved names; any unavoidable interface adjustment needs a recorded ruling, tests and compatibility adapter.

**Acceptance:** The two Round 08 Minor coverage limits are closed by genuine executed tests (or documented as still open if native execution blocked), and all T06 implementation boundaries are based on real local code.

## R09-2 — Probe capabilities without conflating metadata, filtering and health

**Proposed files:** `app/src/main/kotlin/com/ichichuang/cinema/data/catalog/SourceCapabilityProbe.kt`, `SourceCapabilityCache.kt`; `app/src/test/kotlin/com/ichichuang/cinema/data/catalog/SourceCapabilityTest.kt`. Prefer extension of existing source capability types; no duplicate model family.

**Interfaces:** `probe(sourceId, request, observedResponse)` or equivalent pure/semi-pure method consumes validated source-scoped request/response evidence and returns an observation with observed-at timestamp and rule/probe revision; a separate cache persists observed capability and expiry without redefining stable Source IDs.

- [ ] **Step 1 — RED:** A source accepts `year=1994` but returns tagged 1995/unknown-year items; request HTTP 200 alone does not establish year filtering. Separate `field supplied` from `server filter observed`, and `temporarily failed` from `unsupported`.
- [ ] **Step 2 — RED:** Genuine category ID handling is source-scoped: two sources may have `t=6` but mean different categories; a server ignoring `t` cannot be treated as verified. A single empty page does not establish permanent unsupported status or full exhaustion.
- [ ] **Step 3 — RED:** A passing probe record is invalidated or marked stale on meaningful endpoint/adapter/protocol revision change; explicit authorized endpoint alias preserves stable source identity but requires new capability validation. A single timeout does not permanently remove capability.
- [ ] **Step 4 — GREEN:** Implement minimal capability observation and bounded candidate persistence, with timestamps, evidence and expiry strategy. Do not fetch live URLs or silently convert unknown into false; no hidden feature flags altering the existing player/legacy repository.
- [ ] **Step 5:** Run targeted JVM tests, candidate file-backed native close/reopen checks if persisted, and baseline regressions. Commit only after verified results.

**Acceptance:** Every capability claim states *what was actually demonstrated*, what remains unknown and when it was last checked.

## R09-3 — Bounded independent-cursor query coordinator (candidate only)

**Proposed files:** `app/src/main/kotlin/com/ichichuang/cinema/data/catalog/QueryCoordinator.kt`, `SourceCursorState.kt`; tests `app/src/test/kotlin/com/ichichuang/cinema/data/catalog/QueryCoordinatorTest.kt` and a native persistent query test. Reuse existing domain `CatalogRepository` contract rather than modifying `MovieRepository` on the active path.

**Interfaces:** `CatalogRepository.observeQuery(query: CatalogQuery): Flow<CatalogResult>` and `suspend loadMore(queryId: String): LoadOutcome`, reconciled with actual T02 signatures. Source transport is injectable and only synthetic/local HTTP fixtures are invoked. Cursor and snapshot revision live under query identity and source identity; cancellation has a scoped generation/token.

- [ ] **Step 1 — RED:** Two enabled sources A/B with independent pages return stable combined **source item keys**; A page 2 and B page 5 are not replaced by a shared `page=2`; duplicate same-source item/page does not add duplicates or loop.
- [ ] **Step 2 — RED:** Source A returns no locally accepted items on its first page but has more data on the next page: outcome stays `Partial/hasMore` until limits/exhaustion evidence, not `Complete`. A and B both truly exhausted yields `Complete` after provenance-safe merge/deduplication only.
- [ ] **Step 3 — RED:** A succeeds and B fails/transiently times out. Result includes A's committed items, B's explicit failure/unfinished coverage, `knownMatchedCount` for verified accepted items and `total=null` when unknown; never sum remote raw totals as global unique matched total.
- [ ] **Step 4 — RED:** Budget exhaustion for pages, elapsed work or bytes returns `Partial`, not `Complete`; same cursor re-emission and cyclic pages are detected. Cancellation or query supersession prevents late work from writing to the wrong query; preserve useful already committed page data when allowed by contract.
- [ ] **Step 5 — GREEN:** Implement minimal coordinator, deduplicating only verified same-work groups via the T05 layer while preserving independent playback entries. Strict facts without evidence are not accepted as exact matches; delegate full combinatorial matching, counts and year picker to T07. Do not create fake work IDs from titles.
- [ ] **Step 6 — Native:** If cursor/query state is persisted, close/reopen real Room file and assert independent cursors, partial coverage and query revision survive. If tables needed, create the explicit v4→v5 Room migration and test old seeded user/catalog/migration staging rows survive byte/field-wise; retain previous schema files unmodified. If not persisted, document why and leave cross-process cursor recovery explicitly unverified.

**Acceptance:** Synthetic multi-source pagination is finite, cancellable, source-isolated, honestly partial when incomplete and stable against old results arriving late.

## R09-4 — Trusted directory candidate and atomic, consented refresh

**Proposed files:** `app/src/main/kotlin/com/ichichuang/cinema/data/subscription/DirectoryValidator.kt`, `SubscriptionRefreshCoordinator.kt`, `CatalogTrustVerifier.kt` (or meaningful equivalents), tests `SubscriptionRefreshTest.kt` and native candidate-config persistence test. Examine `SourceCatalog.kt`, `CinemaSettings.kt`, `ConfigServer.kt`, and existing source selection flow **read-only** before implementation; existing flows remain the sole production path.

**Interfaces:** Honor actual existing `SubscriptionManager.refresh(request: SubscriptionRefreshRequest): Flow<SubscriptionState>`; separate directory fetch/validation, configured-source candidate state and per-entry media verification. Catalog trust uses owner-controlled explicit trust rules; synthetic fixtures only. A local test HTTP server is **not** a trusted production directory.

- [ ] **Step 1 — RED:** Empty directory returns `CatalogEmpty`; malformed JSON/schema, duplicates, oversized payload and unknown executable formats return typed failure; never accept scripts/remote modules/implicit code execution. An unavailable directory preserves previous committed candidate selection byte/field-wise.
- [ ] **Step 2 — RED:** Untrusted signer/origin, invalid signature or trust metadata, replayed/lower revision or changed payload with same revision cannot replace a previously accepted catalog. No real signing private key or unreviewed default trust root added. Fail closed if the current Android minSdk lacks a proven supported verifier.
- [ ] **Step 3 — RED:** Protocol/host/port/redirect, DNS re-resolution and IP classifications reject catalog-server requests to loopback, private, link-local, metadata or other local destinations, including redirects and DNS rebinding. The local test transport is explicitly injected/isolated; it is never a product allowlist exception. Keep user-provided LAN configuration out of this public catalog fetch policy.
- [ ] **Step 4 — RED:** Consent revision is checked at commit time, not just request creation. Disabled sources remain disabled; cancelled/overlapped refresh cannot roll back a newer selection; candidate `Configured` means persisted selection only, not tested playable media; every failure preserves last valid candidate config.
- [ ] **Step 5 — RED:** Expensive/slow/unresponsive directory and upstream behavior respects byte caps, timeout, concurrency limits, retries/backoff and cancellation. Do not include secrets, credentials, source URLs with query tokens, local addresses or full untrusted response bodies in diagnostic logs.
- [ ] **Step 6 — GREEN:** Implement minimal trust/validation plus **transactional candidate-only** config replacement, never touching active `CinemaSettings` or production `update/sources.json`. New persisted state uses an explicitly migrated schema and real native tests; no external scans or real third-party endpoint checks.
- [ ] **Step 7:** Verify typed state sequence and actual committed data after close/reopen with synthetic fixture directory, preserving consent and disabled selections; include one intentionally valid trusted fixture accepted by a test-only root, and multiple invalid fixtures rejected.

**Acceptance:** No catalog is activated without specific verified user consent and publisher trust; success states are truthful and persist only within the isolated candidate store. Public catalog trust is distinct from local/manual configuration.

## R09-5 — Native verification, independent review and evidence handoff

**Files:** `docs/verification/2026-10-24-round-09-source-query-subscription.md`, `docs/architecture/tv-query-subscription.md`, existing `docs/superpowers/progress/2026-10-23-tv-rebuild.md`, current scoped plan. All main sources/tests above are candidates only.

- [ ] **Step 1:** Inventory exact new/old test methods and expected packages before execution. Run targeted JVM suite, complete JVM suite and Python suite. Assert actual tests 0 failed, 0 skipped and 0 unexecuted; record actual command, counts and exit codes. Record correct RED→GREEN observations, or explain why a case passed immediately.
- [ ] **Step 2:** On an explicitly owned, disposable API 30 ARM64 emulator verified by serial/API/ABI/`ro.kernel.qemu=1`, execute the actual Android native methods for Room persistence/migration, cache seeded eviction, three-member split and subscription candidate commit. Pass explicit `adb -s SERIAL` to every mutating command; no physical TV or personal device operation. If no safe AVD, compile and mark native acceptance BLOCKED rather than passed.
- [ ] **Step 3:** Run the checked-in `python3 -m unittest discover -s scripts/tests -v`, `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleRelease`, `:app:assembleDebugAndroidTest`, `:app:lintDebug`, `:app:lintRelease` with `--no-daemon --no-configuration-cache` and documented candidate gates OFF. If CI requires a different checked-in command, record the exact compatible command and reason. Require normal unsigned Release and unchanged ABI, app identity, version and signing freeze.
- [ ] **Step 4:** Validate no runtime candidate insertion into Koin/DI, Application, `HomeViewModel`, `SettingsViewModel`, old `MaccmsMovieRepository` call path, `PlayerViewModel`, updater, production directory or existing user data. If a source adapter changed, compare ordinary path behavior and roll back unintended changes. Verify no dual writers or startup migrations.
- [ ] **Step 5:** Check all Room `1.json`–`4.json` original hashes against locally committed baselines; if a schema bump occurred, run explicit migration with old seeded rows and inspect emitted `5.json`/migrated SQL. Test cache eviction never purges favorites, history, checkpoints, unresolved legacy records or field evidence needed for provenance.
- [ ] **Step 6:** Stage only intended files, run `python3 scripts/check_repository_security.py` (index-only, do not claim full historical scan), compare staged/unstaged diffs, review credentials and source host handling, no new live URLs or demo catalog claims. Obtain an independent **read-only** review focused on SSRF, replay/trust, cancellation, query cursor loops, false completion, source identity and loss of user records. Repair Critical/Important findings with red/green tests, rerun suites and document whether reviewer re-reviewed changes.
- [ ] **Step 7:** Commit locally in coherent verified slices; update progress ledger precisely. Provide Simplified Chinese evidence report: baseline/final HEAD, commits, exact changed files, test counts, AVD serial, migration/schema hashes, trust assumptions, coverage status, preserved old paths, known gaps and unperformed real-source/TV/production actions. Stop at Round 09.

**Acceptance:** Verified **T06 candidate slices only** plus actual closure of the two prior Minor test gaps, where native proof exists. `A14`, `A15`, `A17`, `A18`, `A21` only gain explicitly named *synthetic subcase* evidence; neither full T06 nor T07, M03, any full A-scenario, default usable source, production subscription nor new UI may be marked complete.

**Stop conditions:** Unverified starting HEAD/dirty state, any evidence of affecting personal device or existing user data, source URL trust uncertainty, unsafe redirect/network boundary, loss-prone Room migration, inability to show single-writer candidate transaction, unexpected ordinary runtime behavior, or no safe instrumentation target. For safe test/environment blockers, leave candidate pieces isolated and report precise blocked checks; never bypass by faking test execution or overriding safety gates.
