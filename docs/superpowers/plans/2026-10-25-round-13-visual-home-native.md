# Round 13 — Original TV Visual System and Functional Home Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (preferred) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox syntax. New observable behavior requires failing behavioral tests before minimal implementation. This is a bounded **T02/T10 native visual-home slice**, not the full T02/T10/T11/M04 or a production activation.

**Goal:** Deliver an original, coherent, Simplified-Chinese dark TV visual system and a genuinely usable Home/top-navigation slice, rendered by native Jetpack Compose and backed exclusively by the already-verified Round 11/12 candidate local data and state coordinators on an explicitly authorized disposable Debug test profile.

**Architecture:** Keep the existing Kotlin/Compose/TV Material/Media3/Koin feature/domain/data/core/di/nav architecture, Room v6 and the R11/R12 guarded navigation path. Add reusable TV visual tokens, original vector icons with editable SVG masters, shared card/button/focus components, and a Home read-only projection sourced from the existing unified candidate catalog and personal-library stores. Wire only into an existing strictly authorized test entrance; no parallel catalog, user-record writer or new production route.

**Tech Stack:** Existing Kotlin, Jetpack Compose, TV Material, Media3, Koin, Coil, Room v6; built-in vector/SVG assets and current Android instrumentation. No new runtime dependency unless demonstrably essential, narrowly reviewed and with full regression.

**Spec:** `docs/superpowers/plans/2026-10-23-tv-experience-rebuild.md` §§1–4, 5–7, 10–12, T02, T08–T12, A01/A02/A03/A04/A19/A22; `docs/architecture/tv-navigation-restoration.md`, `docs/architecture/tv-personal-library.md`, `docs/architecture/tv-strict-filtering.md`; real Round 12 source and progress ledger. `references/no-brand-visual-direction.png` is **style guidance only**: its fabricated titles, plots, years, scores, UI states and images are NOT valid production fixtures or source claims.

## Global constraints

- **Reported baseline, not independently read:** local branch `codex/contract-foundation`, worktree `<isolated-worktree>`, HEAD `4a35d35e555b184bff93a1e68e4aa0cbb94bf7c3`; check actual local state before work. `main`/`origin/main` reportedly remain `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`. Do not reset, rebase, merge, overwrite local changes, or push.
- System-provided Android application identity is retained, but **no software title, product name, brand/logo, watermark, promotional banner title or constant version corner label may appear inside the app**. Actual movie names and functional labels are allowed.
- All app-authored actionable words, missing-data, error, accessibility labels and help are Simplified Chinese. Real film original titles can remain in source language; third-party licenses and system-controlled strings are not falsified.
- Existing Debug/Release ordinary routes, DataStore writer, source configuration, update files, package identity (`0.9.8` / code `18`), normal `armeabi-v7a` ABI, min/target SDK, signed history and `releaseSigningFrozen = true` stay unchanged. The ordinary unsigned Release is a compile-validation artifact, not a distributable APK.
- All new visuals are real native UI, not an HTML preview, a single painted mockup, an ImageGen overlay or an entire pre-rendered screen. SVG files must remain editable; Compose vectors must have traceable original paths. Never transplant Netflix/third-party logos, proprietary icon path data or poster images.
- Ordinary Debug/Release stays **OFF**. Reuse R11/R12 authorized Debug route/profile gate and single-writer guard. Do not make it possible to activate new UI just by setting one arbitrary public runtime preference. No implicit candidate Room migration, dual writes, live CMS scan or test-data seed into normal user storage.
- This round does **not** delete diagnostics, activate production routing, replace full library/details/player/settings UI, implement global T11 animation/key handling, or claim ARMv7/physical TV performance. Existing diagnostic removal remains T12.
- Tests run only on identity-verified dedicated disposable ARM64 AVD, with pinned serial and verified isolated app package. Do not install on any physical/personal device or disturb shared ADB server. Keep legacy sources/records untouched.
- Reuse actual domain model semantics; do not invent genre/year/language metadata, counts, recommendation scores, watch history, playback availability, or subscriptions. Unknown/partial/failed/offline remain visibly distinct.
- The R12 acknowledged-after-commit but lost-receipt UI retry text Minor is **in scope** for closure. Data-layer idempotency does not stand in for an actual Compose user-visible result test.

## Review focus — five difficult input classes and required owning tests

1. **No catalog configured but valid local personal records:** show functional Home with records + true empty-source guidance, not an invented film hero or a fully empty app; R13-C native tests and R13-D screenshots.
2. **Portrait art / missing art / long Chinese title under large text:** preserve artwork proportions and visible actions, with no clipped focus or inaccessible navigation; R13-B component tests and R13-D layout screenshots.
3. **Delayed image/query completion while user moves focus:** do not steal or teleport focus, change work identity under cursor or replay entrance animations; R13-C + R13-E.
4. **Partially queried sources / unknown totals:** distinguish partial coverage, loading-more, offline and complete-empty; never display an invented all-library count or success; R13-C.
5. **Commit succeeded, UI acknowledgement was lost:** retry reports actual persisted favorite state without duplicate optimistic state or misleading permanent failure; R13-C and R13-D actual native injection/reopen.

---

## File ownership map (confirm existing exact names before touching)

The paths below are the intended responsibilities, not an assertion that their Round 12 counterparts exist with precisely these names. Do not create duplicate files if the repo already has an equivalent type; record the exact actual path/interface in the architecture note before modifying it.

| Responsibility | Intended file(s) | Rules |
| --- | --- | --- |
| TV visual semantic tokens | `app/src/main/kotlin/com/ichichuang/cinema/core/design/TvVisualTokens.kt` | New only if not duplicated by `Color.kt`, `TvMetrics.kt`, `Motion.kt`; single token authority |
| Native icons | `app/src/main/kotlin/com/ichichuang/cinema/core/design/TvOriginalIcons.kt` and focused companion files | Compose `ImageVector`/Android vectors, exact named accessibility semantics |
| Editable icon masters | `design/icons/*.svg` and `design/icons/README.md` | Original SVG geometry, viewBox=0 0 24 24 unless optical correction is documented |
| Shared card/button/focus components | `app/src/main/kotlin/com/ichichuang/cinema/core/ui/` | All interactive elements are native focusable nodes; do not nest two independently focused layers |
| Home read-only state projection | home 功能目录（app 内 feature 包的首页模块）| Adapter to existing R08–R12 candidate stores only; no new DB writer |
| Home rendering | home 模块的 TvHomeScreen.kt（或既有首页包等价物）| Compose receives immutable display state and callbacks, no direct network access |
| Guarded top navigation | Existing R11/12 test-gated navigation seam | Five discoverable Chinese destinations; no dead decorative buttons |
| Tests | existing `app/src/test/...` and `app/src/androidTest/...` | Real JVM transformation + Compose instrumentation + actual screenshots/semantics |
| Evidence | `docs/verification/2026-10-25-round-13-visual-home.md`; `docs/architecture/tv-original-visual-system.md` | Actual commands, shots, visual audit, review, limitations |

Define the narrow new UI boundary in the Home module; names are fixed **only for newly created types**; use the actual existing view-model and repository names after reading code:

```kotlin
internal enum class HomeContentStatus { UNCONFIGURED, LOADING, READY, PARTIAL, OFFLINE, FAILED }
internal enum class HomeImageShape { LANDSCAPE, PORTRAIT, UNKNOWN }
internal data class HomeDisplayCard(
    val workId: String,
    val title: String,
    val artworkUri: String?,
    val imageShape: HomeImageShape,
    val secondaryText: String?,
    val progressText: String?
)
internal data class HomeDisplayState(
    val continueCards: List<HomeDisplayCard>,
    val recentCards: List<HomeDisplayCard>,
    val catalogCards: List<HomeDisplayCard>,
    val contentStatus: HomeContentStatus,
    val failedSourceCount: Int,
    val hasMore: Boolean,
    val totalKnown: Int?
)
```

`HomeDisplayCard` is an **immutable UI projection** of actual saved data; it is not a new authoritative film record. `artworkUri` may be null; remote requests remain governed by existing image/network policies, and a failed poster never deletes the card. Do not derive `totalKnown` from sum-of-source totals or claim global counts from a partial scope. Use real R12 types for actions and route origins rather than adding arbitrary duplicate `HomeAction` or `PlaybackSnapshot` types. For components implement:

```kotlin
@Composable internal fun TvHomeScreen(
    state: HomeDisplayState,
    onOpenWork: (workId: String) -> Unit,
    onResumeWork: (workId: String) -> Unit,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier
)
```

The function above is a display boundary, not a standalone nav host. If an existing code signature already embodies it, **prefer adapting that signature** to avoid type duplication, and document the one final entrypoint. Every callback is enabled only when its destination is actually routable and supported; avoid a misleading enabled CTA.

## Task R13-A — Baseline, design brief and gated navigation seam

**Files:** Existing architecture docs, R11/R12 navigation and profile gate, new plan import, the visual-architecture note.

**Interfaces:** Consumes actual R12 protected destinations and R11 navigation/focus snapshot. Produces a written state-to-screen mapping and the exact single guarded Home route to extend in R13-C.

- [ ] **Step 1 — Verify actual workspace.** Verify current git root, branch/HEAD/status, origin/ref and attachment checksums; preserve worktree even if drift exists. Inspect core design tokens, Compose TV Material components, guarded R11/12 route, R12 personal projection, R10 strict query state and current screen semantics. No repeat of full legacy read-only audit.
- [ ] **Step 2 — Map real types to display state.** Record exactly which actual Flow/Store/DAO supplies continue, recent and catalog items, which yields source-status/coverage, and which receives stable work-ID navigation. Identify missing fields and choose a truthful unavailable state instead of inventing them.
- [ ] **Step 3 — Establish visual brief from existing approved style.** Document a dark charcoal cinematic design, warm restrained focus accent, clear text hierarchy, low visual noise, no UI brand, sufficient distance/contrast. Reference file is a direction, not a render target containing valid movie facts. Document normal and large-text responsive behavior, native focus/selection differentiation, and exact component inventory; name missing T02 deliverables not completed this round.
- [ ] **Step 4 — Fail a guarded-entry test first.** A targeted native or JVM test must show ordinary route cannot surface candidate Home without gate, and authorized throwaway profile can reach the intended root. Do not turn ON existing probe globally. Commit focused baseline/spec changes only after meaningful tests.

**Acceptance:** All new presentation state is sourced from real candidate stores; ordinary runtime remains unchanged.

## Task R13-B — Original design tokens, editable SVG icons and native reusable controls

**Files:** `core/design/` (reuse relevant existing `Color.kt`, `TvMetrics.kt`, `Motion.kt`, `TvFocus.kt` rather than create competing constants); `core/ui/`; `design/icons/*.svg`; JVM test of icon manifest/component semantics; Compose native test.

**Interfaces:** Produces authoritative semantic tokens plus native `TvTopNavigation`, `TvPrimaryButton`, `TvPosterTile`, `TvStatusMessage`, `TvLoadingTile` (names may be consolidated if equivalent exists). These consume only `HomeDisplayCard`, focus/selection state and callbacks. No direct Room writes.

- [ ] **Step 1 — RED token/asset checks.** Write tests/assertions for one token authority, accessible state labels, full mapping of **ten required icon concepts**: 首页、片库、我的收藏、最近观看、设置、播放、查看详情、筛选、返回、重试. Verify each concept has an editable owned SVG source and native vector mapping; SVG geometry/viewBox syntactically valid, no embedded raster, no external href/style imports and no copied third-party icon pack. Test English placeholder fallback not shown.
- [ ] **Step 2 — Author semantic tokens.** Choose dark, content-first colors and contrast-safe semantic accents, typed spacing/typography, focus ring, card radii and disabled/selected states. The visual reference suggests warm-amber focus and dark panels; all literal values in the visual spec are new **proposals**, not inherited fact. Token example palette may begin with charcoal background `#0B1017`, panel `#171E27`, main text `#F5F5F5`, secondary text `#B5BECA`, warm accent `#F5C693` and evolve after native render review. Do not use warm focus indication alone for selection; preserve shape/outline semantics.
- [ ] **Step 3 — Draw original SVG paths.** Create original, recognizably functional 24-unit vector geometry for each of the ten concepts and document optical deviations. Native path commands match masters, not PNG crops and not copied Material/Netflix SVG paths. Record authored-by-project status; do not claim registered trademark/originality verification beyond actual source inspection.
- [ ] **Step 4 — RED native control tests.** For top navigation, button and poster tile: distinct focus/selected state, action dispatch once per confirmed click, disabled action not focus trap, pure DPAD/OK/Back reachability, language semantics, missing image placeholder, portrait containment and large-title wrapping. Test safe reduced motion and no per-item indefinite glow loop.
- [ ] **Step 5 — Implement minimum reusable native Compose components.** Match the reference's restrained hierarchy and warm focus border. Use a composable overlay to indicate loading on real data, not a poster of fake film data. Focus/selection carried by existing R11 mechanism; keep pressed/disabled feedback and accessibility available. Respond to normal repeated D-pad without locking input for an animation. GREEN tests, focus/component review, local commit.

**Acceptance:** Editable vector masters and native rendering are both present; controls are composables, not a painted static image; visual states are distinct and testable.

## Task R13-C — Functional Home with truthful statuses and actual cross-page routes

**Files:** Existing authorized R11/12 navigation, home 功能目录, test fixtures/projections and real Compose instrumentation.

**Interfaces:** Consumes actual existing favorites/recent/continue, R09 source/coverage, R10 candidate strict filter and stable work IDs. Produces `HomeDisplayState` Flow or existing equivalent and `TvHomeScreen` callbacks. Routes to real R11/12 destinations only, preserving their focus/navigation coordinator.

- [ ] **Step 1 — RED projection tests.** `no_sources_does_not_create_fake_hero`, `offline_still_shows_saved_continue_and_recent`, `partial_coverage_does_not_claim_global_total`, `unknown_title_or_poster_does_not_fabricate_metadata`, `work_id_survives_multi_source_change`, `progress_card_uses_acknowledged_episode_and_variant`, `watch_browsing_does_not_mutate_history`.
- [ ] **Step 2 — Implement read-only adapter.** Populate Home sections from existing candidate Room data only; prefer Continue Watching as first row when nonempty, lightweight Recently Watched as next, then currently indexed catalog. A featured/hero panel may show a verified title/work only; when absent, show concise helpful empty-source state with working `前往设置` action. No synthetic featured art/titles, fake popularity/reviews, or internal debug labels on normal builds.
- [ ] **Step 3 — RED native route tests.** `home_to_detail_back_restores_selected_work`, `home_resume_uses_actual_checkpoint_and_stays_paused_on_restore`, `home_to_library_preserves_filter_and_focus`, `home_to_favorite_history_back_preserves_all_contexts`, `top_settings_entry_has_real_destination`, `user_input_preempts_late_focus`, `slow_artwork_updates_do_not_retarget_focus`. These must use actual Compose semantics and platform D-pad events, not just verify a rendered screenshot.
- [ ] **Step 4 — Wire only existing gated route.** Make the five Chinese navigation destinations 首页／片库／我的收藏／最近观看／设置 discoverable and routable. Reuse existing guarded screens for library/favorites/history; for Settings route use the existing harmless settings destination within that same authorized test profile only, never provide an enabled dead-end. Disabled or blocked route must explicitly say why and avoid false navigational affordances. Full settings redesign and diagnostic deletion are out of scope.
- [ ] **Step 5 — Red/green truth-state tests.** Verify unconfigured, empty-complete, loading, partial-failed, offline-with-records, failed, bad image, corrupt read-only projection, interrupted query, no resume and restored resume; never change strict filters silently. For first-run default empty directory, show no fictional catalog content. Navigation labels, source-status and errors are all Chinese and accessible. Ensure UI cannot launch real sources or network scan by demo setup.
- [ ] **Step 6 — Close R12 Minor via native test.** On actual gated UI, inject a successful favorite transaction with deliberately lost acknowledgement and user retry. Assert one durable final state, no duplicate success semantics, no false permanent failure toast, and stable focus after confirmation. Record exact prior Minor reference and commit. GREEN native reruns.

**Acceptance:** One actually interactive Home/back/navigation/continue flow using true durable data, with truthful no-source/partial/offline states; ordinary application paths untouched.

## Task R13-D — Native layout, screenshot evidence and accessibility review

**Files:** Native tests, dedicated fixture image assets generated locally with visible "合成测试" in test-only code, evidence files under `.local/evidence/round-13/` (ignored) and tracked factual verification report.

**Interfaces:** Uses R13-B controls and R13-C actual authorized test route. Does not create new production film facts or new network endpoints.

- [ ] **Step 1 — Native display matrix.** Run at least 1280×720 and 1920×1080 logical output configurations where supported by verified disposable AVD; record actual density, API and screenshot dimensions. Test normal vs enlarged system font scale, very long Chinese title, long saved episode title, missing images, portrait-only poster, mixed art and all six content states. If one configuration cannot be exercised, mark precisely blocked, not passed.
- [ ] **Step 2 — Save screenshot and semantics evidence.** Capture from actual Android screen, matching traceable tested commit, and native accessibility/semantics or UIAutomator node dump proving which labels are focusable. Avoid synthetic Photoshop composition or deriving source claims from reference image. Source art labels in all test screenshot fixtures must disclose synthetic origin and remain debug-only.
- [ ] **Step 3 — Compare visual treatment with reference.** Read side-by-side Home screenshot and reference, then document layout hierarchy, spacing consistency, type overflow, visible focus and controlled accent use. Distinguish subjective design review from automated correctness. Adjust components with targeted RED assertion when there is a behavioral defect; run tests again.
- [ ] **Step 4 — Check final appearance audit.** No software name/logotype/watermark, placeholder film rating/popularity, untranslated owned labels, clipped primary action, fake global inventory, overlapped/hidden focus outline, or visual selection confused with navigation focus. For reduced motion, animated elements stop without changing functionality.

**Acceptance:** Real native screenshots and focus/semantics traces for normal and large-text flows; assets are original vectors; untested devices explicitly recorded.

## Task R13-E — Back, input, animation and persistence regression

**Files:** Existing R11 focus/nav control, R12 action flow, targeted Compose tests; no full T11 keymap rewrite.

- [ ] **Step 1 — RED motion/repeat tests.** `back_returns_to_real_origin`, `repeated_confirm_does_not_push_duplicate_detail`, `image_completion_does_not_steal_focus`, `nav_focus_distinct_from_selected`, `rapid_tab_switch_aborts_stale_restore`, `disabled_loading_still_allows_back`, `reduce_motion_does_not_disable_feedback`.
- [ ] **Step 2 — Minimal fixed-focus motion only.** Focus transition approx 120–180ms and panel approx 180–240ms from original spec as *initial*, not performance guarantee. No entrance animation replay when returning to deep list; no queue build-up from rapid D-pad; reduced-motion path lowers movement while preserving border/state feedback. R11 restoration owns authoritative focus, not per-card ad hoc requestFocus. Use actual press lifecycle; do not accidentally trigger short action on long press (document if T11 full press-semantics still open).
- [ ] **Step 3 — Persistence check.** Open from Home, enter detail/playback, return, visit library/favorites/history, background/recreate/kill/relaunch; verify stable selected `workId`, context, correct source route, paused resume, no unexpected Room/DataStore change and correct UTF-8 Chinese labels. Only last committed record is guaranteed after interruption.
- [ ] **Step 4 — Tests and commit.** GREEN targeted and native key test traces, with explicit known T11 limitations.

**Acceptance:** UI visual changes do not undermine R11/R12 durable contexts or event single-writer guarantees.

## Task R13-F — Full regression, review, evidence and stop

**Files:** `docs/verification/2026-10-25-round-13-visual-home.md`, `docs/architecture/tv-original-visual-system.md`, progress ledger; implementation/tests/assets as above.

- [ ] **Step 1 — Run targeted JVM and actual Android instrumented tests** with test-visible semantic names; reconcile **unique source test methods versus instrumented starts/pass/fail/skips**. Legacy unit/native test suites must remain accounted for; do not claim compiled tests executed. Include R12 acknowledged-after-commit Minor UI test evidence.
- [ ] **Step 2 — Full ordinary gate-OFF regression.** Run `python3 -m unittest discover -s scripts/tests -v`, all JVM unit tests, `:app:assembleDebug`, `:app:assembleRelease` (unsigned only), `:app:assembleDebugAndroidTest`, `:app:lintDebug`, `:app:lintRelease`, suitable actual debug-native regression with pinned AVD; use `--no-daemon --no-configuration-cache` and rerun flags to avoid stale evidence. Record task count, warning *message* diff and every fail/skip.
- [ ] **Step 3 — Safety and source boundaries.** Compare normal gated-OFF APK ABI (`armeabi-v7a`), app IDs/manifest, version `0.9.8`/code `18`, signing freeze, default empty `update/sources.json`, no extra source network contacts, Room schema 1–6 exact hashes unchanged unless explicit separately approved migration (this UI slice should **not** need new tables or version), no extra app data writer, unchanged old user path. Existing Security scan is `python3 scripts/check_repository_security.py` over **staged Git index only**, never historical proof; check SVG external hrefs, fixture branding/claims, credentials and image licenses separately.
- [ ] **Step 4 — Fresh independent read-only review.** Target reference-to-native quality, token duplication, vector originality, misleading metadata/status, D-pad focus, long text/portrait layout, slow callback cancellation, R12 UI ack Minor, accidental ordinary-route mutation and guard bypass. RED→GREEN relevant Important/Critical fixes and full reruns; request limited independent re-review when feasible. Record limitations without erasing found defects.
- [ ] **Step 5 — Verify commit and stop.** Local commits only, prefer per-task meaningful commits, verify tested candidates vs staged/committed blob hashes, clean both worktrees and baseline `main`. Update progress for tested **T02 original token/icon and T10 Home visual slices** only. Keep full T02/T10/T11/M04, A01/A02/A03/A19/A22, real CMS/ARmv7/API24 TV/remote and production activation unaccepted. No push, merge, publish, production sign or proceed to Round 14.

## Explicit out-of-scope and acceptance truth table

| Claim | Allowed Round 13 evidence | Still open |
| --- | --- | --- |
| Original icons ready | Ten editable SVG + native vector mappings and native tests | Full app icon inventory/complete T02 |
| New visual home works | Authenticated test-profile native Home with real R12 records and actions | Default app Home/production launch |
| Top navigation works | Guarded native 5 destinations tested with D-pad | TV physical remote and entire T11 |
| Personal library actions remain safe | R12 actual Room semantics + new UI lost-receipt test | Real-user migration and upgrade |
| Entire TV UX rebuild complete | **Not allowed** | Library strict-filter UI, full detail/player/settings, diagnostic removal, performance and release |

**Stop rule:** Any discovered bypass of production guard, changed normal APK ABI/identity, data-loss risk, unapproved source access, unsupported original assets or unknown destination is a STOP blocker. Report candidly, do not widen scope to hide it.
