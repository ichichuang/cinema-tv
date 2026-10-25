# Round 15 — Native Work Detail, Playback Lines and Episode Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (preferred when available) or `superpowers:executing-plans` task-by-task. Each behavior change starts with a real failing test, then the smallest implementation and a passing test. This is a **bounded T10 Detail UI + T09 action-consistency + T08/T11 focus slice**, not the full player-control redesign, normal-app activation or production acceptance.

**Goal:** Deliver an original, truthful, five-way-remote-operable native Compose 作品详情 view connected to the already validated identity/provenance, line/episode, favorites and playback-resume authorities in the consented disposable Debug path. Preserve exact Library/Home/Favorites/History return origins, stable focus and personal data, while proving play, resume, favorite and multi-line choices on actual Android instrumentation.

**Architecture:** Extend the existing guarded R11–R14 Compose route, reusing R13 visual tokens/icons/components; show one stable work identity from R08 rather than independently merging source rows. The screen is a projection of authoritative R08 work/source/route evidence, R12 user records and R05–R07 playback/session authority. A selected line or focused episode is UI state; only a confirmed, available playback entry may start the existing guarded Media3 runtime. No new record owner, bypass query or replacement media player.

**Tech Stack:** Existing Kotlin / Jetpack Compose TV Material / Media3 / Room v6 / R08–R14 candidate components, host JVM and Android instrumented tests on an owned disposable AVD. No required new dependency, Room schema, external media requests or physical-device installation.

**Spec:** `docs/superpowers/plans/2026-10-23-tv-experience-rebuild.md` §§1,3–6,8,10–12, T02/T05/T08/T09/T10/T11, A01–A04/A06–A10/A16/A18/A19/A22; existing `docs/architecture/tv-catalog-identity.md`, `tv-personal-library.md`, `tv-playback-runtime-probe.md`, `tv-navigation-restoration.md`, `tv-library-filter-ui.md` and the R13 native visual materials. Read the real source, not guessed interfaces, at the verified checkpoint.

## Global constraints

- **Checkpoint to verify, not independently inspected:** Existing worktree `（本仓库根，既有隔离 worktree）`, branch `codex/contract-foundation`, user-reported HEAD `827b3983f8271b3f35eac0e6996a9dac38ea8392`. User reports `main` and `origin/main` at `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`, both worktrees clean, no remote branch. Investigate drift without reset, rebase, checkout overwrite, worktree recreation, push or merge.
- Only the existing **explicitly authorized, disposable-profile Debug probe route** may show this detail implementation. Reuse all R11–R14 consent, writer and task-pair gates. An additional default-off Detail probe is permissible only if necessary and must **chain** earlier gates, reject Release/bundle/disallowed install tasks, and leave ordinary Debug OFF semantics unchanged. No separate bypass. No other normal-app runtime changes.
- Keep `com.ichichuang.cinema`, `0.9.8`/versionCode `18`, minSdk `24`, targetSdk `28`, ordinary `armeabi-v7a` ABI, signing freeze, unsigned Release verification, existing historic APK/tag, and empty `update/sources.json` unchanged. Preserve all Room schema `1.json` through `6.json` **byte-for-byte**. If schema change is indispensable, **stop and report** rather than silently advance to v7.
- No unauthorized or live CMS, provider/network scanning, remote poster downloads, fake default works, credential handling, personal/physical TV, signed publish, or unverified source connection. All scenarios use synthetic work/line/episode facts with private, locally generated playable fixture media solely inside a verified owned emulator.
- Reuse R08 stable work IDs, source-entry IDs, route/variant IDs and original evidence; never reconstruct identity from title/year, infer episodes from array indices, or collapse entries because display labels coincide. Unknown, missing, conflict and explicitly disabled facts remain distinct. Do not invent ratings, quality, episode totals, whole-series completion or guaranteed playback.
- Reuse R12 Favorites/History/Continue and R05–R07 persisted session arbiters. Entering a detail, focusing a line/episode, selecting a poster or loading a route **is not** a watch event. Old callbacks and retries cannot corrupt another episode or variant. No speculative resume/favorite writes and no dual writes to Legacy DataStore.
- Reuse R13 original tokens and SVG/native-vector mapping. Own UI/action/accessibility copy is Simplified Chinese, with no app/software brand, watermark or diagnostic information. Film titles/source-given work names may retain truthful original script. All major operations are reachable with D-pad/OK/Back; focus and selected states are visually and semantically different.
- This round does **not** redesign the full in-player controls, rewrite Media3 behavior, change global key bindings, create subscriptions, remove diagnostics from production, implement user-data migration in real profiles, activate normal app or claim T08–T11/M04 completed.
- Preserve original R14 test evidence and disclose its **five existing assumption skips** precisely; they are not passed methods. Improve the R14 N09 missing-deep-anchor evidence with a **strong direct native assertion** instead of extrapolating from JVM-only proof.
- JVM, native test and screenshot evidence must name actual discovered Gradle tasks, build switches, package/runner, AVD identity, serial, XML/test events, exact run counts and skips. Test-only timings are **not** production performance measurements.

## Review focus: five highest-risk behaviors and their owning tests

1. **Same work from several source entries / identical line labels:** display all distinct, evidence-supported routes; choose by stable route identity, not text or title/year; `P06` + `E01/E07` tests.
2. **Resume after explicit line switch with no proven equivalent episode:** never copy old variant's 8000ms position to another variant; user must confirm the selected route and resume semantics; `E07/E08`, `A01/A04` tests.
3. **Bad/disabled route, source failure or no episode list:** no false playable button, auto source switch or forged episode label; preserve favorite/history; `E02/E05/E09`, `A05/A09` tests.
4. **D-pad held OK plus async lines refresh when a modal is closing:** one action per physical cycle, no background focus, user input cancels late restoration; `E06/E10`, `N03–N06` tests.
5. **After a deep-filter search from Library, detail → line sheet → playback → Back → detail → Back:** same original source route, query, work anchor and valid neighboring focus after removal; `N01/N02/N05/N07/N10` native tests.

---

## Source/ownership map (reconcile with checked-out code before implementation)

| Responsibility | Suggested exact location (create only if no equivalent exists) | Constraint |
| --- | --- | --- |
| Read-only detail state/projection | `app/src/main/kotlin/com/ichichuang/cinema/feature/detail/guarded/DetailUiState.kt`, `DetailProjection.kt` | Consume actual R08 work/entry/provenance and R12 personal-record types; do not create a catalog repository |
| Line/episode selection intents | `.../feature/detail/guarded/DetailSelectionController.kt` | Distinguish viewed, focused, selected and confirmed-playback state; IDs are opaque keys |
| Native detail composition | `.../feature/detail/guarded/TvWorkDetailScreen.kt` | Reuse R13 components, original icon set and focus tokens |
| Existing guarded navigation seam | Actual existing R11–R14 route host/runtime discovered at checkpoint | Only authorized Debug; maintain parent route and R11 stable snapshot authority |
| Playback action seam | Actual R06/R12 guarded Media3 action/controller | Explicit user OK dispatches existing session start/resume with exact `(workId, episodeId, variantId)` |
| Personal record actions | Existing R12 favorites and resume stores | Single writer; consistent committed or retry states, no optimistic success |
| Host tests | `app/src/test/kotlin/com/ichichuang/cinema/feature/detail/guarded/` or observed convention | Tests on real controllers, projections and corner cases |
| Device tests | `app/src/androidTest/kotlin/com/ichichuang/cinema/feature/detail/guarded/` or observed convention | Real Compose/Room/Media3 when required; no self-only fake UI |
| Docs and evidence | `docs/architecture/tv-work-detail-ui.md`, `docs/verification/2026-10-25-round-15-work-detail.md` | Exact routes, source ID maps, native screenshots, coverage matrix, limitations |

Do **not** rename the existing ordinary `feature/detail` implementation just to fit these suggestions. Identify R14's present detail sublayer and R12's guarded player handoff first; use the smallest safe seam. A copy/paste duplicate detail model or new history writer is a rejection.

### Explicit logical boundaries

The actual names and signatures in the checked-out repo control implementation. The following are **behavioral contracts** rather than unverified literal Kotlin signatures:

- **Detail projection input:** stable `workId`, an evidence-backed work snapshot (including unknown/conflicted facts), *all* distinct source entries/playback routes, per-episode/variant records, actual favorite/continue/historical states, live route availability with freshness/coverage, navigation origin.
- **Detail projection output:** truthful display fields and explanations, route/episode presentation carrying stable IDs, `playAction`/`resumeAction` only when a verified usable route and exact record target exist, independent `focusedId` and `selectedId`, current committed personal state and pending/failure states.
- **Action dispatch:** explicit user confirmation checks current route, consent, work identity, episode identity and variant identity **at point of dispatch**; only then call the existing guarded R06/R12 Media3 seam. Never create a new playback session coordinator or write a progress checkpoint from focus alone.
- **Return behavior:** detail context is keyed to `workId` **and a distinct origin token**, not a mutable URL. Top overlay Back restores opener; detail Back restores its R11/R14 origin (Home, Library, Favorites, History or Continue), including full query and focused work.

## R15-A — Checkpoint, seams, actual UI and test baseline

**Files:** Existing R08/R11–R14 contracts, current `feature/detail`, actual guarded nav host and tests; new scoped plan and ledger documentation only until baseline is complete.

- [ ] Verify supplied archive hashes and compare reported full HEAD, branch, both worktree cleanliness, ordinary build/signing/ABI/version and Room v1–v6 schema hashes with source. Stop on unexplained drift, preserving user changes; do not clean/reset.
- [ ] Read the complete master spec sections listed above and actual classes for works, variants, episode identity, Route availability, source evidence, R12 personal library, R14 `LibraryScreen` detail callback, R11 focus restore, guarded Media3. Save a **concrete type-to-file map** and any already-supported behavior to avoid duplicate implementation.
- [ ] Run targeted existing R12–R14 JVM/native tests; record exact counts and the R14 5 assumption skips. Confirm ordinary probe OFF path and local fake-source absence before adding UI.
- [ ] Build synthetic fixture: one work with two source entries sharing a display label but different variant keys, independent episode sets, a second same-title/year work with distinct stable identity, missing year/conflicting language work, a disabled/offline route, and an 8000ms resume for one exact episode/variant. Use explicitly local generated media, no network.
- [ ] Map all 56 acceptance rows to real test methods/process evidence and their first owner before editing. Commit meaningful fixture/baseline evidence locally only if useful as a standalone diff.

## R15-B — Evidence-based, truthful detail projection (P01–P09)

**Files:** `DetailUiState.kt`, `DetailProjection.kt` only where absent; host tests for P cases.

- [ ] RED: `detail_identity_uses_work_id`, `same_title_year_is_distinct`, `same_work_shows_both_source_entries` demonstrate the projection never joins on labels. Run first to show expected failure before implementing adapter, then GREEN.
- [ ] RED/GREEN: `missing_or_conflicted_fact_has_no_verified_claim`, `audio_subtitle_do_not_change_work_language`, `original_artwork_missing_fallback`, `stale_source_evidence_not_current_playability`; preserve `FactIds`, provenance/source and missing/known-empty/conflict semantics, not coercion to a plausible value.
- [ ] RED/GREEN: `only_verified_episode_completion_is_shown`, `details_do_not_fake_total_rating_or_availability`. A recent episode completion is not completion of all seasons; clearly label unverified metadata in Chinese.
- [ ] Assert read-only projection causes **zero** user record mutations and does not require network. Run all tests after minimal implementation and refactor only after GREEN. Commit the focused projection/test change.

## R15-C — Source route and episode picker (E01–E10)

**Files:** Guarded detail controller/screen, existing actual route adapter; JVM plus native tests.

- [ ] RED/GREEN: construct two distinct route IDs with equal display name and three episodes with independent IDs. Assert the screen displays all routes, the current selected route is independent of D-pad focus, and switching an active line changes the **candidate** route rather than silently transferring progress.
- [ ] RED/GREEN: absent/disabled/unreachable routes are visible with truthful explanations and disabled Play when necessary; empty episode list is not a fabricated “第 1 集” default. Source episode labels are displayed faithfully; index-only labels without proof are not synthesized.
- [ ] RED/GREEN: open route/episode picker via a visible basic-remote control, trap focus in top layer, navigate using D-pad, confirm once with physical OK-cycle semantics, dismiss with Back to exact opener. Do not start media when a user only highlights/focuses an episode.
- [ ] RED/GREEN: slow or cancelled source/episode refresh arriving after a newer selection cannot reset the selection, start playback or steal focus. Ensure source-specific episodes are separate and only evidence-backed equivalent IDs can map progress.
- [ ] Run host controller tests followed by actual guarded native Compose picker tests. Commit the independently reviewable picker slice.

## R15-D — Favorite, play, resume and truthful failure states (A01–A09)

**Files:** Guarded detail actions and existing R12 user store/player entry seams; JVM/native tests.

- [ ] RED/GREEN: on committed resume for exact `(workId, episodeId, variantId, 8000ms)`, the detail button explicitly states resume vs play and invokes the existing guarded runtime with that target, not guessed next episode or max-duration merge. Reopened detail remains paused; playback starts only after confirmed user OK.
- [ ] RED/GREEN: opening detail, poster loading, focusing a line/episode, cancelling a sheet or preloading media do not create viewing history, raise the continue pointer or write playback progress. Preserve R05 five-second commit behavior.
- [ ] RED/GREEN: selecting a different route/episode with no proven mapping does not reuse old position; unavailable selected route never auto-falls back to another source. Return an actionable Chinese error/disabled state without misrepresenting a playable media URL.
- [ ] RED/GREEN: toggle favorite through actual R12 transaction and source workId; offline/disabled source still retains favorite. Failed commit visibly reverses pending state and permits retry. Replayed confirmation after an acknowledgement-loss scenario converges idempotently instead of toggling again.
- [ ] Use actual generated local media and actual guarded Media3/Room test for verified playable fixture, not just a mocked navigation callback. Track exact record state before and after action. Commit targeted changes after green and full impacted tests.

## R15-E — Real navigation, remote, process and responsive visual (N01–N10, L01–L07)

**Files:** Actual guarded nav host, R11 focus coordinator adapters, guarded screen and native tests, synthetic screenshots.

- [ ] RED/GREEN: enter detail independently from Home, deep Library filtered result, Favorites and History; Back uses exact origin snapshot (query, scroll anchor, focused work). Through detail → picker → playback → Back → detail → Back, each Back closes **one** layer and does not tunnel through to Home.
- [ ] RED/GREEN: verify true native D-pad events show focused vs selected distinction, cancel stale focus on user input, and prevent duplicate OK action when held/repeated or when a child layer closes. Long OK must not execute short OK first.
- [ ] Strengthen old R14 N09 with an actual **native controlled removal** of a deep-list focused work, then assert chosen adjacent stable work identity in Compose semantics/XML (not only fallback algorithm in JVM). No unrelated first-item jump. Report this as new evidence, not retroactive R14 full acceptance.
- [ ] Test Activity recreation and independently orchestrated `force-stop`/new-PID relaunch for committed route selection/detail context and paused resume. A test that merely invokes `background()` or re-creates a ViewModel does not establish a real process restart. Only last committed data is guaranteed.
- [ ] Capture **actual** 1280×720 and 1920×1080 screenshots on pinned owned emulator and inspect long Chinese titles/episode names/route labels, 200%-scale accessibility text (if available), missing/portrait images, disabled/partial states, reduced motion. Preserve visible controls; neither crop a necessary button nor hide the only way to select an episode or favorite.
- [ ] No fake film ratings, global counts, app brand, debug/diagnostic text. Existing icon token/SVG source remains authority. If additional icon is genuinely necessary, add original editable SVG and exact native-vector mapping + screenshot/accessibility proof. Commit the validated UI/navigation slice.

## R15-F — Whole-branch verification, independent review and local handoff (R01–R05)

**Files:** `docs/verification/2026-10-25-round-15-work-detail.md`, `docs/architecture/tv-work-detail-ui.md`, `docs/superpowers/progress/2026-10-23-tv-rebuild.md`; existing schema untouched.

- [ ] Run actual complete JVM debug-unit suite and Python unittest suite, then `:app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease :app:testDebugUnitTest --no-daemon --no-configuration-cache --rerun-tasks` with actual repo syntax/task availability. Ordinary R11–R15 gates OFF; record Gradle task count and compare Lint warning **messages**, not just totals.
- [ ] Run the full relevant native tests on an owned disposable identity-checked Android emulator, explicitly pin serial and app/test pair. Record tests discovered, methods started/finished, 0/other failures, skips and process experiment totals **separately**. Preserve existing five R14 assumption skips unless directly fixed with evidence; do not count skipped tests as pass.
- [ ] Verify SHA-256 bytes of `app/schemas/.../1.json` through `6.json`, normal artifact ABI/identity/version/release unsigned status and `update/sources.json`. Confirm no modified Legacy runtime DI/NavHost/default Debug/Release writer or production source access; probe invalid build requests still blocked.
- [ ] Stage only intentional paths, **then** execute `python3 scripts/check_repository_security.py` and report that it checks Git index, not history. Inspect `git diff --cached --check` and security-sensitive generated files. Reconcile all 56 matrix outcomes to real tests, screenshots, XML and recorded commands, including blocked and conditional cases.
- [ ] Request independent **read-only** architecture and UX/focus review with explicit emphasis on identity, availability, dynamic episode lists, nested Back paths, single-writer and data deletion bounds. If issues arise, obtain failing tests, minimal fixes, rerun impacted and whole tests; explicitly distinguish review, repair and follow-up limited review.
- [ ] Create local coherent commits, verify tested file hashes versus staged blobs and final commit blobs. Record full baseline/final SHA, files changed, test counts/runner/emulator, screenshots evidence references and environment constraints in Simplified Chinese. Leave both worktrees clean, main unchanged, no upstream, no push/merge/release. **Stop after Round 15.**

## Exit criteria and explicit non-acceptance

- **Only** the authorized Debug native Detail/line/episode/favorite/play-resume UI slice may be called candidate-tested, contingent on the actual evidence. Native rendering and Media3 handoff must be exercised, not inferred from class compilation, a mock click or screenshots alone.
- **Do not mark** full T02/T03–T11, M04, A01–A24, ordinary navigation/player behavior, native player-control redesign or production source availability complete. Every missing condition remains open in progress ledger.
- Future work remains: full player-controls UI and global remote/motion behavior (T10/T11), settings/diagnostics deletion (T12), experience enhancements (T13), non-debug performance and device tests (T14), real-data upgrade and release safety (T15/T16). Actual sources and real ARMv7 API24 TV still require separate verified permission and evidence.
