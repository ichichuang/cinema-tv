# Round 16 — Native TV Player Controls and Remote Input Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` when available or `superpowers:executing-plans`, task-by-task. All new behavior requires a failing test first, a minimal implementation, a passing test, and an independently reviewable local commit. This is a **bounded T10 player-controls slice + T11 player remote/focus slice**, not a full T11, T12, M04, or ordinary-app activation.

**Goal:** Complete a real five-way-remote-operable player-control experience inside the already authorized disposable Debug Compose flow: play/pause, seeking, line/episode access, overlays, progress/status, error/retry and correct nested Back, driven by the existing Media3 + Room v6 authorities, with no regressions to exact resume or user data.

**Architecture:** Keep Media3 `Player` and R05–R07 checkpoint/session arbitration as the sole playback and durable-progress authorities. Add a small, testable remote-key state machine and UI projection in the guarded R06/R12–R15 player path; use real player state and available commands rather than optimistic UI assumptions. The controls overlay and its optional picker are navigation sublayers, with ephemeral focus/visibility state distinct from persistent episode/variant identities. Do not create a second player, a second checkpoint writer or an alternative legacy implementation.

**Tech Stack:** Existing Kotlin, Jetpack Compose/TV Material, Media3, Room v6, R11 navigation/focus, R12 personal library, R13 original icons/tokens, R14 Library and R15 Detail. Android instrumentation with locally generated permitted media on an owned API30 ARM64 AVD; JVM/unit and repository Python scripts. No new dependencies unless a compelling, reviewed necessity is found.

**Spec:** `docs/superpowers/plans/2026-10-23-tv-experience-rebuild.md` §§3–6, §§12–14, T09–T11, A06–A11/A19/A22; R05–R07 checkpoint/handoff architecture, R11 navigation restoration, R12 personal library, R13 visual system, R14 Library, R15 Detail and its verification report. The full original master may be at `docs/superpowers/plans/2026-10-23-tv-experience-rebuild.md` in the Mac worktree. Validate its actual location and every code symbol locally; the coordinator cannot read unpushed Mac source.

## Global constraints

- **Reported, not independently inspected:** existing worktree `<本机隔离工作树路径（执行时以实际路径为准）>`, branch `codex/contract-foundation`, Round 15 HEAD prefix `1d8f66c` (resolve full hash with `git rev-parse HEAD`), existing `main`/`origin/main` at `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`. Verify clean state and actual code before editing; do not discard drift, reset, cherry-pick, rebase, recreate worktree or change upstream.
- **Only explicitly consented, disposable, private Debug profile:** chain all existing R06–R15 authorization and build-task checks into a new *default-off* `cinema.playerControlsProbe` if a new gate is needed. Do not weaken earlier guards. Ordinary Debug/Release and all existing DataStore, route, DI and startup behavior must remain unchanged; no dual write, silent Room switch or production media source.
- No physical or personal device; only the verified owned disposable AVD, explicitly pinned serial, isolated test identity, locally generated or previously permitted offline media. No remote CMS, actual playback URLs, credentials, third-party downloads or production trust roots. Keep `update/sources.json` empty.
- Preserve `0.9.8 / versionCode 18`, normal `armeabi-v7a` ABI, manifest/package name, min/target SDK, signing freeze, historical assets and unsigned Release. All Room v1–v6 schema bytes must remain unchanged unless a separately justified schema migration is unavoidable (none expected). Never use destructive fallback.
- UI-authored labels and accessibility descriptions must be in Simplified Chinese; no app brand, diagnostic identifiers, stack traces, fake rating, fake episode count or fake media capabilities. Reuse R13 vectors/tokens and SVG source. Any genuinely missing icon has an editable original SVG and matching vector path plus tests; no stock font/image assets.
- Seek/next/previous/track actions **must** check `Player.availableCommands` on the correct application thread before invoking Media3. Display unavailable controls as disabled with truthful Chinese explanations; an unknown duration does not turn into zero/estimated time. Player event semantics and command availability: https://developer.android.com/reference/androidx/media3/common/Player and https://developer.android.com/media/media3/exoplayer/listening-to-player-events .
- A return from the player restores the actual R15 Detail source and R14/13 Home/Library/Favorites/History origin, including stable work and focus. Only last **committed** Room state is guaranteed through kill/force-stop; do not promise lost unsaved frames or cross-variant resume. First restore is paused, never silently plays sound.
- Do not turn on auto-next, sleeping timers, backup, production settings or diagnostic deletion (T12/T13); do not claim global T11 remote completion without physical-device evidence. Keep the five earlier `assumption` skips separate from PASS.

## Review focus: five high-risk input conditions and the owning tests

1. **Physical OK long press:** The initial `ACTION_DOWN` cannot dispatch short OK before the long event is established. `K03`, `K04`, `K05` must reproduce old short-first behavior with a failing test and show one action per complete physical press.
2. **Hidden controls / overlay focus:** The same arrow key must mean the existing bare-player shortcut with controls hidden and directional navigation (or timeline preview) while controls are open. `K01`, `K02`, `K08`, `E04` assert both branches in real UI.
3. **Unknown/nonseekable media:** No call to unsupported seek commands, fabricated duration or progress overwrite. `U05`, `S05`, `S06` assert actual Player command and Room state.
4. **Interrupted switching and callback races:** Late Media3 callbacks/older session cannot replace newly chosen episode/line or steal focus; checkpoint old item before new route. `E03`, `E06`, `P04` include real-media/Room assertions.
5. **External restart:** Overlay is transient, durable playback identity/position returns paused; no preloading-as-watch event. `P06`, `P07`, `P08` require separately orchestrated OS process phases, not in-process Activity recreation.

---

## Exact responsibility map (check actual R15 symbols before coding)

| Responsibility | Expected existing source / area to inspect | Intended change |
| --- | --- | --- |
| Guarded app route and internal player | `MainActivity.kt`, `nav/CinemaNavHost.kt`, guarded probe route, `feature/player/` | Modify **guarded** dispatch only; keep ordinary Legacy branch byte-identical wherever feasible |
| Exact work/episode/variant dispatch | `feature/detail/`, R15 Detail action, `PersonalLibraryRuntime`, R06 `playbackRoomProbe` | Reuse verified identity tuple; don't map by index/title/source URL |
| Checkpoint/session arbitration | R05 `PlaybackCheckpoint` coordinator / Room v6, R06 event bridge, R07 writer handoff | Call existing contract; do not fork, relax or bypass arbitration |
| User library and nav restoration | R12 Room user records, R11 `BrowseRestoreCoordinator` and focus components | Preserve persisted state; transient overlay state is not watched history |
| Original visuals and focus | `core/design/`, `core/focus/TvFocus.kt`, `core/ui/`, `design/icons/tv-*.svg`, `TvOriginalIcons.kt` | Reuse tokens; add icons only with editable source + native parity |
| New player-controls UI | Prefer focused files under `feature/player/` (e.g. `TvPlayerControlsScreen`, `TvPlayerControlsState`, `TvPlayerRemoteController`) | One owner for key interpretation, one projection, one native control surface |
| Tests | Existing `app/src/test/.../player/` and `app/src/androidTest/.../player/` | JVM event-state/clock tests + actual Media3/Compose/Room, real D-pad XML and screenshots |
| Evidence and local progress | `docs/verification/`, `docs/architecture/`, `docs/superpowers/progress/` | Reproducible commands, test methods, screenshots, no speculative PASS |

Exact Kotlin symbol names above are responsibility labels, **not** an assertion that a particular method/class exists. Determine file paths, real signatures and ownership from the checked-out Round 15 source and record reconciliations before changes. No broad refactor or second remote listener attached to both parent and child.

## R16-A — Verify baseline, guard, player ownership (G01–G07)

**Files:** inspect prior R15 sources/tests/docs; modify `app/build.gradle.kts` and guarded routing/authorization only if new probe is necessary; add narrowly scoped guard tests and Round 16 plan/matrix to `docs/superpowers/plans/`.

**Produces:** Verified checkpoint hash, guard decision flow, player/input-ownership diagram, actual test-runner task+serial strategy and evidence baselines.

- [ ] A1. Verify ZIP manifest from its root, exact worktree branch/HEAD, both dirty statuses, R15 56-row report, original master and current resolved contract signatures; stop and report unexpected drift rather than rewriting or resetting anything.
- [ ] A2. Capture baseline full host tests, existing granted native fixture availability, unchanged ordinary build hashes (or record a reproducible baseline). Read prior R15 `Detail`→player route, R06 Media3 listener, R05 checkpoint, current `onKeyEvent` or `onPreviewKeyEvent` owner, any legacy long-press bug and legitimate Media3 commands.
- [ ] A3. Write failing guard tests: enable without all upstream probes, without private test marker, with Release/bundle/publish/install/signing targets, or with legacy profile/data must be rejected **before any side effect**; normal OFF renders unchanged legacy.
- [ ] A4. If needed, add `cinema.playerControlsProbe` default false and chained Runtime authorization, with one canonical input owner and no duplicate key dispatch. PASS means compile + negative gates + actual normal-OFF behavior; a newly named build switch alone is insufficient.
- [ ] A5. Commit guarded foundation plus focused tests locally after independent review of the gate. Keep plans/status as `NOT RUN` until tests actually execute.

## R16-B — Player UI projection and honest commands (U01–U08)

**Files:** create small `feature/player/` UI-state/projection/control-screen files and host/native tests; reuse R13 `TvPrimaryButton`, icon vectors, focus/motion tokens.

**Produces:** `PlayerControlsUiState` (contract shape reconciled to code) with real `currentWorkId, episodeId, variantId, isPlaying, isBuffering, playbackState, positionMs, durationMs?, commandAvailability, overlayState, focusedControlId, error?, reducedMotion` and a guarded Compose surface. Do not maintain a second watched/history truth.

- [ ] B1. Write unit and native RED assertions for U01/U02: play/pause and progress reflect Media3 state after a real event; don't equate `onIsPlayingChanged(false)` with user pause and don't mistake buffering for completed playback.
- [ ] B2. Implement state projection from existing guarded Media3 `Player` / listener with an explicitly bounded UI position ticker while visible, not a second checkpoint timer or independent progress writer.
- [ ] B3. Add RED tests U03–U06 for real overlay reveal, idle-hide gates, unknown-duration state, disabled commands and error/retry; implement controls with visible labels, no hidden-only required actions.
- [ ] B4. Native integration test confirms a disabled control does not invoke unsupported player commands, audio/video-surface overlay does not capture unintended input, and controller disposal cancels listener/ticker. Use locally generated media and distinguish WAV audio from actual video frame evidence.
- [ ] B5. Commit tested UI projection slice; record remaining false states instead of synthetic content.

## R16-C — Exactly-once physical key cycles and focus (K01–K10)

**Files:** `feature/player/` input owner (pure key-cycle reducer plus Android adapter), native Compose/player tests; minimize changes to shared `TvFocus.kt` and Legacy player.

**Produces:** Deterministic reducer for DOWN / repeated DOWN / long-press threshold / UP / CANCEL, per physical key-cycle. Emit at most one activation for OK/Back per cycle; allow bounded directional auto-repeat. `onKeyEvent`/`onPreviewKeyEvent` or native adapter owns consumption at a single layer; no existing `.clickable` double-dispatch path.

- [ ] C1. RED K03/K04: first DOWN never triggers short OK; a true long OK fires long once and never short; short fires once on legitimate UP. Verify using actual Android key events and direct controller tests.
- [ ] C2. Implement monotonic long-press recognition (real platform repeat/long flag + bounded timer when needed); loss of focus, overlay switch, CANCEL or missing UP revokes held state. No arbitrary key timeout should silently synthesize a short click.
- [ ] C3. RED K01/K02: controls hidden retains original bare-player L/R seek and U/D episode-switch semantics **only if supported**; controls visible routes arrows to actionable controls, and the focused timeline routes arrows to seek preview. Actual UI state decides route, not stale flags.
- [ ] C4. RED K05–K10: rapid OK and Back physical cycles, mismatch key-up, opener focus, focus trap, user canceling stale restore, and no dead ends. Implement minimal reducer and tie focus to stable `ControlId`, not child position.
- [ ] C5. Long OK may be a shortcut to settings/controls only where prior behavior and contract permit, but all operations also have visible short-OK-accessible controls; disabled items never trap focus. Commit with native evidence.

## R16-D — Seeking, episode/line overlays and preservation (S01–S08, E01–E07)

**Files:** guarded player controls, optional reuse of R15 line/episode picker, existing Media3 bridge and checkpoint interfaces; JVM and native tests.

**Produces:** Seek-preview state separate from actual committed playback; identity-safe line/episode picker sublayer; correct playback commands and save barriers.

- [ ] D1. RED S01–S03: confirmed seek, backward seek, intentional zero and repeated directional inputs preserve user-intended real target; default step comes from checked Media3 commands or existing configured increment, never invented source duration.
- [ ] D2. Implement timeline preview only while focused and seekable. Left/right previews within real bounds; OK confirms and calls actual Media3 once; Back cancels without progress mutation. For unknown duration, disable absolute scrubbing with truthful Chinese; command-supported relative seek remains separate.
- [ ] D3. RED S04–S08: preview does not create watched events, unknown length, unavailable command, seek failure and old callback cannot corrupt last committed checkpoint. Reuse existing 5-second event coordinator and transaction result; never mark optimistic UI as committed.
- [ ] D4. RED E01–E04: five-way opener → line/episode picker → Back focus, exact stable identity, disabled route and no inherited progress across variant. Reuse R15 picker where possible, not a duplicate data source.
- [ ] D5. RED E05–E07: switch waits old episode checkpoint barrier; retry respects identity and errors preserve committed state; concurrent quick switches/cancellation reject late callbacks. Don't add auto-next or source auto-fallback. Commit focused slice.

## R16-E — Lifecycle, real Media3, responsive visuals (P01–P08, V01–V07)

**Files:** guarded player activity/route and tests, verification screenshot assets in ignored `.local/evidence/round-16/` (never insert fake screenshot artifacts into product resources).

**Produces:** Player controls usable on actual owned AVD and real offline media, reliable last-commit recovery; 720p/1080p/large-font/unknown-duration/failure/reduced-motion screenshots.

- [ ] E1. RED P01–P05: pause vs buffering vs errors, background save and retry outcome, interrupted switch, idle timer and Back one-layer behavior. Use real Media3 + Room v6 on a verified disposable AVD. Do not treat direct coordinator calls as Activity lifecycle instrumentation.
- [ ] E2. Run a separately orchestrated **two-phase OS-process** experiment: phase A with actual player media/controls commits exact work/episode/variant/position; host triggers force-stop or `am kill` and verifies different PID; phase B explicit relaunch restores same last committed record and remains paused. Save actual commands and PID evidence. A second phase must not rely on the old instrumentation process surviving force-stop.
- [ ] E3. RED V01–V07: 1280×720 and 1920×1080 controls fit; large font, long episode/line labels, reduced motion, loading/error/unknown duration, actual pixel checks plus semantics and D-pad reachability. Any dubious `boundsInWindow` zero requires screenshot/pixel or alternative robust corroboration, never a false PASS.
- [ ] E4. Device-specific decoding/video-output claims require actual video output from locally generated licensed/offline clip and proof of first rendered frame. If only PCM WAV available, count Media3 audio + control-layer tests as passed but mark video frame/surface claims `BLOCKED` (not N/A). Do not download arbitrary third-party media to hide the gap.
- [ ] E5. Validate overlay focus while player is playing; disable auto-hide during pause, error, loading, picker, accessibility interaction; measure whether animations remain interruptible. This round is not a release-like p95 performance measurement or two-hour longevity acceptance.
- [ ] E6. Commit tested native UI/lifecycle implementation, preserve evidence and exact limitations.

## R16-F — Whole-branch review, security and local handoff (R01–R05)

**Files:** `docs/verification/2026-10-26-round-16-player-controls.md`, `docs/architecture/tv-player-controls.md`, existing progress ledger, matrix statuses; relevant tests and product files only.

- [ ] F1. Run `python3 -m unittest discover -s scripts/tests -v`, `./gradlew :app:testDebugUnitTest --no-daemon --no-configuration-cache --rerun-tasks`, and normal all-probes-OFF `:app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease` (or the real existing task names; record exact commands). Keep production signing frozen; Release is unsigned verification only.
- [ ] F2. Execute the 60 matrix rows using matched JVM/native/instrumented suites; run actual Android tests on a pinned verified AVD serial and isolate test pairing. Enumerate discovered vs started vs passed vs failed vs skipped/assumption vs ignored vs not-run and prevent duplicate method counting. Include inherited five assumption skips as a separate category; do not translate to success.
- [ ] F3. Compare original Room v1–v6 schema hashes, ordinary ABI/package/version, empty source catalog, manifest and signing-freeze lines, normal-OFF Debug/Release APK identity and baseline lint warning **message multiset**. Test guard denials for illegal Release/bundle/install/publish/signing and unauthorized profiles; inspect artifacts for secrets and sample movies.
- [ ] F4. Run `python3 scripts/check_repository_security.py` **after staging** and note explicitly that it checks the staged Git index, not history. Obtain an independent *read-only* full-slice source/UX/security review where available. For real defects, preserve failing RED then GREEN, run full affected/native regression and targeted reviewer check; record any open Minor rather than declaring zero by assumption.
- [ ] F5. Create meaningful **local-only** commits, compare tested working bytes with staged and committed blobs; verify two worktrees clean without modifying `main`. Deliver precise Simplified Chinese report with full hashes, verified test counts, screenshot paths, process PIDs, failures and residual boundaries; update master progress ledger, **stop after Round 16**.

## Matrix scoring and stop boundary

- Each of the **60 rows** in `PLAYER-CONTROLS-ACCEPTANCE-MATRIX.md` starts `NOT RUN`. Mark `PASS` only with executable evidence meeting that row's required layer. Use `FAIL` for executed failures, `BLOCKED` for missing device/fixture/code, and `N/A` only for a demonstrably inapplicable condition with proof.
- Complete JVM or mock tests cannot replace required real Compose/Media3/Room/UI assertions. Instrumentation *compilation* never counts as execution. WAV audio verification does not establish video frame or decoder compatibility.
- If the new guard permits forbidden build tasks, ordinary Debug/Release changes, profile ambiguity, production signatures, an unauthorized AVD, real media network or user-data access: **stop immediately**, preserve evidence and do not continue execution.
- Passing Round 16 establishes only the guarded player-control and D-pad slice of T10/T11. It does **not** close entire T02/T03–T12/M04 or A01–A24, real source integration, full native performance, ARMv7/API24 physical TV, signed production upgrade or signing security review.
