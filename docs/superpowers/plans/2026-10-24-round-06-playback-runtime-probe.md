# Round 06 — Isolated Debug Playback Runtime Integration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Follow applicable `superpowers:executing-plans` or `superpowers:subagent-driven-development`, with `superpowers:test-driven-development` and `verification-before-completion`. Implement this plan task by task, recording RED → GREEN results; stop at the Round 06 boundary.

**Goal:** Connect the Round 05 checkpoint coordinator and Room v2 store to real Media3 playback events in one explicitly opted-in, disposable **Debug-only** runtime path, and demonstrate save, resume, switching and lifecycle behavior without changing the ordinary Debug or Release persistence path.

**Architecture:** Introduce one narrow persistence/event bridge at the existing `PlayerViewModel` boundary. The default existing DataStore writer remains unchanged; a build-guarded Debug probe uses the actual Round 05 Room store and checkpoint coordinator exclusively, with no parallel writes. Verify policy using deterministic JVM tests, Android instrumentation with a local generated media fixture, and a two-process replay on a positively identified disposable emulator. Real legacy migration and release activation are out of scope.

**Tech Stack:** Existing Kotlin, coroutines, Compose/TV Material, Media3 ExoPlayer, Koin, DataStore; Round 05 Room 2.8.5 / KSP 2.3.12 and Room schema v2; existing Gradle, JDK, SDK, AndroidX Test; existing default-off `cinema.storageTestArm64` paired test-only build configuration. No general dependency upgrade.

**Spec:** `docs/superpowers/plans/2026-10-23-tv-experience-rebuild.md` §§5.3–5.4, 6, 10–12, T03/T04/T09 and A06–A09; Round 01–05 plans and reports, `docs/architecture/tv-playback-checkpoints.md`, `docs/architecture/tv-storage-contracts.md`, and `docs/superpowers/progress/2026-10-23-tv-rebuild.md`.

**Reported starting point (verify locally):** existing isolated worktree `codex/contract-foundation`; HEAD `d75cb40622a5adc8d9680a198063d0a0fc01cec4`. User reports Round 05 JVM 149/149, Android native 61/61, Python 62/62, Room v1→v2 migration passed, no player runtime wiring; Round 05 independent review fixes were re-tested but not independently re-reviewed. These are user-supplied handoff facts, **not freshly verified in this execution plan**. Original `main` remains reported as `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`.

## Global Constraints

- **Stay in the existing isolated worktree.** Inspect actual HEAD, branch, worktrees, staged/unstaged diffs and relevant existing classes/tests before editing. Preserve drift rather than resetting, cherry-picking, fetching or discarding unrelated changes. Work locally, commit coherent verified slices; no push, merge, rebase, publish or tag movement.
- **Default paths unchanged.** Normal Debug with no extra property and ordinary unsigned Release use the same legacy DataStore read/write behavior and player UX as before. Do not silently activate Room or run both writers. Document any necessary plumbing refactor and prove the legacy behavior remains unchanged by focused tests.
- **Dedicated probe gate.** New property `cinema.playbackRoomProbe=true` is OFF by default, permitted only for the exact opted-in Debug / DebugAndroidTest probe build pair, and must fail closed for any Release, bundle, publish or signing task. This probe is also gated at runtime by a test-only authorization marker on a clean/disposable app profile; existing legacy Debug data must not be silently switched to an empty Room library. Verify gate-off and gate-on artifacts/configuration. If the current Gradle/plugin architecture requires a differently named but equivalent strongly isolated mechanism, document the smallest justified ruling and test it.
- **Don't change production identity or distribution.** Preserve application ID `com.ichichuang.cinema`, Debug suffix `.debug`, versionName `0.9.8` / versionCode `18`, `armeabi-v7a` ordinary ABI, SDK floors, JNI/manifest and frozen production signing. Existing `cinema.storageTestArm64=true` is only for the already guarded ARM64 test build pair. No signature material, Release, production APK install, or update metadata changes.
- **Never touch personal devices or real saved data.** Install and run only against a verified **test-owned disposable AVD** with explicit serial after checking `ro.kernel.qemu=1`, API/ABI, AVD ownership and boot identity before every install/run. All `adb` calls must use `-s <VERIFIED_SERIAL>` (or exact serial-specific Gradle device targeting); no unqualified `adb`, `pm clear`, `am force-stop`, installation or shell calls. Restrict any package stop/relaunch to the isolated Debug package on that AVD. No real video sources, credentials, personal account, physical TV or real user data.
- **Use existing domain types.** Read actual Round 05 types, store methods and schema first; do not duplicate `PlaybackSnapshot`, `CommitResult`, session identities, clock or Room arbitration. Preserve work/episode/variant identity distinctions and unresolved legacy mapping. If no trustworthy episode/variant key can be derived, fail closed for resume or keep records source-scoped, without inventing canonical association or mapping across different timelines.
- **No accidental normalization.** Keep unknown duration (`null` / `C.TIME_UNSET`) distinct from zero, legitimate nonnegative seek-to-zero and backward seeks valid, and position greater than a stale duration intact. `capturedAt` including legitimate negative values and opaque IDs are not ordering keys. Persisted generation/operation sequence controls staleness. A `Committed` result is a real committed transaction, not queueing a coroutine.
- **Do not introduce undocumented behavior changes.** Preserve production remote-control mapping, UI, navigation, catalog, subscription and automatic source selection. No original icon work, designs or broad refactors this round. Probe-specific restart defaults to paused until the user deliberately presses play; no automatic audio on restore.
- **Do not activate live legacy migration.** Legacy DataStore files remain sole owners for existing users; Round 02 preservation staging is not enough to associate old source IDs with stable works. If an existing Debug profile contains legacy watch records, probe mode refuses activation or selects unchanged legacy mode without resetting or hiding the old records. Record which rule is implemented and test it. No automatic migration marker, deletion or duplicate writes.
- Respect coroutine cancellation; no detached `GlobalScope`, silent `runCatching` cancellation, false success after ViewModel destruction, or blocking Room writes on the main/UI thread. Keep user-facing texts in Simplified Chinese.

## Review Focus — user-visible hazards that must have an owned test

1. An old playback callback or late save after episode/variant switch must not replace the newer episode or the work resume pointer (R06-2 and R06-3).
2. A user pause and explicit seek backward must commit the correct position; spontaneous buffering, idle/loading and failure must never persist an accidental zero (R06-2 and R06-3).
3. Deliberate seek-to-zero and rewatch must be allowed; final episode completion must not fabricate whole-series completion; preload cannot promote the next episode (R06-2).
4. Pressing Back or sending the test app to background must preserve the last successful checkpoint; a process relaunch must restore paused, without auto-start or wrong-origin playback (R06-3 and R06-4).
5. Probe OFF, incompatible tasks, dirty legacy profile, unknown media identity, cancellation or a storage failure must never silently switch writer, erase data, claim success or touch Release behavior (R06-1 and R06-4).

---

## R06-1: Verify starting state and establish the Debug-only gate

**Files:** Inspect `app/build.gradle.kts`, `app/src/main/kotlin/com/ichichuang/cinema/feature/player/PlayerViewModel.kt`, `PlayerScreen.kt`, `data/settings/PlaybackProgress.kt`, actual `data/userdata/` coordinator/store, `data/local/`, `di/`, and prior tests. Modify only necessary Gradle/runtime wiring; add focused build-gate tests or a verification script under existing test conventions. Add a gate map to `docs/architecture/tv-playback-runtime-probe.md`.

**Interface contract:** `Playback runtime mode` has exactly two mutually exclusive states: current Legacy (default) and explicit RoomProbe (test-only, opt-in). The existing runtime selection logic remains deterministic and has an observable test seam. Refuse activation when marker absent or older user data detected. No duplicate storage writer.

- [ ] **Step 1 — Confirm reported checkpoint.** Read worktree paths, Git status, HEAD and Round 05 reports; verify existing schema v2 and the 61 native test methods. Record drift without forcing the tree to match the report.
- [ ] **Step 2 — Design the smallest safe seam.** Document exact existing class/method signatures and old writer calls; identify Koin/provider wiring. Choose whether to inject an adapter/interface or a bridge with two explicitly selected implementations. Every legacy call must remain mapped one-to-one when probe OFF. Write this interface map before editing, not a second progress domain model.
- [ ] **Step 3 — RED: gate failure cases.** Add tests or reproducible build-command checks for `probe_off_keeps_legacy_writer`, `probe_flag_rejects_release_tasks`, `probe_requires_disposable_authorization`, `existing_debug_legacy_records_block_probe`, and `probe_never_dual_writes`. Confirm the implementation is missing/incorrect before adding it.
- [ ] **Step 4 — GREEN: implement only this gate.** Add strict Gradle property `cinema.playbackRoomProbe`; check debug task/target pairing and runtime marker. Ordinary tasks build with flag OFF. Add one explicit test-only marker provision step through the dedicated instrumentation test environment. Record attempted unsafe commands and actual nonzero rejection results without ever running a Release signing task.
- [ ] **Step 5 — Gate regression.** With both probe/ARM64 flags OFF, verify `:app:assembleDebug :app:assembleRelease :app:testDebugUnitTest` and assertions that only Legacy writer is instantiated in ordinary builds. Check package identity/version and default ABI. An added code path means byte-for-byte equality to Round 05 builds is **not required**; behavior and package settings are the criterion.

**Expected:** An actually testable opt-in path exists, with default-off and fail-closed behavior. No production path replacement or temporary database reset.

## R06-2: Integrate actual player events with Round 05 coordinator

**Files:** Minimal changes to `feature/player/PlayerViewModel.kt` and focused bridge/adapter files under `feature/player/` or `data/userdata/` consistent with current packaging; JVM tests under existing `app/src/test/kotlin/.../feature/player/` path. Do not change `PlayerScreen` unless needed to express a testable explicit lifecycle intent without changing ordinary UI or keys.

**Interface contract:** Uses the **existing** candidate coordinator and `PlaybackProgressStore`; source event → immutable checkpoint intent → outcome, with one coroutine ownership model. One active work/episode/variant/session; separate work resume pointer. The legacy adapter delegates to existing `PlaybackProgress` behavior in probe-OFF mode; it does not reimplement a new competing storage strategy.

- [ ] **Step 1 — RED: event mapping.** Test `actual_playing_seconds_advance_checkpoint_clock`, `buffering_false_is_not_user_pause`, `unknown_duration_does_not_persist_zero`, `seek_backwards_saves_new_position`, `retry_preserves_last_valid_checkpoint`, and `explicit_from_start_is_not_suppressed`. Provide deterministic event inputs and fake elapsed time, but use the real coordinator for the probe path. At least one test must demonstrate failure on pre-fix production logic, not merely assert candidate class behavior again.
- [ ] **Step 2 — Implement minimal Media3 translation.** Use `Player.isPlaying`, playback state, `onPositionDiscontinuity` (seek reason), explicit pause/back/episode/line actions, and actual listener callbacks as appropriate. `onIsPlayingChanged(false)` alone is **not** a pause event: it can also mean buffering/error/ended. Do not write from preloads, timeline changes, route arguments, or unprepared `position=0`. `C.TIME_UNSET` maps to absent duration. Preserve the known last-valid resume across `prepare`/retry.
- [ ] **Step 3 — RED: episode and identity boundaries.** Test `switch_waits_for_old_episode_checkpoint`, `late_old_session_cannot_steal_resume_pointer`, `preloaded_next_episode_does_not_count_as_watched`, `variant_timebase_mismatch_keeps_original_checkpoint`, and `series_completion_needs_real_evidence`. Test duplicate-key, missing-id and changed-lineup scenarios; never match the same episode solely by numeric index across sources.
- [ ] **Step 4 — Implement source/episode transitions.** Map verified source-scope identities only; serialize switch barriers and record pending failed saves instead of claiming success. Restore exactly the current episode's checkpoint when its identity can be proven; if not, preserve unresolved record and present a safe fresh/unresolved result without silently seeking to a different edit/version. Keep the separate work resume pointer protected.
- [ ] **Step 5 — RED/GREEN: persistence outcome and cancellation.** Test `failed_commit_is_not_reported_saved`, `cancellation_propagates`, `rapid_back_does_not_overwrite`, and `no_detached_post_destroy_write` using controlled asynchronous completions. A commit acknowledgement is emitted only after Room transaction success. If onStop/onCleared cannot await a commit, explicitly document reliance on last successful periodic/intent checkpoint; do not promise forced durability after process death.

**Expected:** Probe-ON code actually routes real player-derived state through the candidate coordinator and Room store; legacy OFF behavior remains unchanged; dangerous callback mapping is covered by regression tests.

## R06-3: Android Media3 and file-backed end-to-end tests

**Files:** New tests in `app/src/androidTest/kotlin/com/ichichuang/cinema/feature/player/` plus focused test-only fixtures/host runner as needed. Reuse the existing 61 native cases. Synthetic media only, stored in the disposable app's private test sandbox; never call live sources. Avoid permanent binary/media assets unless necessary and license-cleared.

**Interface contract:** A real `ExoPlayer` drives the actual bridge; Room is opened from a file; real commits and reopen checks confirm the candidate is not an in-memory simulation. Test may inject a controlled repository supplying a test-created local URI but no fake database replacing the Room store.

- [ ] **Step 1 — Verify test device preflight.** Prove test-owned AVD name, serial, `ro.kernel.qemu=1`, API/ABI, boot state, no user data and test-only app identity. Re-check before **every** install or execute. Use guarded `cinema.storageTestArm64=true` + `cinema.playbackRoomProbe=true` only for paired Debug/test tasks. If unsafe or unsupported, native execution is `BLOCKED`, not PASS.
- [ ] **Step 2 — Test real Media3 sample playback.** Generate a deterministic, small, locally accessible 8–12-second PCM WAV or similarly synthetic decoder-compatible fixture in app-private storage, without network. Instantiate/drive real player events through the same bridge used by probe runtime. Assert `isPlaying`, progress update, pause/resume, backward seek, explicit restart and actual work/episode checkpoint in file-backed Room. A WAV validates player-event wiring, not television video decoding; state this explicitly.
- [ ] **Step 3 — Test transitions and failures on actual Room.** Exercise quick next/back between two synthetic episodes, retry after simulated failure, stale callback delivered after new session, same-sequence conflict, failed store IO/rollback, buffering vs user pause, and no auto-play on restored session. Reopen Room and assert persistent per-episode states and current work pointer. Respect the 5-second active-play promotion threshold from Round 05; do not silently change it.
- [ ] **Step 4 — Two-process restore smoke test.** On the same verified disposable emulator only: in phase A, drive playback to a committed checkpoint and close test app; record the DB state. Use serial-pinned, package-exact `am force-stop` on the **test Debug app only** between separate instrumentation runs, then in phase B relaunch and assert restored identity/position, playback paused, and no corruption or implicit active playback. A force-stop cannot guarantee an unsaved final moment; expected position is the **last committed** one. If the test runner design cannot safely separate the processes, report the exact remaining gap and do not call it verified.
- [ ] **Step 5 — Native suite result.** Execute the previous 61 Android test methods and all new tests, record methods actually executed/passed/failed/skipped/not-run. Test APK compilation by itself is insufficient. Shut down only the test-owned emulator after evidence capture.

**Expected:** A connected actual Media3 + Room runtime checkpoint path is exercised on a disposable ARM64 Android emulator; exact limits on physical video/remote and real user migration remain explicit.

## R06-4: Full default-off regression and independent review

**Files:** Actual changed source/tests/Gradle, existing Room `1.json`/`2.json` if schema remains unchanged; `docs/verification/2026-10-24-round-06-playback-runtime.md`; `docs/architecture/tv-playback-runtime-probe.md`; existing progress ledger.

**Interface contract:** No public/production activation. The review focuses on atomic boundary, callback-to-write correctness and writer isolation rather than styling.

- [ ] **Step 1 — Re-run targeted and complete host tests.** Capture deliberate RED → GREEN results, `:app:testDebugUnitTest`, Python suite, source-level T04 event assertions, with counts and exit codes.
- [ ] **Step 2 — Re-run normal builds with probe OFF and ARM64 flag OFF.** `:app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease` with `--no-daemon --no-configuration-cache`; record actual warnings and version/ABI/manifest/unsigned Release properties. Check release guard failure with opted-in property on, without signing or publishing.
- [ ] **Step 3 — Schema and migration guard.** Prefer no schema changes for this bridging round. If a schema change is proven necessary, require a genuine Room v2→v3 migration, export new schema, keep v1 and v2 hashes unchanged, seed v2 data and execute migration tests. Do not use destructive fallback, overwrite existing historical schemas or merely increment a version number.
- [ ] **Step 4 — Security and source review.** Stage intended files only, run the index-based repository security check, inspect staged and unstaged diff including DI/debug gates, logs, test fixture licensing, network calls, ABI and build flags. Request an independent read-only review focused on writer exclusivity, cancellation, lifecycle, identity and version mapping, event ordering and verification strength. Important findings get new failing tests, minimal fixes and a repeated full suite; report whether reviewed again.
- [ ] **Step 5 — Local commits and ledger.** Create coherent local commits. Record starting and final HEAD, branch, changed files, exact JVM/Python/native counts, actual test target, method evidence, Room schema hashes, gate-on/gate-off behavior, failures, regressions, reviewer findings and unverified real-user/ARMv7/TV gates. Mark only the **verified Debug runtime T04 slice**, NOT T03/T04 entire, M02, A06–A09, T09, production release or migration complete. Stop here, do not continue to visual UI redesign or broader feature tasks.

**Expected:** Real isolated Debug playback checkpoint integration with reproducible evidence, while the default production application keeps its previously active legacy DataStore path and untouched distribution boundary.

## Claim boundary

| Evidence | Allowed statement | Not allowed |
| --- | --- | --- |
| JVM event tests only | Adapter mapping passed the tested synthetic event streams | Real ExoPlayer event wiring passed |
| Native test APK compiles | New instrumentation code compiles | Native tests actually ran |
| Actual `Player` + Room test on test-owned AVD | Debug probe playback/checkpoint integration passed there | ARMv7 TV or production upgrade is verified |
| Two-process relaunch passes | Last committed probe checkpoint survives test process restart; returns paused | Arbitrary power loss causes zero loss |
| Debug flags OFF and regression verified | Normal Debug/Release continue legacy storage path as tested | T03 real migration and T04 full production activation is complete |

## Official technical references

- Media3 events: https://developer.android.com/media/media3/exoplayer/listening-to-player-events
- `Player.Listener`: https://developer.android.com/reference/androidx/media3/common/Player.Listener
- Room 2.x migration test helper: https://developer.android.com/reference/kotlin/androidx/room/testing/MigrationTestHelper
- Android saved state vs persistent storage: https://developer.android.com/topic/libraries/architecture/saving-states
