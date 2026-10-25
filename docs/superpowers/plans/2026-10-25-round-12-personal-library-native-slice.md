# Round 12 — Personal Library, Continue Watching and Native Action Semantics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` when available, otherwise `superpowers:executing-plans`. Execute task-by-task; each new production behavior begins with a failing behavioral test and ends with real GREEN verification, review and local commits. This is a bounded **T09 Debug native personal-library slice**, not full T09/M04, a production rollout, or approval to touch real user data.

**Goal:** On a verified disposable and explicitly authorized Debug test profile, make favorites, recently watched, per-episode status and continue watching behave as three distinct durable user-facing features across the actual Round 11 Compose list/detail/back path and existing candidate Media3/Room playback path, without affecting ordinary Debug/Release or legacy records.

**Architecture:** Reuse real Round 02–11 domain/storage contracts, Room v5, R05 playback session arbitration and per-episode checkpoints, R06 Media3 bridge, R07 single-writer handoff, R08 stable work identity/provenance, R09/10 exact candidate catalog queries and R11 route/focus restoration. Create the narrowest shared **personal-library projection and action coordinator** around existing stores; persist any missing watch-history, continue-hide or action-order state in Room using an explicit schema migration if necessary. Wire native Compose user actions and three top-level lists only into the currently guarded test route. Continue to treat legacy production as the only default active path.

**Tech Stack:** Existing Kotlin, coroutines/Flow, Room, Jetpack Compose/TV Material, Navigation, Media3, JUnit and native instrumentation; pinned Gradle/AGP/SDK/KSP versions, no arbitrary upgrades. Use a disposable API 30 ARM64 AVD with device-identity checks, plus JVM tests. No live network/media provider is needed.

**Spec:** `docs/superpowers/plans/2026-10-23-tv-experience-rebuild.md`, especially §§3.1, 5.1–5.4, 6, 10–11, T09 and synthetic portions of A06/A08–A11/A18. Reconcile `docs/architecture/tv-data-contracts.md`, `tv-storage-contracts.md`, `tv-playback-checkpoints.md`, `tv-playback-runtime-probe.md`, `tv-legacy-handoff.md`, `tv-catalog-identity.md`, `tv-navigation-restoration.md`, `docs/superpowers/progress/2026-10-23-tv-rebuild.md` and the actual local code before editing. The planned paths below are **ownership suggestions**, not claims about unseen, unpushed Kotlin source.

**Reported starting checkpoint (verify locally):** existing linked worktree `<isolated-worktree>`, branch `codex/contract-foundation`, HEAD `e9f6cc1875a8a2eb93060feb115cf3b74b9a9204`. The user reports both worktrees clean and `main`/`origin/main` at `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`. Never assume those facts hold without checking. Source commits are local and **not bundled in this package**.

## Global constraints

- **Scope:** A real, small native personal-library UI slice on the R11 test-only Compose navigation path, not merely three new mock lists. Show real Room-backed favorites/history/continue records and actions; keep the app's ordinary home/detail/player/DI/legacy DataStore behavior unchanged with every probe OFF.
- **Gate:** `cinema.personalLibraryProbe` if a dedicated flag is necessary must default OFF and only activate with verified Debug/DebugAndroidTest pairing, consent/ownership, disposable profile and compatible R11 `cinema.browseRestoreProbe`/R06 `cinema.playbackRoomProbe` writer authority. Reuse the existing flags if safer than another property. Missing/contradictory authorization fails closed. Reject Release/bundle/publish with any candidate gate ON before changes to outputs or devices. Never permit concurrent Room and DataStore progress/favorite writers.
- **User records are primary:** No favorites, history, per-episode progress, browse snapshots, unresolved legacy staging or trustworthy origin provenance may be erased by cache eviction, source disable/unavailability, metadata correction, merge/split or page refresh. A genuinely requested history deletion must never be silently reintroduced by a later legacy staging replay; specify the suppression policy and test it.
- **Semantics:** Favorites are deliberate user choice; history requires a **confirmed actual-play event**, never hover/detail/preload; continue is a separate unfinished/eligible-entry projection from acknowledged checkpoints. Missing facts and terminal conditions cannot be guessed. Keep per-episode and per-variant identities separate; watch state is not max(position) or arbitrary wall-clock sort.
- **Three operations, three scopes:** Remove from continue only hides its entry and leaves history/favorites/checkpoints intact; delete watching history removes the chosen work's actual history and corresponding resume/checkpoint state in the documented confirmation scope, but never favorites/catalog/staging; unfavorite removes only the favorite relation. After deletion, stale events/import retries must not resurrect removed rows without a new legitimate playback/action. Build a clear evidence-backed data contract; do not silently widen the deletion scope.
- **Resume promotion:** An existing acknowledged latest resume checkpoint must not be supplanted by a brief accidental play, preload, loading zero or invalid/old-session callback. Reuse R05 verified 5000ms candidate-session promotion policy unless actual code/spec shows a newer explicit decision; document exact threshold and test genuine new session vs replay of old events. After 'remove from continue', only a **new confirmed playback action** can make it visible again.
- **Favorites:** Set idempotently through existing `FavoritesStore.setFavorite` or its authentic implementation, do not invent a second favorite writer. UI must distinguish pending, committed, failed and undone. On `Failed` do not show durable success; a reversible unfavorite supports a short accessible Chinese undo action; restoring must be an actual storage commit, not just an optimistic icon.
- **History and completion:** 'Completed this episode' is not 'series completed'; if the latest known released episode is watched with no reliable terminal data, show `已看至最新` or specific episode, not `全剧完结`. Preload/prepare alone never marks an episode watched. Retry keeps effective checkpoint. Switching playback variant never silently assumes identical timeline; when unknown prompt to resume cautiously/from start or choose, preserving old checkpoint.
- **Offline/unavailable:** Lists of personal records render from Room without source/network access; use saved minimal identification data and truthful `暂不可播放`/`来源不可用` for missing media. No fake content, generated user history, invented global totals, forced network requests or presumed download for offline playback.
- **UI/navigation:** All app-authored labels, action affordances, accessibility names, failure and confirmation strings are in Simplified Chinese. No app name, logo or watermark. Reuse R11 independent Library/Favorites/History context, focus/scroll origin, user-input-cancels-late-restore, and actual D-pad/OK/Back. A removed item redirects focus to a provably valid neighbor or action, not another work or first item unexpectedly. No automatic audio on restart.
- **Identity:** Preserve stable work identity and original source evidence; for multi-source/variant works, do not infer equality by title/year. If association is corrected or split, personal reference stays with its original opaque identity unless a separately authorized remap rule is proven. Source disable must not cascade-delete user rows.
- **Persistence:** Reuse Room v5 if exact semantics can be represented in existing persistent structures. If new durable visibility, user-action or history entities are needed, create **explicit v5→v6 migration**, generated `6.json`, real seeded v5→v6 tests of all existing 22 tables (verify actual count; reported v5 has 22 after 19+3), foreign-key checks, prior schema SHA-256 preservation and reopen. NEVER mutate schema 1–5 or enable destructive migration. The old DataStore stays intact.
- **Safety:** Preserve package identity, version `0.9.8`/code `18`, minSdk/targetSdk, ordinary `armeabi-v7a`, release signing frozen, update JSON/assets, tags, private visibility and existing releases. Do not install on physical/personal devices, scan real CMS or remote resources, push/merge/rebase, sign or publish. Test fixtures belong to test/debug-only isolation; no sample movie/demo user records visible in ordinary builds.
- **Evidence:** Count unique native method starts and outcomes, reconcile code, test APK, simulator serial and executable gates, not historic sums. Report honest blocked vs failed vs not-run. Full T03–T09/M02/M04 and A06–A11/A18 remain open for production/real-user/ARMv7 TV validation.

## Review focus — five high-risk user-visible failures

1. **Delete-scope confusion:** `remove_from_continue_preserves_history_favorite_and_checkpoint` and `delete_history_preserves_favorite_but_removes_resume` prove the three commands cannot corrupt unrelated records (R12-C).
2. **Stale resurrection:** `old_playback_callback_cannot_resurrect_deleted_history` and `dismissed_resume_requires_new_confirmed_session` verify late events and staging retry cannot repopulate user-removed records (R12-C/D).
3. **False success under I/O fault:** `favorite_failure_rolls_back_visible_state` and `undo_unfavorite_requires_commit` verify UI and storage reconcile honestly (R12-C/E).
4. **Wrong completion/episode:** `preload_does_not_create_history` and `latest_episode_without_finality_is_not_series_complete` distinguish playback state from metadata guesswork (R12-B/D).
5. **Focus/back after mutation:** `unfavorite_focused_card_uses_valid_neighbor` and `history_to_player_back_restores_history_context` assert that deleting a card, app recreation and source changes cannot steal D-pad focus or jump Home (R12-E).

---

## File map — verify real Round 11 source first

| Responsibility | Proposed owner / reference | Requirements |
| --- | --- | --- |
| Candidate user data and action coordination | `app/src/main/kotlin/com/ichichuang/cinema/data/userdata/PersonalLibraryRepository.kt` and `PersonalLibraryActions.kt` | One durable projection over existing favorites and acknowledged playback/session records, with explicit delete scopes and truthfully surfaced commit results. Prefer existing implementations if already present; no duplicate writer. |
| History/event state, continue dismissal | `data/userdata/` focused files plus `data/local/` entities, DAO and migrations **only if v5 lacks required durable fields** | Persist confirmed-play events, immutable order identity, hide/delete suppression, completed-per-episode status and last known source identification without cascading user-data loss. |
| Presentation state | `feature/favorites/`, `feature/history/`, optionally the home feature directory test-owned continuation component | Room-backed Chinese `加载中/无记录/离线/保存失败/暂无可播放来源` states and stable-ID D-pad actions; no final visual redesign. |
| Candidate navigation | Existing R11 gated Compose list/detail/back seam and `core/navigation/` restoration | Preserve independent context and focus after mutation; no changes to ordinary `CinemaNavHost` route behavior with OFF gates. |
| Existing playback | Actual R05/R06 coordinator, `PlaybackProgressStore`, Media3 bridge, player/detail | Reuse original ordered checkpoint/retry/paused restoration behavior. Do not introduce another clock/session arbitrator. |
| Existing favorites | Actual `FavoritesStore.setFavorite`/Room favorite DAO | Idempotent storage commit and honest failure, stable ordering and genuine undo. |
| Tests and verification | `app/src/test/.../UserLibrarySemanticsTest.kt`, `app/src/androidTest/.../PersonalLibraryNativeTest.kt`, targeted migration tests, external process harness | Use actual project packages and test facilities, pinned owned AVD, real file-backed Room/Compose/Media3, explicit close/reopen and PID evidence. |
| Documentation | `docs/architecture/tv-personal-library.md`, `docs/verification/2026-10-25-round-12-personal-library.md`, existing progress ledger | Present exact storage shape, action table, gate combinations, test identity/cases, blocked real-world claims and review corrections. |

**Interface decision, contingent on real APIs:** Prefer composing `FavoritesStore.setFavorite(workId, selected): CommitResult`, R05 acknowledged playback snapshots and a single user library projector. Proposed bounded facade if genuinely absent:

```kotlin
interface PersonalLibraryActions {
    suspend fun removeFromContinue(workId: String): CommitResult
    suspend fun deleteWatchHistory(workId: String): CommitResult
}
```

`observeFavorites`, `observeRecentHistory`, `observeContinueWatching` may return `Flow` through one owned projection; use the actual existing card/entity types and expose availability/coverage independently. Do not add these exact names or signatures if a tested existing contract covers them. Persist action outcomes before emitting success. Cancellation propagates, and `RejectedStale` is not a successful mutation.

## Task R12-A — Verify baseline, invariants, and isolated native UI gate

**Files:** Existing build gates, R11 navigation source, progress ledger, small new scoped plan and validation tests only where necessary.

**Interfaces:** Consumes actual R11 native route/focus coordinator, R07 writer gate and R06 playback probe; produces a documented guarded entry for only the R12 personal-library route.

- [ ] **Step 1 — Baseline inspect.** Verify branch HEAD, both worktrees, uncommitted changes, `main`, actual files and real Kotlin interfaces. Run existing JVM/Python baseline and inspect native fixtures; if mismatched, record drift and preserve local user work, no reset.
- [ ] **Step 2 — RED gate tests.** `personal_library_probe_off_preserves_legacy`, `probe_on_without_owned_profile_fails_closed`, `candidate_gate_rejects_release_and_invalid_pairing`, `legacy_records_cannot_be_silently_switched` must show the target behavior is currently absent/unsafe before modifications; distinguish tests already green because previous safety gates exist.
- [ ] **Step 3 — Implement minimal authorization.** Reuse R11 route rather than adding a parallel NavHost; introduce an extra default-OFF capability flag only if required. Ensure it cannot silently open user data, second writer or production test fixtures.
- [ ] **Step 4 — GREEN, baseline fingerprint, commit.** Targeted gate tests pass; retain reportable artifact hashes for normal Debug/unsigned Release with all flags OFF. Never presume build byte identity if gated code itself changes.

**Acceptance:** Only explicitly authorized owned Debug profile can reach the R12 path. Ordinary runtime remains unchanged.

## Task R12-B — Consistent derived personal-library state, real history evidence

**Files:** `data/userdata/` projection, focused entities/DAO as needed, JVM+Room tests.

**Interfaces:** Consumes real committed playback/session/episode rows, stable work identity/captures and existing favorites; produces three independent ordered observation streams with durable record identities and known/unknown playback/source state.

- [ ] **Step 1 — RED projector tests.** `preload_does_not_create_history`, `preview_or_detail_does_not_create_history`, `actual_play_writes_history_once`, `separate_episodes_keep_distinct_positions`, `brief_accidental_play_does_not_replace_resume`, `latest_episode_without_finality_is_not_series_complete`, `source_disabled_keeps_personal_cards`, `offline_cards_use_saved_real_labels`. Assert exact IDs, persisted positions and known/unknown states, not just counts.
- [ ] **Step 2 — Inspect storage.** Determine whether R05/R07 confirmed media-start evidence, R02 favorite order and Room v5 histories exist. State which evidence is durable and which isn't. If history cannot be proven by progress row alone, persist the minimal confirmed-watch event with session+operation identity; no made-up record for pre-migration sessions.
- [ ] **Step 3 — Implement projection and durable ordering.** Favorites sort by committed favorite operation, recent history by confirmed playback event order and continue by acknowledged eligible resume promotion; do not use filename, title, wall-clock alone or view opening. Restore locale-correct labels without misrepresenting source metadata; retain stopped/finished/unknown states separately.
- [ ] **Step 4 — GREEN tests and commit.** Test pending sources, offline Room reopen and same work with several episodes and variants. A completed episode does not complete whole series unless explicit evidence exists.

**Acceptance:** Three lists are based on durable independent semantics, not repeated views of one `lastMovieId` value.

## Task R12-C — Three non-overlapping actions, failures and resurrection protection

**Files:** Action coordinator, DAO/transactions, JVM and real native Room tests.

**Interfaces:** Consumes R12-B projector, authoritative favorite and playback stores; produces durable idempotent `removeFromContinue`, `deleteWatchHistory`, `setFavorite` actions with operation revision or transaction-safe equivalent.

- [ ] **Step 1 — RED deletion-scope tests.** `remove_from_continue_preserves_history_favorite_and_checkpoint`, `delete_history_preserves_favorite_but_removes_resume`, `unfavorite_preserves_history_and_progress`, `clear_cache_cannot_delete_personal_records`, `source_disable_cannot_delete_user_data` with file-backed Room, direct row assertions, and post-reopen verification.
- [ ] **Step 2 — RED stale/failure tests.** `dismissed_resume_requires_new_confirmed_session`, `old_playback_callback_cannot_resurrect_deleted_history`, `legacy_reimport_cannot_resurrect_explicit_delete`, `favorite_failure_rolls_back_visible_state`, `undo_unfavorite_requires_commit`, `cancelled_action_does_not_report_success`. Inject storage failure and cancellation **without filling host disk**; distinguish before-commit rollback from acknowledged after-commit outcome.
- [ ] **Step 3 — Implement minimum atomic semantics.** Persistent visibility/deletion revision or tombstone only when necessary; do not delete other tables or rewrite stable work references. Serialize conflicting user actions by revision. Hide-only affects continue projection, while a new *genuine confirmed play* after removal re-eligibilizes. History deletion must remove only its documented scope of playback/history and prevent replay of stale events; retain favorites and auditable staging according to user-data policy. Undo favorite/unfavorite is a real inverse transaction, bounded by action identity; a prior I/O failure must not show the new durable state.
- [ ] **Step 4 — GREEN + native SQL/file assertions, commit.** Test two concurrent taps, repeated idempotent operations, resume after force-stop, source alias/split and DB reopen. Ensure deletion of one work/episode cannot wipe another.

**Acceptance:** UI-visible and persisted actions match the distinct semantics after failures, retries, cancellation and restart.

## Task R12-D — Optional Room v5→v6 schema migration, only if necessary

**Files:** Room entities/DAO/migration registration, generated schema history, androidTest migrations.

**Interfaces:** Supplies durable state needed by R12-B/C; keeps all preexisting data, foreign keys, versioned payload and canonical IDs.

- [ ] **Step 1 — Decision record.** List every v5 table and field from actual `5.json`. If existing schema supports reliable delete/hide/event state without inappropriate overloaded payloads, use v5 unchanged and record `MIG-02=N/A`; do not create schema v6 merely to increment a number.
- [ ] **Step 2 — If required: real migration RED test.** Seed **actual v5 schema** with favorites, progress across episodes/variants, browse library/favorites/history contexts, raw source evidence, identity links, subscription state, legacy staging/handoff flags and candidate query payload; verify `v5_to_v6_preserves_seeded_user_data` cannot pass before implementing the migration. Verify all actual tables by name, not an assumed count.
- [ ] **Step 3 — Implement migration.** Create only necessary `data/local/` table(s)/indices, explicit `Migration(5,6)`, KSP-generated `6.json`, retain original 1–5 schema bytes; keep old migration paths and never call destructive migration.
- [ ] **Step 4 — GREEN native checks and commit.** Real file-backed migration, foreign-key check, schema identity, data snapshot equality, reopen, fault rollback and repeated open; verify ordinary OFF build still uses unchanged user data authority.

**Acceptance:** One honest outcome: either v5 reused with no invented migration, or a real native-tested v5→v6 preserving all prior rows and schema files.

## Task R12-E — Real native Compose actions, player and navigation restoration

**Files:** Existing R11 test-gated navigation seam, `feature/favorites/`, `feature/history/`, bounded continue list test view, direct/native UI tests and local synthetic media.

**Interfaces:** R12-B/C streams and actions, R11 stable focus/origin coordinator, R06 actual Media3 bridge and R05 checkpoint store. No parallel playback session controller.

- [ ] **Step 1 — RED UI tests.** `focusable_favorite_opens_detail_and_returns`, `unfavorite_focused_card_uses_valid_neighbor`, `history_to_player_back_restores_history_context`, `continue_click_uses_committed_episode_and_pauses_on_restore`, `favorite_failure_rolls_back_visible_state`, `undo_unfavorite_requires_commit`, `empty_and_offline_chinese_states_are_truthful`, `dpad_reaches_remove_actions_without_menu_key`. Use actual Compose semantics and injected remote key events, not only screen rendering.
- [ ] **Step 2 — Minimal real test UI.** Extend R11 guarded path to show the three genuine Room projections (favorites, recently watched, continue) and a detail-level favorite toggle; present concrete Chinese `移出继续观看`, `删除观看记录`, `取消收藏` with scope explanations/confirmation for destructive history and accessible undo for unfavorite. Keep focus/selection different. Do not pre-implement T10 branding, icons or visual layout; avoid duplicating a test-only UI in production code.
- [ ] **Step 3 — Real media and watch semantics.** Use only local synthetic WAV/video where previously verified; navigate detail→player in authorized disposable profile. Confirm actual media play event before history changes; preload/prepare never creates history. Resume from acknowledged episode/variant/position; retry retains checkpoint; differing/unknown source-version timeline cannot auto-map position. App restart must remain paused.
- [ ] **Step 4 — Native failure/lifecycle.** Invoke storage failure while focused on a favorite/action, verify visible rollback/error and focus stability. Run real Activity recreate, app process kill with new PID, and **externally orchestrated** force-stop + explicit launch as separate cases when supported, including no resurrection after delete. Do not claim a plain VM re-creation proves force-stop recovery. During long async result changes, D-pad input invalidates delayed restore.
- [ ] **Step 5 — GREEN and bounded review commit.** Re-run all new instrumented native methods. Track source→test APK→actual serial/runner/event trace; never count compiled-only or unexecuted tests as passed.

**Acceptance:** One actual native local end-to-end personal-library flow, independent navigation contexts, correct durable actions and paused restore on a verified synthetic test profile.

## Task R12-F — Regression, independent review, evidence and stop

**Files:** `docs/verification/2026-10-25-round-12-personal-library.md`, `docs/architecture/tv-personal-library.md`, progress ledger and local commits.

- [ ] **Step 1 — AVD preflight.** Use self-owned isolated API30 ARM64 AVD, pin serial, check `ro.kernel.qemu`, ABI/API/image, ownership and target package before **every** install/execution. Never assume all `adb devices` are disposable. Existing R11 R05/07 probes must remain isolated.
- [ ] **Step 2 — Matrix reconciliation.** For each `PERSONAL-LIBRARY-ACCEPTANCE-MATRIX.md` row, record actual test method, command, run target, result, assertion and issue/commit. Reconcile instrumentation source unique methods against starts/outcomes, prevent double count. Distinguish JVM, native, external PID and inherited historical evidence.
- [ ] **Step 3 — Full ordinary-OFF regression.** With all candidate properties OFF run `python3 -m unittest discover -s scripts/tests -v`; `:app:testDebugUnitTest`; Debug, unsigned Release, AndroidTest APK builds; `:app:lintDebug :app:lintRelease` with `--no-daemon --no-configuration-cache` (and `--rerun-tasks` when required to prove execution). Execute relevant prior native suites on pinned disposable serial. Record exact counts, warning *message* diffs, ordinary package ID/version/ARMv7, signature freeze, updates, default empty source catalog and user-writer authority.
- [ ] **Step 4 — Review and staged security.** Stage only intended code/schema/docs. Run `python3 scripts/check_repository_security.py` and state it inspects the current Git index, **not history**. Check credentials/fixture leakage, release guards, schema bytes, diff, working tree. Obtain fresh independent read-only review targeting false history, delete scope, stale resurrection, cache cascade, favorite rollback, UI focus and migration; reproduce Important/Critical findings RED→GREEN and full reruns; record whether follow-up review was performed.
- [ ] **Step 5 — Finish and stop.** Commit locally only, keep both worktrees safe, verify committed code against tested candidate and record baseline/final HEAD, changed paths, actual native counts/runner/serial, DB version and schema fingerprint, review, known limitations. Update T09 **candidate native slice only**; full T09, T03/T04 closure, T10, M02/M04 and A06/A08–A11/A18 remain open for real user data, ARMv7/API24 TV, default production route and physical remote. Do not push, merge, publish or enable production signing.

## PASS / BLOCKED / NOT CLAIMED

**PASS (bounded):** With test-only authorized native Debug profile, real Room rows drive Favorites/Recent/Continue and their three actions, real Media3 play events create appropriately scoped history/progress, D-pad navigation stays stable across action/restart, negative tests prove no loss across deletion/failure/offline/refresh, native data survive close-reopen and separate process. Ordinary OFF builds retain legacy behavior and all user records.

**BLOCKED:** Existing candidate stores cannot represent confirmed watch or bounded deletes without a safe schema change; gate/writer mismatch; unable to obtain explicit self-owned AVD or confirm identity; native tests never execute; any deletion loses unrelated history/favorites/progress; recovered app auto-plays; loss or change to ordinary runtime, source catalog, ABI/signing, or legacy DataStore. Report partial results truthfully instead of bypassing a gate.

**NOT CLAIMED:** Full T09/M04/A06–A11, actual old-user migration, real CMS/variant timeline equivalence, true offline media playback, production UI, original icon system, ARMv7/API24 or physical TV remote input, arbitrary power-loss recovery, complete T02/T08 or signed release.
