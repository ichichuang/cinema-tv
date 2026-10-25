# Round 15 — Native Detail, Lines and Episodes Acceptance Matrix (56 cases)

**Scope:** bounded T10 Detail native UI and T09/T08/T11 action/focus integration, only in owned consented Debug test profiles. This document is a **pre-execution checklist**, not a claim that tests ran. Rows begin `NOT RUN`; only Codex can mark their eventual outcomes based on actual evidence. Ordinary Debug/Release, real CMS, physical TV and production signing remain outside the round.

**Reported starting checkpoint to verify:** `827b3983f8271b3f35eac0e6996a9dac38ea8392` on `codex/contract-foundation`. Refer to the scoped plan for source ownership, test-first process and safety gates.

**Counts:** G 6 + P 9 + E 10 + A 9 + N 10 + L 7 + R 5 = **56**. `JVM` host test; `NATIVE` instrumented actual Android; `ROOM` actual file-backed database; `MEDIA3` real local fixture playback; `PROC` separately orchestrated process; `SHOT` real AVD screenshot and semantics; `SEC` inspected code/artifact; `BUILD` executed Gradle; `REVIEW` independent read-only source review. Matrix scenarios are not identical to Android test method counts.

**Ledger fields for each case:** PASS/FAIL/BLOCKED/N/A; *new* RED failure and GREEN command if implementation changed; real method name and assertion; exact command/task, test pairing/serial; evidence path, screenshot or source artifact, skip status and reviewer disposition. Never label an unperformed device test PASS, and never call assumption-skipped methods successful.

## G — Authorization, probe isolation and personal-data protection (6)

| ID | Test / evidence key | Actual required assertion | Required evidence | Status |
| --- | --- | --- | --- | --- |
| G01 | `ordinary_debug_stays_legacy` | Ordinary Debug with all probes OFF preserves original detail/nav/DataStore and does not instantiate a second writer | NATIVE/BUILD | NOT RUN |
| G02 | `release_cannot_activate_detail` | Release/bundle/prohibited task pairs cannot enable detail probe or unfreeze signing | BUILD/SEC | NOT RUN |
| G03 | `authorized_debug_profile_opens_detail` | Owned consented disposable profile reaches new detail through existing R14 guarded Library | NATIVE | NOT RUN |
| G04 | `unauthorized_profile_refuses_detail` | Missing consent/profile/token prevents candidate screen, synthetic fixture writes and guarded media launch | NATIVE/SEC | NOT RUN |
| G05 | `default_catalog_remains_empty` | Fresh unconfigured profile sees truthful no-source/detail fallback; no demo films, fake routes, ratings or play claims | NATIVE/SHOT | NOT RUN |
| G06 | `personal_records_keep_one_writer` | Entering or leaving detail cannot create second Room/DataStore writer or modify personal records absent explicit action | NATIVE/SEC | NOT RUN |

## P — Stable work identity, provenance and truthful metadata (9)

| ID | Test / evidence key | Actual required assertion | Required evidence | Status |
| --- | --- | --- | --- | --- |
| P01 | `canonical_work_id_drives_detail` | Title collisions and same title/year remakes open separate stable work IDs | JVM/NATIVE | NOT RUN |
| P02 | `source_entries_are_provenance_bound` | Every displayed source/metadata detail retains actual evidence and provenance rather than UI inference | JVM | NOT RUN |
| P03 | `unknown_year_is_not_filled` | Unknown/known-empty/missing year produces Chinese unknown state, never a guessed year | JVM/NATIVE | NOT RUN |
| P04 | `conflicted_fields_are_not_silent_facts` | Conflicting original language/country/year is signaled as unresolved instead of arbitrarily resolved | JVM/NATIVE | NOT RUN |
| P05 | `audio_subtitles_not_work_language` | Chinese dub or subtitle does not redefine non-Chinese work language | JVM | NOT RUN |
| P06 | `same_work_all_distinct_routes_preserved` | Identity-confirmed same work keeps each source entry and playback variant separately addressable | JVM/NATIVE | NOT RUN |
| P07 | `missing_portrait_image_truthful` | No poster uses title placeholder; portrait aspect remains correct, no fake art/cast info | NATIVE/SHOT | NOT RUN |
| P08 | `source_status_freshness_disclosed` | Stale/offline/disabled/unverified data does not become a guaranteed playable claim | JVM/NATIVE | NOT RUN |
| P09 | `not_finished_without_work_evidence` | Watched current/latest episode does not imply all-series completion, fake episode total or rating | JVM/NATIVE | NOT RUN |

## E — Playback lines, episode identity and chooser (10)

| ID | Test / evidence key | Actual required assertion | Required evidence | Status |
| --- | --- | --- | --- | --- |
| E01 | `same_label_distinct_route_ids` | Two lines with identical display names and distinct IDs remain selectable without merging | JVM/NATIVE | NOT RUN |
| E02 | `disabled_or_unreachable_line_semantics` | Unavailable route remains visible with truthful label and cannot be launched as playable | NATIVE | NOT RUN |
| E03 | `long_source_name_is_reachable` | Long Chinese route names do not clip focus/action or hide route identity | NATIVE/SHOT | NOT RUN |
| E04 | `episode_progress_is_variant_scoped` | Progress for the same episode label on another variant remains independently keyed | JVM/NATIVE | NOT RUN |
| E05 | `unknown_episode_number_no_fabrication` | Unknown episode order/name is not invented from array index and source wording remains truthful | JVM/NATIVE | NOT RUN |
| E06 | `episode_picker_dpad_and_back` | D-pad opens/navigates/closes picker; focus trapped; Back restores triggering control | NATIVE | NOT RUN |
| E07 | `switch_line_does_not_copy_resume` | Switching selected variant does not silently apply another variant’s 8000ms resume checkpoint | JVM/NATIVE | NOT RUN |
| E08 | `old_episode_history_survives_line_switch` | Switching lines/episodes preserves records, favorite and existing resume target unless real user play changes them | NATIVE | NOT RUN |
| E09 | `empty_routes_no_fake_episode_one` | Zero routes/episodes disables play with actionable Chinese status and no imaginary first episode | NATIVE/SHOT | NOT RUN |
| E10 | `stale_line_refresh_never_overwrites_choice` | Late or canceled source refresh cannot replace newly selected episode/line or steal focus | JVM/NATIVE | NOT RUN |

## A — Play/continue, favorite and failure behavior (9)

| ID | Test / evidence key | Actual required assertion | Required evidence | Status |
| --- | --- | --- | --- | --- |
| A01 | `resume_targets_exact_episode_variant_position` | Committed 8000ms point resumes the same work, episode and variant, not an inferred source | NATIVE/MEDIA3 | NOT RUN |
| A02 | `restore_paused_until_user_confirms_play` | Reopen from details with checkpoint remains silent/paused; playback starts only on explicit confirmed action | NATIVE/PROC | NOT RUN |
| A03 | `detail_browse_does_not_write_history` | Open/focus/detail scroll/poster load/picker cancel/preload creates no watch event, continue or checkpoint mutation | NATIVE/ROOM | NOT RUN |
| A04 | `explicit_play_uses_selected_verified_route` | Confirmed play passes exact current route identity to existing guarded Media3 and Room authority | NATIVE/MEDIA3 | NOT RUN |
| A05 | `unavailable_play_disables_without_fallback` | Unavailable/missing entry never auto-falls back to a different line or fabricates URL | JVM/NATIVE | NOT RUN |
| A06 | `favorite_toggles_only_canonical_work` | Favorite uses original stable work ID, does not affect history/progress and survives offline source state | NATIVE/ROOM | NOT RUN |
| A07 | `favorite_sql_failure_rolls_back_ui` | Failed favorite commit reverts claimed state, shows Chinese retry option and preserves DB state | NATIVE/ROOM | NOT RUN |
| A08 | `favorite_lost_ack_retry_idempotent` | Commit succeeded but acknowledgment lost: retry converges to intended state, never reverse-toggles | NATIVE/ROOM | NOT RUN |
| A09 | `media_error_preserves_resume_and_route` | Real local media failure cannot silently switch route, zero checkpoint or mark watched without confirmation | NATIVE/MEDIA3 | NOT RUN |

## N — Origin, D-pad, focus and lifecycle (10)

| ID | Test / evidence key | Actual required assertion | Required evidence | Status |
| --- | --- | --- | --- | --- |
| N01 | `detail_back_returns_actual_origin` | Detail opened from Home/Library/Favorites/History returns to its correct origin | NATIVE | NOT RUN |
| N02 | `deep_library_query_and_anchor_preserved` | Deep Library 1994/JP/Horror context and focused work restored after detail Back | NATIVE | NOT RUN |
| N03 | `picker_focus_trap_and_opener_restore` | Episode/line top layer prevents background key reception; dismiss restores actual opener | NATIVE | NOT RUN |
| N04 | `user_input_cancels_late_focus` | User D-pad navigation revokes delayed image/data/layout focus-restore request | NATIVE | NOT RUN |
| N05 | `back_unwinds_exactly_one_layer` | Picker → Detail → Player → Detail → source route Back stack unwinds one layer at a time | NATIVE | NOT RUN |
| N06 | `held_ok_one_action_and_long_ok_safe` | One physical OK cycle triggers at most one action, long press does not execute short press first | NATIVE | NOT RUN |
| N07 | `empty_or_removed_route_focus_fallback` | When selected route disappears, focuses nearest valid choice/control rather than jumping unrelated Home | NATIVE | NOT RUN |
| N08 | `activity_recreate_restores_committed_detail` | Actual Activity recreation restores committed detail identity/origin and valid focus, not uncommitted picker action | NATIVE | NOT RUN |
| N09 | `external_restart_restores_committed_pause` | Separate process PIDs with real force-stop/relaunch recover committed detail and paused resume | PROC/NATIVE | NOT RUN |
| N10 | `r14_n09_missing_deep_anchor_native_assertion` | New direct native assertion removes focused deep-list work and observes a specifically expected adjacent stable-ID focus | NATIVE/XML | NOT RUN |

## L — Original TV visuals, content honesty and accessibility (7)

| ID | Test / evidence key | Actual required assertion | Required evidence | Status |
| --- | --- | --- | --- | --- |
| L01 | `detail_1280_720_no_clipped_actions` | Actual 1280×720 screenshot shows usable actions, long title and episode entry without crop | SHOT/NATIVE | NOT RUN |
| L02 | `detail_1920_1080_render_verified` | Actual 1920×1080 screenshot and semantics have correct alignment and visible focus | SHOT/NATIVE | NOT RUN |
| L03 | `large_text_long_title_and_episode` | Large font, long source labels and long Chinese episode names keep every primary operation reachable | SHOT/NATIVE | NOT RUN |
| L04 | `portrait_and_no_poster_states` | Portrait fit and missing-poster status retain true title; image loads do not hijack focus | SHOT/NATIVE | NOT RUN |
| L05 | `reduced_motion_interaction_not_blocked` | Reduced motion retains feedback, no animation queue preventing directional or Back input | NATIVE/SHOT | NOT RUN |
| L06 | `simplified_chinese_no_brand_or_false_badges` | UI-authored copy/semantics Chinese and no software brand, fake popularity, episode total, rating or diagnostic ID | NATIVE/SHOT/SEC | NOT RUN |
| L07 | `original_vector_authority_remains_single` | Reuses original R13 icons/tokens; any added icon has editable SVG and matching native vector and accessibility name | SEC/SHOT | NOT RUN |

## R — Regression, safety, traceability and review (5)

| ID | Test / evidence key | Actual required assertion | Required evidence | Status |
| --- | --- | --- | --- | --- |
| R01 | `room_schemas_1_to_6_unchanged` | All stored Room v1–v6 exported schema bytes unchanged; no unreviewed migration or destructive fallback | SEC/BUILD | NOT RUN |
| R02 | `ordinary_artifacts_identity_unchanged` | Normal Debug/unsigned Release/AndroidTest identity, 0.9.8/18, ARMv7, signing freeze and source catalog remain baseline | BUILD/SEC | NOT RUN |
| R03 | `jvm_native_python_lint_actual_runs` | Run actual complete suites with native method IDs, build/runner/target serial and warning-message diffs recorded | JVM/NATIVE/BUILD | NOT RUN |
| R04 | `independent_review_and_committed_hashes` | Independent read-only review, important RED→GREEN fixes and tested/staged/committed blobs reconciled; local commit only | REVIEW/SEC | NOT RUN |
| R05 | `prior_assumption_skips_disclosed` | Five previous R14 assumption skips kept separate from PASS; no extrapolated complete native coverage | NATIVE/REPORT | NOT RUN |

## Exit rules

- A case is `N/A` only if its precondition demonstrably does not exist (not merely because equipment, time or code is missing); record a precise justification. Otherwise use `BLOCKED` or `FAIL`.
- A passing source/unit projection test does not establish an actual Compose/Media3/Room behavior unless the row explicitly permits JVM-only evidence.
- Native counted totals must distinguish discovered unique test methods, started, passed, failed, assumption-skipped, ignored, timed out and not run. The five existing Round 14 assumption skips must remain visible, even if unrelated regressions still pass.
- The R14 N09 limitation is closed only by a **real native focus identity** assertion after removal, not by repeating an algorithm-only unit test.
- All guards and media test data must remain isolated; no live source contact, user-profile access, physical TV or production release operation.
- Completion of this matrix can validate only the authorized Detail slice; entire T02/T03–T11/M04 and A01–A24 remain gated until their full requirements are independently satisfied.
