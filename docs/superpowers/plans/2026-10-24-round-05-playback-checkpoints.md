# Round 05 Reliable Playback Checkpoint Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or superpowers:subagent-driven-development, with superpowers:test-driven-development per task, superpowers:systematic-debugging for failures, and verification-before-completion before reporting success. Work through this plan one task at a time. Stop at the Round 05 boundary.

**Goal:** Implement and actually test the T04 *candidate* per-episode checkpoint and persistent stale-write arbitration, without activating it for current users before safe legacy migration and production-path transition are approved.

**Architecture:** Adapt the existing Round 01 `PlaybackSnapshot` and `PlaybackProgressStore.commit` contract to an event-driven coordinator, an injected monotonic elapsed-time source, and an atomic Room-backed candidate store. Persist independent episode/version positions plus one work-level current-resume reference, with a durable session generation and per-session operation sequence to reject old asynchronous completions. Test policies with deterministic JVM clocks and persistence/transactions on a positively identified disposable ARM64 emulator. Keep existing DataStore/`PlayerViewModel` runtime behavior unchanged.

**Tech Stack:** Existing Kotlin, coroutines, Media3 (unchanged production integration), Room 2.8.5, KSP 2.3.12, AGP 9.4.1, Gradle 9.8.1, AndroidX Test, existing disabled-by-default `cinema.storageTestArm64` test pair. Do not upgrade these just for this round.

**Spec:** `docs/superpowers/plans/2026-10-23-tv-experience-rebuild.md` §§5.3, 5.4, 6, 11, 12, T04 and A06–A09; `docs/architecture/tv-data-contracts.md`, `docs/architecture/tv-storage-contracts.md`, Round 01–04 verification reports, existing Room schema v1, and `docs/superpowers/progress/2026-10-23-tv-rebuild.md`. The master spec wins over this plan if a field's semantics conflict.

**Reported checkpoint — verify locally, not accepted as freshly re-run:** `codex/contract-foundation` isolated worktree; HEAD `a46a8a97947d1b72fefd3361f9742df9540a5be6`; `main` at `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`; Round 04 instrumentation 34/34, JVM 124/124, Python 62/62, 135 Gradle tasks. Existing Room candidate is not wired into production; no real legacy-user migration was accepted. Round 04 ran the real storage tests on a positively identified disposable API 30 ARM64 AVD with a dedicated debug/test build pair, not on an ARMv7 television. These figures are handoff reports rather than independently measured in this plan.

## Global Constraints

- Remain in the **existing** `codex/contract-foundation` worktree after confirming actual branch/HEAD and clean or independently preservable modifications. Do not create another worktree, reset, discard, cherry-pick from main, force push, merge, publish, sign, or manipulate production Release assets.
- Preserve production identity `com.ichichuang.cinema`, version `0.9.8`/code `18`, `armeabi-v7a` ABI, min/target/compile SDK, signing freeze and old source compatibility routes; retain regular Debug `.debug` identity and ABI with the ARM64 test property OFF. The ARM64 override is only for the exact guarded test pair, never for Release/bundle/publish tasks.
- **Do not activate** candidate Room/playback storage in Koin, Application, navigation, or production `PlayerViewModel`; do not switch legacy DataStore readers/writers, start a dual-write path, perform real legacy migration, edit real user settings, access signing secrets, change live subscription data, or add UI/recommendation/demo content. T04 runtime wiring remains **not accepted** at the end of this round.
- Use the **actual committed** Round 01 model and interfaces; the coordinator has no copy of the unpushed sources. Read the concrete signatures, current Room DAO/entity, schema and tests before declaring new types or changing storage. Do not invent a second `PlaybackSnapshot`, `CommitResult` or playback repository interface.
- Preserve unknown duration (`null`) versus genuine known duration; never convert Media3 `C.TIME_UNSET` into a persisted duration of `0`. Nonnegative progress greater than stale reported duration is permitted by the existing domain contract; no automatic clamping. Never compare playback position to decide which write is newer.
- `capturedAt` can be a legitimate negative timestamp and **is not** a durable operation ordering key. `sessionId` is opaque and **is not** lexicographically sortable. Persisted, transactionally minted generations/revisions and explicit operation sequence decide arbitration. `SystemClock.elapsedRealtime` (or equivalent monotonic source) is only for scheduling checkpoints within a running process, never a cross-process ordering marker.
- A delayed callback from an old session must not overwrite current state; however, an old episode's successfully committed final checkpoint remains part of that episode's history. Separately protect per-episode progress and the work-level current-resume pointer. Do not suppress a legitimate user seek backward because its position decreases.
- Keep user-data tables independent from disposable cache. No destructive Room fallback. If schema v1 cannot express the arbitration invariant, extend to version 2 by a tested **v1 → v2** migration, preserve the existing checked-in `1.json`, and export and commit actual `2.json`. Do not synthesize a fake v1 or simply bump the version and recreate tables.
- Instrumentation only on a verified test-owned disposable emulator with explicit serial and matching ABI; reuse a known disposable profile only if still provably owned/isolated. No unqualified `adb install`, `adb shell`, `connectedDebugAndroidTest` or `pm clear`. Nothing runs on physical TV, phone, personal emulator, real production or existing user data.
- Synthetic test inputs only, without live sources, media URLs that access the internet, or fabricated production records. All newly added user-facing text and status terms use Simplified Chinese; technical identifiers/test class names remain code-native English.
- Only `Committed` confirms durable storage; `RejectedStale` is not success, `Failed` carries actionable failure, cancellation propagates rather than converting to failure. Do not use detached GlobalScope jobs, swallow errors with `runCatching`, or claim actual flushing from a ViewModel that has already been destroyed.

## Review Focus — five regressions most likely to harm users

1. **Seek from 20:00 to 10:00:** after >=5 seconds of actual elapsed playback, the stored position is near 10:00, not the previously larger position; test with a fake monotonic clock.
2. **Stale asynchronous work:** callbacks from the old episode/session or a lower/equal operation sequence must not replace a newer same-record commit or a newer work-level resume pointer; verify persistently after close/reopen.
3. **Initialization and retry:** an unprepared or buffering player reporting zero position/unknown duration must not clobber a valid previously committed checkpoint; a retry preserves the last valid resume position.
4. **Episode boundary:** an actual switch saves the old episode first and preserves its own progress; preload does not mark a new episode watched or take over work-level continuation.
5. **Schema and failure handling:** an app using real v1 data migrates to v2 (if schema changes) with preserved rows; SQL/write failure and cancellation do not produce `Committed` or partial state; the ordinary Release remains on its unchanged ABI and signing boundary.

---

## R05-1: Confirm local starting state and map existing interfaces

**Files:** Read `app/src/main/kotlin/com/ichichuang/cinema/domain/**`, `data/local/**`, `data/userdata/**`, `data/settings/PlaybackProgress.kt`, `feature/player/PlayerViewModel.kt`, existing JVM/Android tests, `app/build.gradle.kts`, schema v1 and Round 01–04 reports. Create/update `docs/architecture/tv-playback-checkpoints.md` with an interface and event-flow table. Do not edit production PlayerViewModel yet.

**Interfaces:** Consumes actual `PlaybackSnapshot` / `PlaybackProgressStore.commit(snapshot): CommitResult`, current database/DAO and player callbacks; produces a recorded, source-grounded mapping to one candidate checkpoint coordinator/store and the required database changes.

- [ ] **Step 1 — Verify local reality.** Record `git rev-parse HEAD`, branch, `git worktree list --porcelain`, `git status --porcelain=v1`, `git diff --cached`, `git diff`, `git log -6 --oneline`; compare to reported `a46a8a9` without resetting differences. Verify normal Debug/Release and ARM64 test property safety rules are still present. Stop on unresolved unrelated modifications rather than deleting them.
- [ ] **Step 2 — Read actual contracts and storage mapping.** Record exact source paths, type and method signatures, checked-in schema version/table names and how existing saved state and Room candidate represent `workId`, `episodeId`, `variantId`, `sessionId`, `operationSequence`, `positionMs`, `durationMs`, `watchState`, `capturedAt`. Determine whether tables already store per-episode and work resume identities. Do not assume file or class names based on the report.
- [ ] **Step 3 — Identify current player behaviors without changing them.** Inspect the existing heartbeat, pause, seek, episode/line switch, retry, end-of-media callbacks and `onCleared`/lifecycle path; list exactly which errors the new candidate will prevent after eventual activation. Identify Media3 `STATE_IDLE`, `BUFFERING`, `READY`, `ENDED`, `TIME_UNSET`, and true `isPlaying` behavior for future integration. Trace potential double callbacks and write ordering.
- [ ] **Step 4 — Record one deliberate implementation map.** Separate a pure event policy/coordinator, monotonic time abstraction, transactional persistence adapter and isolated test-only Media3 event translation if needed. No new duplicate domain model or mock-as-production storage. Document ambiguous policy decisions explicitly and connect each to a test before implementation.

**Expected:** A concise contract and file map anchored in actual Round 04 sources. No runtime DataStore or player changes.

## R05-2: Pure checkpoint policy with deterministic time and ordering tests

**Files:** Create focused files under existing `app/src/main/kotlin/com/ichichuang/cinema/data/userdata/` (e.g. `PlaybackCheckpointCoordinator.kt`, `PlaybackClock.kt` only if existing equivalents do not exist); add `app/src/test/kotlin/com/ichichuang/cinema/data/userdata/PlaybackCheckpointTest.kt` and, if independently useful, `PlaybackSessionOrderTest.kt`. Choose final names from actual package structure and document any deviation.

**Interfaces:** Consumes actual `PlaybackSnapshot` and `CommitResult`, actual `PlaybackProgressStore`; produces a candidate coordinator driven by immutable input/events with an injected monotonic clock. Store owns persistent arbitration, coordinator owns event ordering and scheduling.

- [ ] **Step 1 — RED: seek-back test.** Add `seek_backwards_still_saves_on_clock`: seed playback position 1,200,000ms, seek to 600,000ms, advance fake monotonic time >=5,000ms of active playback; expect a new snapshot near 600,000ms to be sent/committed independently of the position delta. Run the targeted JVM test and observe a behavioral failure (not mere missing SDK/toolchain).
- [ ] **Step 2 — GREEN: monotonic interval policy.** Implement a minimal time-source interface such as `fun elapsedRealtimeMs(): Long`, with fake clock in JVM tests; do not persist clock values. Only count time in a genuinely playing state; buffering and pause do not count as active playback. Normal interval initially 5,000ms, unless actual master spec says otherwise. Verify RED → GREEN.
- [ ] **Step 3 — RED: initialization/retry regression.** Add `loading_zero_keeps_resume_point` and `retry_preserves_last_valid_resume_point`: an unprepared/loading/retry event reporting `0` or unknown duration does not issue a destructive checkpoint, and resume of the same version starts from the last valid durable progress. Add explicit user “from start” event to allow intentionally seeking to 0; do not make all zeros illegal. Confirm RED.
- [ ] **Step 4 — GREEN: state-specific persistence events.** Only accept a valid position on documented player-ready/seek/verified-play events. Treat `TIME_UNSET` as nullable duration, keep valid positive position even when larger than stale duration, and record that this candidate is not yet wired to Media3. Re-run tests.
- [ ] **Step 5 — RED: episode and preload regressions.** Add `save_before_episode_switch`, `preload_does_not_mark_watched`, `switch_source_keeps_episode_only_when_identity_matches`: a switch captures old episode before selecting new; a preload cannot become a watch/history/resume pointer; an unverified variant/timebase change must not silently map the old position. Confirm RED.
- [ ] **Step 6 — GREEN: controlled transition policy.** Serialize switch intent and save barrier; on a failed commit preserve last known valid state and return a visible failed result to the caller rather than asserting success. Record per-episode watch state only after genuinely qualifying user viewing; do not use mere prepare or detail browse. Design work-resume promotion so an accidental instant exit does not overwrite the previously active target; if choosing an initial active-play threshold (e.g. 5s), document it as a reviewable project choice and test exact boundaries. Confirm GREEN.
- [ ] **Step 7 — RED/GREEN: completion is not a reset.** Test an episode finishing, a last-known episode and unknown series completion: completion marks only what was actually watched, does not zero all episode progress, and does not label an unknown series finished. Align existing watch-state enum without inventing a new production label. Test rejection of negative progress/operation sequences where domain already requires it.

**Expected:** JVM tests prove time independence, legitimate backwards seek, missing-duration handling, source/episode boundaries and preload non-effects. A pure fake-store test is **not** evidence of durable stale-write rejection.

## R05-3: Real Room-backed atomic commit and durable stale-write arbitration

**Files:** Extend the existing candidate `data/local/` Room DAO/entities and `data/userdata/` adapter(s) only where necessary. Update the existing `CinemaDatabase` version only if needed for durable session generations, per-episode rows or work-resume pointer. Add instrumented `PlaybackCheckpointNativeTest.kt` and conditional `PlaybackSchemaMigrationTest.kt` in the current `app/src/androidTest` package. No code in existing active `PlayerViewModel` / legacy `PlaybackProgress`.

**Interfaces:** Implement the **existing** `suspend PlaybackProgressStore.commit(snapshot: PlaybackSnapshot): CommitResult` with one durable transaction: protect per-episode/version progress and, independently, a work-level current-resume target. Persisted generation/operation sequence must reject delayed asynchronous older writes even across database reopen/process recreation.

- [ ] **Step 1 — Assert failing native semantics first.** Test `old_session_cannot_overwrite_new_episode`, `old_sequence_cannot_overwrite_newer_same_episode`, `duplicate_same_snapshot_is_idempotent`, `same_sequence_different_payload_is_rejected`, `separate_episodes_keep_separate_progress`, `old_episode_callback_cannot_replace_resume_pointer`, and `resume_after_database_reopen_is_paused`. Exercise a real file-backed Room instance. Tests must first fail for the missing implementation/behavior, not merely fail to compile due to mismatched imports.
- [ ] **Step 2 — Define durable session arbitration.** Allocate a monotonic generation/revision for a new session **within a database transaction**; opaque `sessionId`, `capturedAt`, random values or current media position are not substitutes. A snapshot belongs to its allocated persistent generation and carries monotonic `operationSequence` within that session. Atomic predicate compares generation then operation sequence; exact duplicates return a documented idempotent outcome, conflicts are stale/rejected. Do not allow a newly recreated ViewModel to reset a durable generation floor. Protect work-level continuation separately, and do not let unqualified preloads advance it.
- [ ] **Step 3 — Implement minimal Room persistence.** Use the actual Round 01 `CommitResult` variants. `Committed` only after transaction commit; `RejectedStale` on stale/conflicting operations without overwriting; `Failed` only for true storage/IO errors, preserving evidence safely; **rethrow** `CancellationException`. Do not silently catch all exceptions or write a second update after the commit. Verify targeted instrumented red-to-green checks.
- [ ] **Step 4 — Schema evolution gate.** If a v1 change is required, add version 2 and a proper v1→v2 migration, export/commit generated `2.json`, keep **original `1.json` byte-identical**. Use actual old v1 schema with seeded browse/favorites/playback/staging records; migrate and assert every existing field/row survives with no drop or fabricated values. Add migration to the shared test-only Room factory. No `fallbackToDestructiveMigration`. If v1 already supports all invariants, leave version=1 and document why.
- [ ] **Step 5 — Cover failure/ordering evidence.** Test concurrent/out-of-order commit tasks using a controlled scheduler, pause/background boundary, captured old episode before a switch, thrown persistence error/rollback, cancellation propagation and acknowledgement-loss retry. Assert real database contents after close/reopen. Test negative `capturedAt` and duration null, large nonnegative position, duplicate commit idempotence, cache clear isolation and old-import staging survival. Only actually executed instrumented assertions may be called native-verified.

**Expected:** No stale progress or resume-pointer overwrite in real Room, correct per-episode persistence, explicit commit outcome, versioned migration if needed, no app/runtime activation.

## R05-4: Narrow event mapping proof and invariant audit

**Files:** `docs/architecture/tv-playback-checkpoints.md`, relevant JVM/instrumentation tests only. If a small unbound Media3 event adapter is required for a verifiable test boundary, keep it separate from the current player and do not change DI or player navigation.

**Interfaces:** Consumes actual player event meanings and the new coordinator/store; produces a documented future integration table and a test proving startup/loading/error vs actual playback events are differentiated.

- [ ] **Step 1 — Verify event semantics.** Read official Media3 references and the actual player callbacks. In particular, `onIsPlayingChanged(false)` can mean buffering/ended/error, not just user pause; `getDuration()` can be `C.TIME_UNSET`; `seekTo()` triggers a position discontinuity. Do not blindly turn every callback into an unconditional checkpoint.
- [ ] **Step 2 — Test mapping decisions with a deterministic event source.** Explicitly separate metadata/preload/prepare from actual user viewing; seek/pause/episode switch capture user-intended values. Never auto-play after reading a snapshot during eventual process recovery.
- [ ] **Step 3 — Record unresolved integration gates.** Identify exactly what T03 genuine legacy-user migration, T05 stable work association and T09 UI/Media3 live integration must supply before a production path replacement is safe. If identity mapping is currently unavailable, preserve raw legacy records and do not activate.

**Expected:** Source-grounded event mapping and a small, independently testable candidate boundary without rewriting the production player or claiming Media3 runtime verification.

## R05-5: Real verification, review, local commits and stop

**Files:** `docs/verification/2026-10-24-round-05-playback-checkpoints.md`, `docs/architecture/tv-playback-checkpoints.md`, existing progress ledger, intended source/tests/schema as needed. Store raw sanitized instrumentation logs and APK hashes under ignored `.local/evidence/round-05/`; do not commit binary evidence or personally identifying paths.

**Interfaces:** Consumes changed behavior and all actual output; produces precise test evidence, a clean isolated-branch local commit history and correctly bounded progress accounting.

- [ ] **Step 1 — Run JVM/Python red-green and regression.** Record the targeted new `PlaybackCheckpointTest` / ordering cases, then all `:app:testDebugUnitTest`, and `python3 -m unittest discover -s scripts/tests -v`, with actual counts and exit codes. Failures are not fixed by removing or weakening tests.
- [ ] **Step 2 — Native test isolation.** Verify dedicated AVD serial, AVD name, `ro.kernel.qemu=1`, API 30 (or documented owned alternative), ABI, and boot state before **every install/run**. Build existing guarded ARM64 Debug and DebugAndroidTest pair using `-Pcinema.storageTestArm64=true`; inspect manifest/runner and JNI ABI. Install only on pinned serial and execute the actual runner with method-by-method pass/fail/skip/not-run counts. Include all existing 34 Round 04 storage cases plus new Round 05 native cases; do not claim counts based on compilation. Shut down only the test-owned emulator after evidence capture.
- [ ] **Step 3 — Standard product and security regression.** With test flag **off**, run `:app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease` using `--no-daemon --no-configuration-cache` and applicable repo checks. Run the existing staged-index security script after staging only intended files. Inspect both cached and unstaged diffs. Confirm original `armeabi-v7a` packaging, Debug suffix, unsigned Release, signing freeze, version, manifests and code paths unchanged at runtime; compile/bundle path must not enable ARM64 test override in Release.
- [ ] **Step 4 — Independent read-only review.** Ask review of transaction/CAS arbitration, file-backed durability, migration safety, cancellation, exact known/unknown values, event semantics, test strength and scope boundaries. Address important findings with focused new failing tests followed by minimal fixes and reruns; describe if a second independent confirmation was not performed.
- [ ] **Step 5 — Local commits and truthful report.** Commit coherent changes locally only. Record starting/final HEAD, commit hashes, files, schema version and SHA, actual JVM/native/Python counts, elapsed-time tests, normal-vs-test build artifact evidence, limitations, incident fixes and review scope. Mark only *verified* T04 candidate checkpoint portion; do **not** mark all T04, T03, M02, A06–A09 or production upgrade accepted. Stop before any legacy migration activation, app DI wiring, production PlayerViewModel edits, UI/remote changes, Git push/merge or APK Release.

**Expected:** Candidate checkpoint coordinator and real Room arbitration with executed JVM and instrumented evidence; any unmet native/migration tests reported as blocked/failing. Ordinary app continues to use legacy DataStore and unchanged player logic.

## Evidence and completion language

| Evidence | Permitted claim | Forbidden claim |
| --- | --- | --- |
| New JVM fake-clock tests pass | Checkpoint policy behaves as tested in JVM | Data is durable on Android hardware |
| New Room integration tests compile only | Native test implementation is ready | Native commit/transaction behavior passed |
| Old 34 and all new Android methods actually execute/pass | Candidate persistence and stale-write arbitration passed on named AVD | Old ARMv7 TV, production migration, or physical playback verified |
| Room v1→v2 migration tested using original schema and seeded rows | Synthetic v1 schema migration passed on named AVD | Real old DataStore/user migration or production rollback tested |
| No production player/DI changes | Candidate remains isolated | The existing player's reported seek/retry bugs are fixed |

## References

- Media3 event and playback state: https://developer.android.com/media/media3/exoplayer/listening-to-player-events
- Media3 `Player.getDuration()` unknown duration: https://developer.android.com/reference/androidx/media3/common/Player
- Room exported schemas and migration tests (use the actual installed 2.8.5 API, not samples for newer `room3`): https://developer.android.com/training/data-storage/room/migrating-db-versions
- Room 2.x `MigrationTestHelper` API: https://developer.android.com/reference/kotlin/androidx/room/testing/MigrationTestHelper
- Kotlin coroutine cancellation guidance: https://developer.android.com/kotlin/coroutines/coroutines-best-practices
