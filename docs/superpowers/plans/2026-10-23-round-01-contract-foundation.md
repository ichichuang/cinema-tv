# Round 01: Baseline and Data Contract Foundation Implementation Plan

> For agentic workers: Please execute only this scoped plan with the available planning, test-driven development, and verification skills; use the master plan as the product specification, not as authorization to execute later rounds.

**Goal:** Establish an isolated, reproducible development baseline and compile-tested domain contracts without changing current application behavior or stored user data.

**Architecture:** Keep the existing native Android application and production paths intact. Add only the minimal domain value objects and repository interfaces required by chapter eleven of the master plan, alongside the legacy types. Do not wire these contracts into dependency injection or runtime navigation in this round.

**Tech Stack:** The repository's existing Kotlin, coroutine Flow, serialization support, and JUnit test tooling; no new dependency or toolchain version is required by this round.

**Spec:** `docs/superpowers/plans/2026-10-23-tv-experience-rebuild.md`, especially its Global Constraints and chapters five, seven, eight, nine, eleven, fourteen, and fifteen.

**Scope:** T01 local baseline and isolation work, plus only the data-contract slice of T02. This is not completion of M01, all of T02, or any T03–T16 task.

**Status at handoff:** Instructions and source materials prepared. No repository changes, application tests, builds, device operations, or implementation commits have been executed by this handoff.

## Global Constraints

- Preserve production identity `com.ichichuang.cinema`, current version settings, SDK/ABI settings, release-signing freeze, existing release assets, tags, workflows, and update metadata.
- Keep production behavior and legacy DataStore contents unchanged. Do not instantiate a new database, run a migration, install an APK, or operate a television.
- Preserve all unrelated working-tree and staged changes; never reset, clean, stash, overwrite, or include someone else's work automatically.
- Use an isolated worktree and development branch consistent with repository rules. Local commits containing only this round's verified work are allowed. Do not merge, push, publish, create a release, change repository visibility, or rewrite history.
- Do not scan arbitrary personal directories for the previously missing Downloads file. Use the supplied package and discovered attachment paths. A ChatGPT sandbox path is not a path on the user's Mac.
- Keep sources and content real: no live source scanning, unreviewed resource addresses, default subscriptions, production demo catalog, or generated movie facts in this round.
- Keep new domain contracts free of Android UI, Room, HTTP clients, player objects, and filesystem side effects. Do not add placeholder production repository implementations or empty UI screens.
- No new global dependency upgrade, build flag workaround, disabled security check, or weakened test to force a green result.
- English identifiers and technical types are internal. All future application-owned visible copy remains Simplified Chinese, without internal brand titles, debug information, or raw exceptions.
- Do not claim storage durability, strict filtering, subscription readiness, visual implementation, or device performance from contract unit tests.

## Review Focus

1. Unknown facts accidentally represented as zero, an empty success result, or a negative classification: cover in `CatalogContractTest` and `SubscriptionContractTest`.
2. Work identity coupled to a source URL, title, or list index: cover independent immutable identifiers and interface boundaries in `CatalogContractTest`; defer actual identity resolution to T05.
3. Invalid time or year inputs silently clamped into apparently valid data: cover explicit validation in `CatalogContractTest` and `UserDataContractTest`.
4. A fake store implementation returning success without a write: create interfaces only; verify no runtime registration or fake implementation is introduced.
5. Broad task completion claimed from partial work: link each G, T, and A identifier to concrete status and evidence in the progress ledger; retain unexecuted device and design gates.

## R01-A: Import authoritative materials and establish isolation

**Files:**
- Add unchanged: `docs/superpowers/plans/2026-10-23-tv-experience-rebuild.md`.
- Add: this scoped plan at its current relative path.
- Create: `docs/superpowers/progress/2026-10-23-tv-rebuild.md`.
- Create: `docs/verification/2026-10-23-round-01-baseline.md`.
- Do not automatically commit `references/no-brand-visual-direction.png`; retain it as a discoverable design reference and record a repository-relative location only if storing it in the repository is appropriate under repository rules.

**Inputs:** The user's read-only report, the full master plan, the supplied reference image, repository instructions, and the current local checkout.

**Outputs:** An isolated development location, recorded baseline commit, imported plans, initial progress ledger, and baseline command results.

- [ ] Read applicable repository instructions and the full master plan once; reuse the completed source understanding instead of repeating an exhaustive audit.
- [ ] Verify the actual local branch, HEAD, staged and unstaged changes, existing worktrees, JDK, and Android SDK configuration. Compare with reported HEAD `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`. Record drift and inspect affected files; do not reset to that commit.
- [ ] Establish an isolated development branch/worktree using the environment's supported workflow. Stop only the conflicting or unsafe operation if isolation is blocked; record the reason without touching the existing branch.
- [ ] Verify the package checksums before importing. Compare any existing plan at the destination and preserve differing versions instead of overwriting them.
- [ ] Record G01–G12, T01–T16, and A01–A24 in the progress ledger with honest statuses. Mark the T02 data-contract slice separately from icon, state-design, and visual-freeze work.
- [ ] Run the existing baseline checks below before changing application source. Record exit codes, actual test counts, and blocked checks. Capture long raw output only in an ignored local evidence directory; do not commit personal paths, secrets, or unredacted logs.
- [ ] Record missing authorized legacy fixtures or unavailable reference devices as unverified prerequisites. Never extract production device data just to complete the matrix.

Baseline commands, subject to the checked-in development guide:

```sh
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check_repository_security.py
bash ./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest --no-daemon --no-configuration-cache
```

The repository security script checks the Git index. Record that the baseline scan covers the baseline index; rerun after staging only this round's intended files. Do not pass release-signing properties. A successful release assembly here is an unsigned verification artifact, not a distributable update. Do not use offline mode unless the required dependencies are actually cached.

**Acceptance:** The original checkout and production settings remain intact; baseline results distinguish pass, pre-existing failure, and blocked execution.

## R01-B: Implement minimal domain value objects with tests

**Files:**
- Create focused files under `app/src/main/kotlin/com/ichichuang/cinema/domain/model/catalog/` for stable identifiers, field-evidence states, `CatalogQuery`, `CatalogResult`, and `LoadOutcome`.
- Create focused files under `app/src/main/kotlin/com/ichichuang/cinema/domain/model/userdata/` for `PlaybackSnapshot`, `BrowseSnapshot`, and `CommitResult`.
- Create focused files under `app/src/main/kotlin/com/ichichuang/cinema/domain/model/subscription/` for `SubscriptionRefreshRequest` and `SubscriptionState`.
- Create tests under the corresponding `app/src/test/kotlin/com/ichichuang/cinema/domain/` packages: `CatalogContractTest`, `UserDataContractTest`, and `SubscriptionContractTest`.
- Create `docs/architecture/tv-data-contracts.md` documenting exact Kotlin types, stable-ID boundaries, state semantics, and any justified deviation from chapter eleven.

**Inputs:** The minimum fields and semantics in chapter eleven of the master plan.

**Outputs:** Compile-tested value objects, explicit invalid-input handling, and a single documented type vocabulary for subsequent work.

Required behavioral assertions:

- `year_before_2000_is_valid`: a query for 1994 is accepted unchanged.
- `year_range_is_inclusive`: the contract records 1990–1999 as inclusive bounds; document that this does not test the future query engine.
- `inverted_year_range_is_rejected`: start 1999 and end 1990 are rejected, not silently swapped or broadened.
- `unknown_year_is_not_zero`: a missing year or date bound remains unknown/unbounded according to its field semantics; it is never encoded as year 0.
- `language_country_and_audio_are_independent`: original-work language, production country/region, and version audio metadata have separate representations; missing language does not default to non-Chinese. A short bilingual scene alone is not sufficient to classify a work as Chinese-language.
- `missing_and_conflicting_evidence_remain_distinct`: field states preserve absence versus conflict and retain evidence references; serialization, if implemented, must not erase that distinction.
- `empty_partial_result_is_not_complete`: an empty result with partial coverage and `hasMore=true` is representable without becoming complete or a confirmed absence of content.
- `unknown_total_remains_null`: an unknown total stays null; it is not inferred from the returned page size or replaced by zero.
- `source_failure_is_not_confirmed_no_match`: failed source identifiers remain distinguishable from a complete, verified empty result.
- `negative_position_is_rejected`: a negative playback position is rejected; zero position remains representable where valid.
- `unknown_duration_remains_null`: an unknown duration stays null; no artificial duration is invented.
- `negative_operation_sequence_is_rejected`: invalid ordering metadata is rejected. Document that operation sequences are session-scoped and that future storage must establish session ordering; identifiers or wall-clock timestamps alone do not prove recency.
- `browse_contexts_remain_independent`: distinct page context keys remain distinct through any implemented saveable representation; do not implement a real store to test this.
- `commit_outcomes_are_distinct`: Committed, RejectedStale, and Failed are distinct outcomes; declaring these types does not establish persistence guarantees.
- `configuration_saved_does_not_imply_playable`: Configured means configuration committed, not every movie verified playable; an empty directory remains a separate state.

- [ ] Write targeted failing tests and run them before the corresponding minimal implementation. A missing SDK, dependency resolution failure, or unrelated compile error is not evidence that a behavioral test failed for the intended reason.
- [ ] Implement only the required types and validation. Use the master's exact field names where practical; record precise rationale for necessary changes before changing downstream contracts.
- [ ] Choose immutable values and opaque stable identities; do not implement identity merging, random-ID allocation, full normalization, or matching algorithms in this round.
- [ ] Add round-trip tests only where an encoding is actually implemented. Do not claim serialization support from in-memory equality tests or invent a persistence format prematurely.
- [ ] Run the new tests and the existing unit-test suite; record actual names, assertions, results, and limitations.

**Acceptance:** New domain types compile, invalid inputs have explicit outcomes, unknown states are preserved, and all current runtime reads and writes still use their original paths.

## R01-C: Define interfaces without replacing legacy behavior

**Files:** Create focused interface files under `app/src/main/kotlin/com/ichichuang/cinema/domain/repository/` and update `docs/architecture/tv-data-contracts.md`.

**Consumes:** Types produced by R01-B.

**Produces:**
- `CatalogRepository.observeQuery(query: CatalogQuery): Flow<CatalogResult>`.
- `suspend CatalogRepository.loadMore(queryId: String): LoadOutcome`.
- `suspend PlaybackProgressStore.commit(snapshot: PlaybackSnapshot): CommitResult`.
- `suspend BrowseStateStore.save(snapshot: BrowseSnapshot): CommitResult`.
- `suspend BrowseStateStore.load(contextKey: String): BrowseSnapshot?`.
- `suspend FavoritesStore.setFavorite(workId: String, selected: Boolean): CommitResult`.
- `SubscriptionManager.refresh(request: SubscriptionRefreshRequest): Flow<SubscriptionState>`.

- [ ] Compile the interfaces against the new types using the existing Flow dependency. Do not register them in Koin or add placeholder implementations.
- [ ] Document repository responsibilities, cancellation propagation, partial coverage, idempotent favorite-setting intent, and truthful commit outcomes.
- [ ] Document the migration handoff: legacy reads remain active; no dual writers or cross-store migration execute now; future import is idempotent, transactionally marked, preserves unresolved records, and does not reconstruct history that was never stored.
- [ ] Review the diff to confirm that legacy `MovieRepository`, `MovieFilter`, `Movie`, navigation, player, settings, DataStore, and release configuration behavior has not changed.

**Acceptance:** Subsequent tasks have one shared contract vocabulary; no new runtime path is activated and no feature is reported implemented merely because its interface exists.

## R01-D: Verify, review, record, and stop

**Files:** Update the baseline report, progress ledger, and contract document with actual evidence.

- [ ] Rerun the new and existing tests plus the documented build and lint commands. Report any environment or pre-existing blocker separately from introduced failures.
- [ ] Stage only this round's intended files, then rerun the index-based security check. Review both staged and unstaged diffs; never stage the entire repository indiscriminately.
- [ ] Review requirement coverage, type consistency, unknown states, validation, cancellation documentation, and unintended runtime wiring. Use an available reviewer tool where appropriate; otherwise identify the review as self-review.
- [ ] Fix round-introduced defects with targeted red/green evidence. Do not fix unrelated production behavior or weaken checks to complete this round.
- [ ] Create small local commits for verified, coherent changes. Do not create a completion commit with unexplained new failures; leave blocked work clearly identified.
- [ ] Record branch/worktree, baseline and final HEAD, local commit hashes, changed files, command results, actual test counts, limitations, and remaining gates. Keep paths in tracked reports repository-relative and sanitize logs.
- [ ] Finish with a Simplified Chinese implementation report. State explicitly that no database migration, UI redesign, live resource verification, device testing, production signing, installation, push, or release occurred.

**Stop condition:** End after R01-D. Do not execute T03 or later tasks, complete the rest of T02, redesign screens, add Room, or begin data migration in this round.

## Progress mapping

| Master scope | Status allowed after this round |
| --- | --- |
| T01 local baseline and isolation | Verified only for commands and conditions actually checked |
| T01 authorized device/fixture prerequisites | Verified only with actual authorized evidence; otherwise unverified or blocked |
| T02 data-contract slice | Implemented and unit-tested if the actual test run passes |
| T02 visual states, icon deliverables, interaction freeze | Not completed by this round |
| T03–T16 | Not started by this round |
| A01–A24 | No blanket acceptance; associate any new contract tests as partial evidence only |
