# Round 07 — Legacy DataStore → Room Single-Writer Handoff Rehearsal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the repository's applicable `superpowers:executing-plans` or `superpowers:subagent-driven-development`, with test-driven development, systematic debugging, and verification-before-completion. Execute this plan task by task in the existing isolated worktree. **Stop after Round 07.**

**Goal:** Prove a lossless, restart-safe transition from actual legacy DataStore files to the validated Room v2 candidate, and prove exclusive write ownership after a controlled handoff on a disposable Debug test profile; preserve the normal Debug/Release legacy path completely unchanged.

**Architecture:** Reuse the Round 02 typed snapshot reader and transactionally idempotent staging importer, the Round 05 persisted session arbitration, and the Round 06 guarded Media3/Room probe. Add a narrow, persistent handoff state machine and an auditable readiness decision. Read real legacy data via its existing singleton DataStore owners while old writers are quiesced; stage and validate in Room; activate only for a deliberately authorized, fully resolvable disposable test profile. Because DataStore and Room have no shared atomic transaction, writer quiescence and a persisted Room activation marker define the handoff boundary. Never silently revert to legacy once Room has accepted newer writes.

**Tech Stack:** Existing Kotlin/coroutines, DataStore Preferences, Room 2.8.5, Media3, Compose TV, Koin, AndroidX Test, existing Gradle and test-only ARM64 gate; use existing Room schema v2 unless an actual migration is justified.

**Spec:** `docs/superpowers/plans/2026-10-23-tv-experience-rebuild.md` §§5–6 and T03/T04/T08/T09/T15, A05–A11/A20, the actual Round 01–06 implementation and verification files, especially `docs/architecture/tv-storage-contracts.md`, `docs/architecture/tv-playback-checkpoints.md`, `docs/architecture/tv-playback-runtime-probe.md`, and the project progress ledger.

**Reported starting point (verify on the Mac):** `codex/contract-foundation`, HEAD `a7deea8a88ff0afa2f7ba06fd7c4f615f8018af1`. Round 06 was reported as JVM 177/177, Python 62/62, Android main 68/68 plus separate 2/2 process-restoration and 1/1 legacy-probe-off checks. These results are a user report, not an independent verification by the plan author. Original `main` was reported at `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`.

## Global Constraints

- Work **only** in the existing isolated worktree and branch. Verify HEAD, branch, staged/unstaged changes, worktrees and actual implementations before editing. If HEAD differs, preserve changes and record a ruling; no reset, rebase, new worktree, push, merge, publish, tag updates or remote alteration.
- Keep app identity `com.ichichuang.cinema`, `versionName=0.9.8`, `versionCode=18`, `minSdk=24`, `targetSdk=28`, normal `armeabi-v7a`, ordinary Debug suffix, frozen production signing, and release metadata unchanged. No APK or device installation outside the positively verified disposable emulator.
- Normal Debug and Release must remain **legacy DataStore exclusive** with `cinema.playbackRoomProbe=false` by default. The handoff rehearsal must require the existing `playbackRoomProbe` build/test authorization plus a distinct, default-off, test-only migration consent token on a newly created disposable profile. Synthetic legacy rows deliberately seeded in that profile are expected; use a **separate authorized migration eligibility path**, not a bypass or relaxation of the Round 06 probe refusal for unknown pre-existing legacy profiles. Prefer using an existing trusted test runtime gate instead of weakening Gradle guards; prohibit enabling this rehearsal for release/bundle/signing/publish tasks.
- Do not read, copy, upload, inspect, clear, or modify actual personal/TV data, real service credentials or production signing materials. Use only synthetic data written through actual legacy storage APIs inside the app-private test sandbox of a test-owned emulator. No live content scans or external media URLs.
- Preserve old DataStore files unchanged on success, refusal and failure; no deletion, rewriting, truncation, corruption replacement or destructive migration fallback. Do not assume that an unparseable legacy record represents an empty/default state.
- Do not create a second live DataStore instance for the same file. Use existing singleton owners and appropriate read APIs; quiesce their writers before taking a logically consistent migration snapshot. Do not copy live `.preferences_pb` files as a migration implementation. Treat cancellation and IO failure separately.
- Resolve legacy source/movie/episode/variant identity **only** with verified exact source context and explicit evidence; never use bare numeric IDs, list indices, titles, URI guesswork, or nearest timestamp as a canonical identity. A failed or ambiguous match must remain preserved and must block handoff when it would make existing user-visible records disappear from the active UX. Do not fabricate per-episode watch histories from one old watch point.
- Handoff is **single writer**: before activation all new user writes continue on legacy; after durable activation in a test-authorized profile all relevant writes go only to Room. No dual writes, best-effort mirroring or silent rollback to old data after Room acceptance. Read/write ownership must be chosen consistently across Activity recreation and process relaunch.
- Preserve unusual but valid contract values (including negative timestamps, explicit seek-to-zero, unknown duration, nullable identifiers where declared, conflicting field evidence), and exact old raw values in staging. Existing persisted session generation and operation sequence—not timestamps or largest position—arbitrate new checkpoints.
- Default UI, navigation, subscriptions, sources, filter semantics, graphics, icons, diagnostics and remote-control mappings stay unchanged this round. All user-facing new failure strings, when needed for Debug probe, are Simplified Chinese. No unrelated refactor or dependency upgrade.

## Review Focus — each requires an actual test

1. A crash/cancellation at snapshot, staging, verification or activation must leave exactly one recoverable writer and all old records intact (R07-2/R07-3/R07-5).
2. Unresolved origin, bare IDs, index-only episodes, malformed entries or variant timebase mismatch must not cause fake canonical links or invisible old progress after handoff (R07-2/R07-3/R07-5).
3. Once Room becomes active and accepts a newer checkpoint, a restart, missing gate or storage error must not silently select stale legacy writes (R07-2/R07-4/R07-5).
4. Importing identical snapshots repeatedly, changing one legacy field, and acknowledgement loss after commit must be distinguishable and must not duplicate or lose records (R07-3/R07-5).
5. Gate-off normal Debug/Release, another Android emulator, corrupted legacy data, missing user consent or a non-quiescent writer must never trigger activation or data modification (R07-1/R07-4/R07-5).

---

## R07-1 — Verify Round 06, map actual ownership and define test gate

**Files:** Inspect existing `app/build.gradle.kts`, `feature/player/PlayerViewModel.kt`, runtime probe classes, old `data/settings/PlaybackProgress.kt`, `DataStoreBrowseStore.kt`, `DataStoreFilterStore.kt`, `CinemaSettings.kt`, `data/local/`, `data/userdata/`, DI and prior native tests. Create/modify only a narrowly scoped handoff gate and focused tests, with actual filenames decided after reading the branch. Add the gate matrix to `docs/architecture/tv-legacy-handoff.md`.

**Interfaces:** Consumes the existing `PlaybackProgressStore` candidate, `LegacySnapshotReader`, staging importer and legacy storage APIs. Produces an explicit test-only `HandoffAuthorization` / `HandoffEligibility` decision. Final type names and exact signatures must agree with Round 01–06 source; never introduce duplicate domain types.

- [ ] **Step 1 — Baseline.** Verify report/head/diff and exact implementation signatures; capture existing Gradle and JVM native-test baseline evidence. Map all current reads/writes of progress, browse, filters and source settings and identify whether any hidden write bypasses the intended gate. Document ownership map.
- [ ] **Step 2 — RED: permission matrix.** Add tests for normal Debug OFF, Release OFF, mismatched test APK, missing private test token, dirty/unknown profile, unauthorized AVD, conflicting active writer, and clean test-owned profile. Assert all unauthorized paths remain legacy with no Room migration side effects.
- [ ] **Step 3 — Minimal gate.** Implement a fail-closed, default-off gate on top of the existing Debug probe authorization. If no fresh Gradle property is required, keep the existing build script unchanged. Re-run focused tests to GREEN and record the final gate truth table.
- [ ] **Step 4 — Checkpoint.** Commit the test gate and its tests locally only if actual targeted tests pass; record any runtime discrepancy, no production activation.

**Expected:** A verified explicit, test-owned rehearsal gate exists without enabling it in ordinary Debug/Release or weakening Round 06 safeguards.

## R07-2 — Persistent migration state and exclusive writer selection

**Files (candidate names, confirm against actual repo):** Create `data/userdata/LegacyHandoffState.kt`, `data/userdata/LegacyHandoffController.kt`; use a narrow DAO/table under `data/local/` **only when no existing persistent marker supports the contract**. Extend the existing runtime adapter only behind the test gate. Tests: `LegacyHandoffStateTest` plus Android database tests.

**Interfaces:** `readState(profileKey): HandoffState`, `prepare(snapshot, authority): HandoffDecision`, `activate(verifiedBatchId, authority): HandoffDecision`, and `writerFor(profile): Legacy | Room | Blocked`. These names are *proposed*; adapt to actual existing interfaces and lock the exact public signatures in `docs/architecture/tv-legacy-handoff.md` before implementation. Do not let UI or callers change writers by setting an ordinary preference.

- [ ] **Step 1 — RED: durable state machine.** Test `LEGACY_ACTIVE → STAGED → VERIFIED → ROOM_ACTIVE` with test-only authorization, including absent marker, cancelled stage, invalid batch, conflicting digest, and ambiguous legacy identity. Staging does **not** itself change writer.
- [ ] **Step 2 — RED: crash windows.** Simulate interruption before snapshot, after staged commit, after validation but before activation, and immediately after activation. Before activation select legacy after restart. After committed activation select Room after restart; never silently select legacy after a newer Room checkpoint.
- [ ] **Step 3 — Minimal state implementation.** Quiesce legacy writers before capture/activation, place the decisive activation marker and integrity metadata in the same Room transaction as verified readiness (or build a formally equivalent crash-safe boundary). Do not claim atomicity across two independent storage engines. Report an explicit `Blocked`/repairable error rather than silently reverting if an activated DB is unavailable.
- [ ] **Step 4 — Schema gate.** Reuse v2 if sufficient. If persisted new fields require v3, preserve schema `1.json` and `2.json` byte-for-byte, add explicit v2→v3 migration and exported `3.json`, test v1→v2→v3 with seeded favorites, browse and progress records, and no destructive fallback. Do not modify previous schema snapshots.
- [ ] **Step 5 — GREEN and local commit.** Run deterministic state tests, real file-backed reopen tests and migration tests before committing. Explicitly distinguish committed marker from an in-memory state variable.

**Expected:** Writer authority is persistent, unambiguous, restart-safe and reversible before activation—but **not silently reversible after** activation.

## R07-3 — Actual legacy preference APIs → preservation staging → audited readiness

**Files:** Extend the Round 02 legacy snapshot/import classes minimally, add a source/evidence resolver only for exact fixture-provided keys (not generalized T05 fuzzy matching). Tests: `LegacyHandoffImportTest` and `LegacyPreferencesImportInstrumentedTest`.

**Interfaces:** Consumes the *actual* existing legacy DataStore accessor methods and `LegacySnapshotReader`/importer from Round 02. Produces an immutable `LegacyHandoffAudit` with `sourceFingerprint`, `batchDigest`, field counts, unresolved/conflicted identities and activation eligibility, persisted where needed. Distinguish exact-verified association from unresolved staging and keep original raw values.

- [ ] **Step 1 — RED: old data reading.** On a disposable test profile, create nonempty old progress, browse, filter and saved source values through the existing production legacy API, not by constructing only an in-memory JSON map. Read using those same file owners. Verify all typed fields survive capture, including non-ASCII text and malformed/unresolved records placed via a separately authorized synthetic fixture mechanism.
- [ ] **Step 2 — RED: conservative identity.** With a synthetic, explicitly versioned source catalog, test one exact source+movie+episode+variant mapping. Test bare-id collision across two sources, missing origin, lineup reorder, same title/different year, missing/changed episode/version, and conflicting source. Unresolved entries stay staged, and *handoff eligibility is denied when visibility would be lost*.
- [ ] **Step 3 — RED: deterministic idempotency.** Identical values captured at different times and in varying iteration order have the same digest and do not re-import. One changed actual legacy value has a changed digest. Interrupted transactions roll back, cancellation propagates, and lost acknowledgement followed by same-batch retry does not duplicate rows. Old files and user data are not modified.
- [ ] **Step 4 — Minimal importer/readiness.** Reuse the existing preserved typed-value staging and record accounting, add only missing read/audit/verified-link code. Preserve partial errors as errors instead of treating them as empty. No live network matching, no automatic source subscription or external metadata lookup.
- [ ] **Step 5 — GREEN.** Run host tests and actual Android file-backed legacy-to-Room import tests, audit old DataStore values before/after and reopen Room to assert durable snapshots. Commit only verified incremental changes.

**Expected:** Real old storage APIs feed an auditable import; any unresolvable user-visible record remains protected and does not silently disappear behind a new writer.

## R07-4 — Controlled Debug profile switch and single-writer Media3 smoke path

**Files:** Reuse the Round 06 Media3 event bridge/runtime probe and existing DI construction; change only authorized Debug startup/selection seams and test classes. Do not add release startup migration.

**Interfaces:** Reuses `HandoffDecision`, Room checkpoint coordinator, legacy/Room writer adapters. A single writer decision is made per durable profile state, and the active store owns both reads and writes for that state.

- [ ] **Step 1 — RED: writer exclusivity.** Simulate before-activation legacy writes and after-activation Room writes; assert *zero* writes to the inactive store in both phases. Check no background saver, onStop, cancellation handler or delayed lifecycle callback writes to the old store after authority changes. If quiescence cannot be proven, block activation; do not assume `cancel()` means IO has stopped.
- [ ] **Step 2 — RED: migration coherence.** Restore one exact-matched legacy episode at its last effective position, paused. Write a newer Room checkpoint. Restart and ensure it still selects Room with that newer progress, not old DataStore. Missing authorization on an already activated test profile fails closed visibly rather than silently reopening stale legacy for writes.
- [ ] **Step 3 — Probe-only integration.** Add minimal routing to select the single current store in the authorized Debug test profile. Keep normal Debug/Release, regular playback key mapping, and ordinary end-user screens unchanged. Preserve all existing `PlayerViewModel` legacy fallback tests.
- [ ] **Step 4 — GREEN.** Run actual Media3 local WAV tests plus file-backed Room reopen/legacy unchanged assertions in instrumentation; verify no duplicate writes, process relaunch paused, no network calls and no test-only behavior escapes the gate.

**Expected:** A test-owned profile alone demonstrates a **real single-writer** handoff; the actual production app still never activates Room.

## R07-5 — Native fault tests, full regression, independent review and local evidence

**Files:** New Android tests under existing `app/src/androidTest/kotlin/.../data/userdata/` and player integration tests under existing conventions. Docs: `docs/verification/2026-10-24-round-07-legacy-handoff.md`, `docs/architecture/tv-legacy-handoff.md` and existing `docs/superpowers/progress/2026-10-23-tv-rebuild.md`.

**Interfaces:** Verify all prior interfaces. Use the actual test harness from Round 06, not a second unrelated test environment.

- [ ] **Step 1 — Target preflight.** Prove the disposable AVD is owned by this test run, its explicit serial, boot identity, `ro.kernel.qemu=1`, API=30, ARM64, test-only app package and no personal data. Check before **each** install/run. All mutating adb commands use `-s <VERIFIED_SERIAL>`; do not broadcast Gradle instrumentation to other devices. Recreate a fresh AVD only if existing stopped one cannot be proven clean. No physical TV.
- [ ] **Step 2 — Native failure matrix.** Run real tests for interrupted capture, transaction rollback, cancellation, acknowledged/unknown commit retry, corrupt entries, unresolved identity refusal, file IO/storage-full fault injection, legacy writer quiescence, post-activation DB failure fail-closed, and replay after process restart. Fault injection must not fill developer disk or modify real data.
- [ ] **Step 3 — Compare bytes and invariants.** Hash fixture legacy store files at valid quiescence points before/after rehearsal; verify they are not modified *by the migration*. Keep test setup writes separate from migration-side measurements. Verify original Room v1/v2 schema hashes if present, new schema lineage if introduced, exact repeatable counts, per-episode checkpoints, resume pointer and favorite/browse records.
- [ ] **Step 4 — Full regression OFF by default.** Run existing Python suite, `:app:testDebugUnitTest`, debug/unsigned-release/AndroidTest build, lint, all available existing Android tests on the pinned emulator. Turn rehearsal and ARM64 flags off for ordinary builds and verify package/version/ABI/signing freeze/normal player behavior. Check illegal Release/bundle requests are refused before performing actions. Record test counts by *executed method* (pass/fail/skip/not-run), not compiled class count.
- [ ] **Step 5 — Review and archive.** Stage intended files, run `python3 scripts/check_repository_security.py` (index-only scope noted), inspect staged and unstaged diff and generated schema. Request independent read-only review if available focused on writer exclusivity, crash windows, correct old ID handling, cancellation and old DataStore preservation. Fix Important defects with actual RED→GREEN proof and rerun relevant full suites; report whether the reviewer rechecked. Commit locally, capture hashes, branch, HEAD, commands, test serial, warnings, independent review status, and update ledger **only for tested slices**.

**Expected:** Round 07 reports real isolated migration rehearsal with explicit remaining gates and never claims ordinary user data migration or production transition tested.

## Acceptance and stop conditions

**Allowed PASS statements:** Test-owned, fully resolvable synthetic legacy profiles migrate through the actual existing DataStore APIs and persist into Room; the authorized Debug test profile selects one writer, survives app process restart, and preserves legacy files; specific native tests were executed and passed on the verified AVD.

**Blocking outcomes:** No safe emulator / missing toolchain; inability to quiesce old writers; unverifiable source or episode identity; ambiguous corruption; transactional activation fault; unauthorized gate; native tests not actually executed; unexpected changes to ordinary Debug/Release. In each case stop unsafe activation, preserve records, report exact blocking evidence and successful independent parts. Do **not** weaken the gate or invent data to obtain a PASS.

**Never claim:** All T03/T04/M02 or A05–A11/A20 accepted; real user's old data restored; ARMv7/TV validated; arbitrary power-loss recovery; product UI redesigned; automatic source subscriptions implemented; production Room enabled; APK signed or published.

**Source of truth:** Actual unpushed Round 06 code in the user's isolated worktree; the attached plan cannot substitute for inspecting it. If it contradicts the current source, document the discrepancy and narrow scope rather than guessing implementations.
