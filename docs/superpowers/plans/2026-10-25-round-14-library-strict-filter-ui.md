# Round 14 — Native Library and Strict Filter Interaction Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` task-by-task. Use explicit checkbox steps, start each new behavior with a failing test and inspect the actual repo before naming code. This is a **bounded T10 Library/filter interaction slice reusing T06–T08 and R13**, not complete T10/T11, production activation, or real media-source certification.

**Goal:** Deliver a real, remote-operable, dark original native Compose 片库 screen and precise multi-dimensional/year filter panel in the already gated disposable Debug experience. The UI must send the complete T07 query to the existing candidate query pipeline, reflect honest coverage, and restore stable work focus after detail/navigation/restart without ever weakening the user's conditions.

**Architecture:** Reuse the R13 five-destination navigation/TV visual components and R11 browse-focus coordinator, R10 strict three-valued filter and query persistence, R09 source-budget coordinator, and existing Room v6 catalog/user records. Build a thin Library UI projection and an editable filter draft on top of existing authoritative `CatalogQuery`/`CatalogResult` types. Submit whole queries atomically; the Compose layer does not aggregate sources, load CMS pages directly, or introduce another persistence/writer authority. Wire only to the verified R11–R13 test-authorized Debug entry; ordinary Debug and unsigned Release remain legacy.

**Tech Stack:** Existing Kotlin, native Jetpack Compose/TV Material, current Room v6, existing image/navigation primitives and Android instrumentation. No new runtime dependency, Room schema, signing path, asset scraping, network fixture, or new global state library without documented necessity.

**Spec:** `docs/superpowers/plans/2026-10-23-tv-experience-rebuild.md` G01/G03–G08, §§3–5, 7–8, 11–12, T02/T06/T07/T08/T10/T11, A01/A03/A04/A12–A15/A19/A22; existing `docs/architecture/tv-strict-filtering.md`, `tv-query-subscription.md`, `tv-navigation-restoration.md`, `tv-original-visual-system.md` and Round 13 artifacts. Read the real contracts: do not infer signatures from this planning package. R13 home screenshots/design assets are established styling, not movie-data evidence.

## Global constraints

- **Reported checkpoint, not independently inspected:** `codex/contract-foundation`, 既有隔离 worktree（`codex/contract-foundation`，本仓库根）, most recent local HEAD prefix **`865401b`** (R13 documentation commit; recover and record full 40-character SHA by `git rev-parse HEAD`). Both worktrees reportedly clean; `main`/`origin/main` at `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`. If any drift exists, investigate and preserve it. No reset/rebase/merge/push or branch recreation.
- Preserve app identity and ordinary output: `0.9.8` / code `18`, `armeabi-v7a`, minSdk `24`, targetSdk `28`, `releaseSigningFrozen = true`, unmodified historical release/tag/assets, default `update/sources.json` empty and ordinary Debug/Release on existing DataStore/legacy UI. Do not install or sign a production APK.
- The new Library screen is **only** for verified consented/disposable authorized Debug test profiles under the existing R11–R13 gates. Do not add a second activation loophole, use real user profiles, seed demo content into ordinary storage, or create a second authoritative catalog/user-data writer.
- No physical/personal TV, external user's Android emulator, live CMS, real source scan, arbitrary source auto-discovery, or internet media requests. Use deterministic synthetic source/provenance fixtures exclusively in disposable owned AVD and host tests.
- Reuse the exact R10 query contract, R09 bounded query coordinator, R08 identity/provenance and R11 context snapshots. No client-side hidden fallback that drops a dimension, changes query on errors, or shows unverified matches. The screen may only list already locally checked `CatalogResult` items.
- Keep UI-authored copy and accessibility descriptions in Simplified Chinese. No software title/brand/watermark/marketing text, fake rating, fake content counts, or fake availability. Actual canonical original film titles from fixtures remain clearly marked test-only and cannot ship as app defaults.
- Visual components are truly native, accessible, reversible and composable. Reuse R13 `TvOriginalIcons`, `TvNavTile`, `TvPosterTile`, tokens, focus ring and motion budget rather than building an unrelated theme or copying external icons. Add a new icon only if strictly necessary, with original editable SVG + native vector mapping.
- **No T11-wide controller refactor**, complete detail/player redesign, subscriptions/settings implementation, diagnostics deletion, backup/enhancements, release signing or production migration. Preserve all Room `1.json` through `6.json` bytes. If a schema change seems essential, stop and report instead of silently widening the slice.
- Tests must exercise real Compose + Room/query on an identity-verified owned disposable API30 ARM64 AVD with pinned serial and package, as available. No debug-build timings should be reported as release-like benchmark acceptance. Name cases that could not run as BLOCKED, not PASS or N/A.
- Existing strict `contentKind`, `languageScope`, `countryIds`, `genreIds`, `yearStart`, `yearEnd`, `sort`, `enabledSourceIds` must survive UI editing, persistence, and app/activity restoration; no invisible loss of a field. `queryId` and context separation must be explicit.

## Review focus — five highest-risk input classes, with owning tests

1. **Filter panel cancel after editing a pre-2000 year plus country and genre:** applied query and deep list focus remain byte/semantic equivalent; add R14-B and R14-E real-native cancellation test.
2. **One source ignores year filter, first local page yields zero matches and a second page yields matches:** strict local verification shows only matching work, retains `hasMore`, avoids premature 'no results'; R14-C query+native test.
3. **Partial/failed/offline source with query results and unknown global count:** display matches, incomplete coverage and retry/load-more separately, never a final `共 N 部` claim; R14-C/R14-D state tests.
4. **Deferred layout, image callback or old query completion after D-pad navigation:** existing R11 restore token and R09 query generation stop focus teleport and stale content; R14-E race test.
5. **Screen-width/large-text constraints plus unknown metadata:** long selected chips, custom year range, portrait/missing posters and clear/close actions remain reachable by D-pad; R14-D real screenshots/semantics.

---

## File ownership map (actual source wins over suggested new names)

| Responsibility | Preferred/new path(s) | Exact ownership |
| --- | --- | --- |
| Narrow UI model | `app/src/main/kotlin/com/ichichuang/cinema/feature/library/LibraryUiState.kt` | Immutable projection of actual R09/10 query+coverage; no new catalog objects |
| Filter draft/controller | `.../feature/library/LibraryFilterDraftController.kt` | Draft vs applied, year validity, explicit apply/clear, IDs and preserved untouched fields; prefers existing R10 mutator if present |
| Filter panel | `.../feature/library/TvLibraryFilterPanel.kt` | Native Compose dialog/sheet with focus trap, D-pad accessible chips and year controls |
| Library screen | `.../feature/library/TvLibraryScreen.kt` | Visible results/coverage/empty/loading/retry and stable work actions using R13 components |
| Query adapter | Existing guarded R09–R13 nav/ViewModel integration file | Observe existing authoritative query; no direct sources, no additional writers |
| Focus/navigation | Existing R11 `BrowseState`/navigation coordinator | Context `library` and filter-sheet draft, deep work-ID anchor, back origin and stale restore handling |
| Shared design | Existing R13 `core/ui`, `core/design`, `design/icons` | Reuse first; avoid redundant token or third-party icon assets |
| JVM tests | Existing `app/src/test/kotlin/.../feature/library/` or project test convention | Query/draft/status mapping; TDD; no Android assumptions in pure projection |
| Android tests | Existing `app/src/androidTest/kotlin/.../feature/library/` | Actual Compose panel, D-pad, Room reopen, results, query transitions, screenshots |
| Evidence | `docs/verification/2026-10-25-round-14-library-filter-ui.md` plus `docs/architecture/tv-library-filter-ui.md` | Test method manifests, screenshots/semantics, relative coverage and exact limitations |

Do not invent final package paths or duplicate existing types. Begin by locating R13 Library destination callback, R10 strict-query builder and R11 browse context interfaces, then record any adaptations to this map. Do not change behavior outside the guarded route to simplify compilation.

### Required boundary (one actual implementation only)

The screen consumes a UI projection of the **existing** authoritative domain types, not raw `MaccmsSource` lists. If a new projection is required, it must at least retain these values and keep `CatalogQuery` intact:

```kotlin
internal data class LibraryUiState(
    val appliedQuery: CatalogQuery,
    val items: List<LibraryCardUi>,
    val coverage: LibraryCoverageUi,
    val filterSummary: List<LibraryFilterChipUi>,
    val canContinue: Boolean,
    val isLoading: Boolean,
)
```

`LibraryCardUi` must carry stable `workId` and genuine display title and optional artwork (plus no fake metadata), not title-derived keys. `LibraryCoverageUi` preserves complete/partial/failed/offline/unconfigured distinctions and nullable global total; do not create another source-state authority. `LibraryFilterChipUi` has a canonical field/value key and Chinese display text, not a localized value as the query key. Do **not** add these classes if R13 already has identical representations; use actual names discovered.

Compose boundaries should be analogous to:

```kotlin
@Composable internal fun TvLibraryScreen(
    state: LibraryUiState,
    onEditFilters: () -> Unit,
    onOpenWork: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Filter editing: load **the complete applied `CatalogQuery`**, modify a separate draft, confirm -> validate draft -> construct new stable query identity only when meaningfully changed -> atomically save/apply through R10+R11 mechanism -> issue a new R09 generation. A close/Back discards draft and restores the launch button, old query and old result anchor. Exact contract and implementation names must be reconciled against source; never use a second fake `CatalogRepository`.

## Task R14-A — Verify checkpoint and map actual data/navigation seams

**Files:** No application code yet; scoped plan import, existing R09/R10/R11/R13 sources, `docs/architecture/tv-library-filter-ui.md` first mapping section and targeted existing tests.

**Interfaces:** Consumes real `CatalogQuery`, `CatalogResult`, `CatalogRepository.observeQuery/loadMore`, R10 filter enforcement and R11 `BrowseSnapshot`, R13 destination handler. Produces an actual file/interface map and reproducible pre-change test commands.

- [ ] **Step 1:** Verify ZIP `SHA256SUMS` and actual full HEAD/branch/worktrees; record version/ABI/debug gate/schema hashes; do not alter user work.
- [ ] **Step 2:** Read the master spec, R10–R13 docs, and source for R13 Library destination, R10 query builder, R09 results and R11 restore. Determine whether R13's 片库 is already a functional placeholder, guarded view or legacy fallback; record one safe swap seam.
- [ ] **Step 3:** Run targeted existing R10/R11/R13 tests as genuine baseline (JVM and Android as available) and save stdout/test XML; record ordinary OFF behavior and any pre-existing failure.
- [ ] **Step 4:** Prepare exact test fixture identities/years/country/genres/source failures, and map all 52 acceptance rows to one owner. Document no additions to default user catalog.
- [ ] **Step 5:** Local planning/fixture changes commit only if there is a meaningful self-contained deliverable; never make placeholder/stub commits solely to mimic a cadence.

## Task R14-B — Strict filter draft, valid year controls and query identity

**Files:** Actual R10 query mutation location, possibly `feature/library/LibraryFilterDraftController.kt`; test `LibraryFilterDraftTest.kt` in JVM and panel native tests in R14-D.

**Interfaces:** Consumes/produces the **existing** `CatalogQuery`; no parallel filter model, no bypass of R10 matching. Exposes explicit begin-edit, change, apply, cancel, remove-chip and reset-all intents through actual type signatures discovered in R14-A.

- [ ] **Step 1 (RED):** Test `filters_combine_and_across_dimensions_or_within`: country `{JP,TH}` + genre `{HORROR,THRILLER}` + year 1994 accepts only proven matching entries, excluding unknown/conflict in a constrained field; verify actual expected test failure if functionality absent.
- [ ] **Step 2 (GREEN):** Reuse R10 exact filter semantics, update only the view-model/draft adapter required for editable complete queries; no `contains()` on localized text or `title + year` match.
- [ ] **Step 3 (RED/GREEN):** Test exact `1994`; decade `1980–1989`, including both ends and excluding `1990`; custom inclusive `1977–2006`; inverted years show a clear Chinese validation state and never activate a broad query. Implement an accessible D-pad year stepper/grid that can reach before 2000 without keyboard-only entry.
- [ ] **Step 4 (RED/GREEN):** Test `languageScope` semantics: 华语 vs 非华语 based on actual *work language* evidence; production country, Chinese audio and Chinese subtitles do not silently rewrite work language; incomplete/contradictory language evidence cannot satisfy strict scope. Test content kind, sort and enabled-source IDs survive draft/apply and Room v6 close/reopen.
- [ ] **Step 5 (RED/GREEN):** Test no-change Apply preserves query ID and anchor; meaningful Apply/Remove Chip/Clear All creates new query generation and explicit Chinese audit explanation, only at actual user intent; Cancel/Back leaves original query, active request, scroll and result focus untouched.
- [ ] **Step 6:** Re-run focused JVM tests, existing R10 strict tests, inspect non-year payload retention, and commit coherent query/draft tests and minimal implementation.

## Task R14-C — Truthful result projection, coverage and bounded pagination

**Files:** `feature/library/LibraryUiState.kt` or existing R13 projection; adapter to R09 catalog observe/loadMore; tests `LibraryCoverageUiTest.kt`, `LibraryQueryBridgeTest.kt` and real native counterpart.

**Interfaces:** `CatalogQuery -> CatalogRepository.observeQuery(query): Flow<CatalogResult>`; `loadMore(queryId)` returns existing `LoadOutcome`. Preserve exact R09 query token/generation. No direct network list assembly in Composable.

- [ ] **Step 1 (RED):** Seed disjoint synthetic sources: source A ignores server-side year, source B first page no matches then later 1994 matches; assert actual UI list shows only R10 verified 1994 matches, shows no fake all-source complete on page one.
- [ ] **Step 2 (GREEN):** Project actual verified results into focusable stable-ID UI items, keeping all proven playback entries and source failure information; call R09 loadMore by **current** query ID only. Never merge by normalized title/year or sum raw source counts.
- [ ] **Step 3 (RED/GREEN):** Test unconfigured (default empty), loading, complete-empty, partial results, budget-exhausted/incomplete, offline cached rows, failed-all-sources, one-source-failed and retryable states. Each state has genuine Chinese copy/action that does not claim full global availability.
- [ ] **Step 4 (RED/GREEN):** Test partial has unknown total (`null`), no `共 N 部` claim; if a fully verified *scope* total is known, label its scope, and never imply a global catalog size. A zero-matching page + `hasMore` cannot become confirmed empty.
- [ ] **Step 5 (RED/GREEN):** Switching filter generations cancels/ignores old results and loads; late page response and source failure cannot overwrite current query, focus or coverage. On cancellation preserve R09 `Cancelled` semantics rather than converting to an error banner.
- [ ] **Step 6:** Run focused JVM and R09/R10 regression, commit projection/bridge changes with real passing tests.

## Task R14-D — Native TV Library visual and filter panel

**Files:** `feature/library/TvLibraryScreen.kt`, `TvLibraryFilterPanel.kt`, existing R13 visual components and native test `TvLibraryFilterUiTest.kt`/`TvLibraryLayoutTest.kt`.

**Interfaces:** Pure render from `LibraryUiState`, callbacks to real R14-B/C logic; real focusable chips, source status/retry/continue controls; parent R13 nav remains authoritative.

- [ ] **Step 1 (RED):** Create native Compose test starting at authorized Library destination with synthetic fixtures. Assert the filter entry, applied chips and year at top, D-pad reachable country/genre multi-select, scope, sort, Apply and Cancel buttons; confirm semantics on real focused nodes rather than only a screenshot.
- [ ] **Step 2 (GREEN):** Build native Library using R13's restrained dark/warmer-focus design, single icon/token authority, poster cards and status components. Treat selected/current-focus/disabled visual states as three different concepts; avoid hidden-only/menu-only controls and keyboard-only year inputs.
- [ ] **Step 3 (RED/GREEN):** Filter sheet must trap focus above background; Back closes only top sheet and restores filter opener; while editing a draft a source/image callback must not alter active results. A confirm/held OK cycle triggers one action, no accidental double Apply/nav; long OK may not fire a short-press action first.
- [ ] **Step 4 (RED/GREEN):** Provide real focusable retry/continue/empty-state actions only when backing behavior exists. Loading, offline, failed, partial and no-source screens support Back/navigation without dead focus. Render unknown year/country/genre as unknown, not fake metadata.
- [ ] **Step 5:** Capture **actual AVD** screenshots and semantics at 1280×720 and 1920×1080 (when supported), big text, long Chinese title and chips, a pre-2000 year range, portrait-only and absent poster, reduced motion, partial/empty states. Inspect clipping, readable focus, no navigation overlay occlusion, dynamic poster aspect and reachable Apply/Back; screenshots are not a substitute for native behavior tests.
- [ ] **Step 6:** Review that all authored labels and TalkBack/semantics text are Chinese, no app title/logo/debug or fabricated ratings. If new vectors are required, author editable original SVG, native vectors and explicit comparison tests.
- [ ] **Step 7:** Run targeted native UI suite; commit visual slice only after observed tests pass. Leave full details/player/settings visual redesign for later rounds.

## Task R14-E — Real navigation, deep list restoration and process edges

**Files:** Existing R13 guarded nav bridge, R11 browse/focus coordinator and targeted instrumentation `TvLibraryNavigationTest.kt`/`TvLibraryRecoveryTest.kt`.

**Interfaces:** Use R11 context key `library` (or exact existing key), existing Room v6 atomic snapshots, stable work IDs and query identity; no new application-global writer. Back to the real source route.

- [ ] **Step 1 (RED/GREEN):** On a deep synthetic list: apply Japan+Horror+1994, focus work near index 87 (if fixture has 100+), enter detail, return; assert full `CatalogQuery`, stable focused work ID and scroll anchor, not merely index or first page. Do not claim deep coverage using 10 items.
- [ ] **Step 2 (RED/GREEN):** Switch between Library, Favorites, History and Home; verify independent contexts, selected vs focused tabs and no backdrop stealing. Opening sheet from deep item and Cancel restores original anchor; Apply intentionally starts new query and clears only context that query invalidates, not other routes.
- [ ] **Step 3 (RED/GREEN):** Confirm a changed query during old R09 request rejects old source pages; late image/layout callbacks cannot teleport focus after user D-pad; deleted anchor uses R11 adjacent-valid → section action → page default fallback, without jumping to unrelated items.
- [ ] **Step 4 (RED/GREEN):** Actual Activity recreation, background `am kill`, and separately verified `force-stop`/explicit launch after committed browse snapshot restore appropriate query and paused player state if entered; report each experiment separately with PID/XML/Room evidence. Uncommitted draft edits must not be invented as persisted user intent.
- [ ] **Step 5 (RED/GREEN):** Cache eviction/source failure does not change Room favorites, history or checkpoints; library browsing or opening detail cannot mark watched. Verify a work's true identity after source metadata change.
- [ ] **Step 6:** Execute targeted native suite with fixed-serial disposable AVD; mark external process steps independently from instrumentation method counts. Commit small reviewed changes with actual evidence.

## Task R14-F — Full verification, review, integrity and stop

**Files:** `docs/verification/2026-10-25-round-14-library-filter-ui.md`; architecture note, existing `docs/superpowers/progress/2026-10-23-tv-rebuild.md`; matrix status with evidence links.

**Interfaces:** Final test methods, evidence, hashes, review disposition; no feature expansion.

- [ ] **Step 1:** Execute actual `:app:testDebugUnitTest` and `python3 -m unittest discover -s scripts/tests -v`, saving counts and identifying unique tests. `314/349/351/370` from previous rounds are historical observations, **not** required target counts for this round.
- [ ] **Step 2:** Use R13's proven AVD/ARM64 authorization and fixed serial commands; run target and all relevant existing native suites (including OFF mode) with unique method start/end accounting. Avoid aggregate `connectedAndroidTest` against unknown devices. No device = explicit BLOCKED.
- [ ] **Step 3:** With all R13+R14 gates OFF, run actual Debug, unsigned Release, AndroidTest build and both Lints using repository flags `--no-daemon --no-configuration-cache` and `--rerun-tasks` where needed; compare *messages*, not just warning counts, to R13 baseline. Verify schema 1–6 file hashes unchanged.
- [ ] **Step 4:** Verify normal APK version, package/manifest/ABI/JNI, signing freeze, unchanged source catalog/update metadata, no test fixtures leaking to release, no production route or writer activation. Run `python3 scripts/check_repository_security.py` against the staged Git index and label it **index-scoped**, not historical scan.
- [ ] **Step 5:** Independent read-only review covering strict language/year semantics, query drafts, source-coverage honesty, D-pad focus/layer traps, screenshots, legacy gate and data safety. Fix important findings by RED→GREEN and re-run affected+full suites; independently re-review fixes if available, otherwise disclose absence. Existing warnings cannot be suppressed to appear clean.
- [ ] **Step 6:** Reconcile every matrix row `PASS/FAIL/BLOCKED/N/A` with exact executed method/artifact. Don't equate screenshots, matrix rows, and instrumented test counts. Record AVD serial, API/ABI, actual commands, image/Room fixtures, process PIDs, generated native screenshots, unresolved UI polish and real media limits.
- [ ] **Step 7:** Commit coherently in the existing local worktree; verify tested source hashes against staged/committed blobs, both worktree cleanliness and unchanged `main`; no push/merge/rebase/release. Stop after Round 14.

### Full regression commands (adjust only to actual repo tasks; log exact final invocation)

```bash
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check_repository_security.py
bash ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease --no-daemon --no-configuration-cache --rerun-tasks
```

For Android native tests **use only** the existing verified R13 fixed-serial per-package install and instrumentation procedure and report the precise observed commands; do not infer an instrumentation package name or runner from this plan. Run ordinary OFF behavior separately. Record serial before each test operation.

## Required delivery report (Simplified Chinese)

Report baseline full HEAD and final full HEAD, all local commits, diff/file list and reviewed source hash comparisons; scoped new features and existing components reused; query-language-year truth conditions and N/A decisions; actual JVM/Python/native method counts with zero/actual skips, full Gradle tasks and Lint warning **message differences**, index security scope, current schemas and normal artifact identity; AVD screenshots + accessibility/semantics evidence; source/candidate-data vs real production environment limits; the named `PASS/FAIL/BLOCKED/N/A` matrix with one-to-one evidence links; independent review findings, repairs and limitations. Explicitly keep complete T02/T06/T07/T08/T10/T11/M04 and A01/A03/A04/A12–A15/A19/A22 open until their respective full verification gates pass.

**Stop conditions:** Any gate bypass, default catalog mutation, production writer switch, historical schema alteration, full-query weakening, unknown-as-match, unapproved live CMS scan, loss/misassignment of personal records, physical-device access, or need to alter signing/ABI/version is a blocker. Preserve code and evidence and report rather than widening scope to hide the blocker.
