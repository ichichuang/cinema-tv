# Round 16 — Player Controls / Remote Acceptance Matrix

**Initial state: all 60 checks NOT RUN.** A check is a testable claim, not necessarily one test method; an actual PASS must cite concrete test method(s), actual command, device serial and persisted evidence where required. `N/A` is only permitted when the relevant precondition provably does not exist, not when a test was skipped or a tool is missing. Distinguish FAIL, BLOCKED and assumption-skip explicitly.

Evidence codes: `JVM` host unit, `NATIVE` actual Android instrumentation, `MEDIA3` real player, `ROOM` real file database, `PROC` independent-PID external process orchestration, `XML` real focused accessibility/semantics tree, `SHOT` actual device screenshot with pixel/layout checks, `BUILD` actual Gradle output, `SEC` manifest/source/index/ABI hashes, `GIT` local commit proof, `REVIEW` independent read-only analysis, `REPORT` auditable record.

**Fixture limitations:** WAV proves Media3 audio state but not video decoding. If no legal locally generated video fixture can render, keep R03 `BLOCKED`. Keep five previously seen R14 `assumption` skips distinct from PASS even if all new assertions pass.

## G — Authorization, baseline and isolation (7)

| ID | Test or proof key | Required assertion | Minimum evidence | Status |
| --- | --- | --- | --- | --- |
| G01 | `checkpoint_and_dirty_state_verified` | Verify actual Round 15 HEAD prefix 1d8f66c, branch, both worktrees and no unseen drift | GIT/REPORT | PASS |
| G02 | `probe_default_off_legacy_unchanged` | Without new gate, ordinary Debug and Release use original player/navigation/DataStore, no Room dual write | BUILD/NATIVE | PASS |
| G03 | `guard_requires_all_upstream_authorizations` | No activation without full R06–R15 chain, disposable private consent/profile and exact task pair | BUILD/NATIVE | PASS |
| G04 | `guard_rejects_forbidden_tasks` | Release, bundle, publish, signing, ordinary install and prohibited task permutations rejected before side effects | BUILD/SEC | PASS |
| G05 | `empty_source_catalog_and_legacy_data_safe` | No real CMS scan, default sources stay empty and legacy records never migrated by merely opening player | SEC/NATIVE | PASS |
| G06 | `single_player_single_checkpoint_authority` | Guarded path has one Media3 player, one R05–R07 checkpoint authority, one Room writer; no parallel repo/timer | REVIEW/NATIVE | PASS |
| G07 | `no_schema_or_release_configuration_drift` | Room v1–v6, package/version/minSDK/targetSDK/normal ARMv7 and frozen signing preserved | BUILD/SEC | PASS |

## U — Player projection and overlay (8)

| ID | Test or proof key | Required assertion | Minimum evidence | Status |
| --- | --- | --- | --- | --- |
| U01 | `real_play_pause_state_updates_controls` | Media3 playWhenReady/isPlaying transition updates Chinese play/pause label without optimistic assumption | NATIVE/MEDIA3 | PASS |
| U02 | `buffering_is_not_user_pause_or_completion` | BUFFERING/READY/ENDED/error meanings remain distinct; no false completed or watched event | JVM/NATIVE | PASS |
| U03 | `overlay_can_reveal_and_hide_when_playing` | Short user activity reveals control surface; inactive playback may hide only when eligible | NATIVE | PASS |
| U04 | `overlay_must_remain_during_interaction` | Paused, loading, error, focused control, picker, accessibility interaction never hidden by timer | JVM/NATIVE | PASS |
| U05 | `unknown_duration_displays_truthfully` | Unknown duration has Chinese unknown state, not 0:00 total, fake percent or enabled absolute scrub | NATIVE/SHOT | BLOCKED |
| U06 | `unsupported_commands_are_disabled` | Actual Player.availableCommands gates play, seek, next, track controls; disabled actions never invoke Player | JVM/NATIVE | PASS |
| U07 | `real_error_and_retry_preserve_route` | Media3 error shows actionable Chinese retry; no silent route change or checkpoint reset | NATIVE/MEDIA3/ROOM | PASS |
| U08 | `dispose_cancels_ticker_and_listener` | Leaving player stops its UI timer/event observer without leaking or modifying persisted progress | JVM/NATIVE | PASS |

## K — D-pad key cycles, focus and nested Back (10)

| ID | Test or proof key | Required assertion | Minimum evidence | Status |
| --- | --- | --- | --- | --- |
| K01 | `hidden_dpad_arrows_preserve_existing_shortcuts` | Without controls, supported L/R seek and U/D episode gestures keep established bare-player semantics | NATIVE/MEDIA3 | PASS |
| K02 | `visible_dpad_arrows_navigate_controls` | With controls, arrows move exactly one valid focus/preview target rather than also seeking/switching | NATIVE | PASS |
| K03 | `short_ok_fires_on_up_exactly_once` | One DOWN+UP physical OK/ENTER cycle triggers one short action and nothing on initial DOWN | JVM/NATIVE | PASS |
| K04 | `long_ok_does_not_fire_short_first` | Long OK emits one long action and zero short action even when repeatCount was initially zero | JVM/NATIVE | PASS |
| K05 | `held_repeat_orphan_up_cancel_safe` | Repeated DOWN, lost UP, orphan UP, focus loss and ACTION_CANCEL cannot cause ghost click or stuck held state | JVM/NATIVE | PASS |
| K06 | `rapid_ok_and_back_deduplicated` | Rapid presses yield one dispatch per genuine key cycle; no double navigate from clickable + parent handler | NATIVE | PASS |
| K07 | `back_unwinds_exactly_one_layer` | Picker to controls to player to Detail to origin unwinds one visible layer per Back cycle | NATIVE/XML | PASS |
| K08 | `top_overlay_traps_focus_and_restores_opener` | Top modal prevents background events; dismissal returns focus to actual opening control | NATIVE/XML | PASS |
| K09 | `user_navigation_cancels_delayed_focus_restore` | Late layout/player callbacks do not steal focus after actual user D-pad action | NATIVE | PASS |
| K10 | `all_required_controls_without_long_or_menu` | Every important player action is reachable by D-pad/OK/Back only; no hidden-only long-press feature | NATIVE/SHOT | PASS |

## S — Seek preview, time and correctness (8)

| ID | Test or proof key | Required assertion | Minimum evidence | Status |
| --- | --- | --- | --- | --- |
| S01 | `confirmed_scrub_seeks_once` | Focused timeline L/R previews, OK applies exactly one valid Media3 seekTo and exits preview | NATIVE/MEDIA3 | PASS |
| S02 | `scrub_back_cancels_without_persisting` | Back cancels preview, keeps real playing position and last Room checkpoint unchanged | NATIVE/ROOM | PASS |
| S03 | `backward_seek_and_intentional_zero_commit` | Real backwards and explicit zero seek persist the new value without maximum-position merging | NATIVE/MEDIA3/ROOM | PASS |
| S04 | `repeated_seek_is_bounded_and_interruptible` | Long/rapid arrows cannot queue unlimited seeks, create negative time or cross known duration bound | JVM/NATIVE | PASS |
| S05 | `nonseekable_media_no_unsupported_calls` | Absent seek command renders disabled seeking and makes zero forbidden Player calls | JVM/NATIVE | PASS |
| S06 | `unknown_length_relative_seek_is_honest` | No absolute scrub with unknown duration; supported relative seek only when actual command permits | NATIVE | PASS |
| S07 | `preview_and_preload_not_counted_watched` | Preview, media prepare and UI focus do not create watch/history or change resume pointer | NATIVE/ROOM | PASS |
| S08 | `seek_failure_preserves_last_committed_position` | Error/cancel/late position callback never fabricates successful durable seek, old checkpoint remains valid | NATIVE/ROOM | PASS |

## E — Episode/line actions from player (7)

| ID | Test or proof key | Required assertion | Minimum evidence | Status |
| --- | --- | --- | --- | --- |
| E01 | `picker_shows_stable_source_episode_identity` | Only evidence-backed routes, episode IDs and labels appear; no fake count or first episode | NATIVE | PASS |
| E02 | `unavailable_line_has_disabled_action` | Disabled/missing source cannot be played; no default silent fallback or fabricated URL | NATIVE/MEDIA3 | PASS |
| E03 | `exact_variant_resume_never_cross_applies` | Choosing another source variant does not import previous variant 8000ms checkpoint | NATIVE/ROOM | PASS |
| E04 | `picker_dpad_and_back_focus_restore` | Picker is navigable with five keys, traps focus, Back restores originating player control | NATIVE/XML | PASS |
| E05 | `switch_barrier_saves_old_before_new` | Actual switch awaits old episode/variant checkpoint result before binding new session | NATIVE/MEDIA3/ROOM | PASS |
| E06 | `late_switch_and_retry_callbacks_rejected` | Fast repeated route choices, cancellation and stale callbacks cannot change chosen identity or focus | JVM/NATIVE | PASS |
| E07 | `media_failure_preserves_user_library` | Media failure/line removal/retry preserves favorites, other-episode history and last committed resume | NATIVE/ROOM | PASS |

## P — Progress lifecycle and process recovery (8)

| ID | Test or proof key | Required assertion | Minimum evidence | Status |
| --- | --- | --- | --- | --- |
| P01 | `pause_commits_actual_position` | Real pause from playing saves actual position through existing checkpoint coordinator | NATIVE/MEDIA3/ROOM | PASS |
| P02 | `buffering_does_not_commit_fake_zero` | Buffering/loading transient zero does not destroy previously committed resume point | NATIVE/ROOM | PASS |
| P03 | `background_commit_failure_exit_not_trapped` | Activity background/save failure surfaces true outcome and does not trap Back indefinitely | NATIVE/ROOM | PASS |
| P04 | `old_session_operation_rejected_after_switch` | Late pre-switch callback and equal-sequence conflicting write rejected by real Room arbitration | NATIVE/ROOM | PASS |
| P05 | `no_automatic_audio_on_return` | Navigate away/back or recreate player keeps restored state paused until explicit Play | NATIVE/MEDIA3 | PASS |
| P06 | `activity_recreation_uses_last_committed` | Actual Activity recreation restores correct identity/position and safe focus with no speculative seek | NATIVE/ROOM | PASS |
| P07 | `external_process_restart_pid_diff_and_paused` | Two-phase external force-stop or am kill uses new PID and reads exact Room work/episode/variant/position paused | PROC/NATIVE/ROOM | BLOCKED |
| P08 | `uncommitted_progress_not_claimed_durable` | Interrupted write restores last committed value; report does not promise persistence of uncommitted time | PROC/NATIVE/ROOM | PASS |

## V — Visuals and accessibility (7)

| ID | Test or proof key | Required assertion | Minimum evidence | Status |
| --- | --- | --- | --- | --- |
| V01 | `controls_1280x720_fit_real_pixels` | Real 1280×720 AVD capture + pixel check shows all essential controls in bounds and reachable | SHOT/NATIVE | PASS |
| V02 | `controls_1920x1080_focus_visible` | Actual 1920×1080 screenshot + semantics shows proper control focus and no clipped timeline | SHOT/NATIVE | PASS |
| V03 | `large_font_long_title_and_line_fit` | Large font and long Chinese episode/line labels retain actionable controls/Back with no hidden overflow | SHOT/NATIVE | PASS |
| V04 | `unknown_duration_loading_and_error_visuals` | Actual AVD screenshots show truthful unknown/loading/error labels and usable retry without raw codes | SHOT/NATIVE | PASS |
| V05 | `reduced_motion_is_interruptible` | Reduced-motion controls and panel transitions preserve feedback; rapid D-pad/Back not queued by animation | SHOT/NATIVE | PASS |
| V06 | `focus_distinct_from_selected_and_disabled` | Focus ring, selected episode/line and disabled states are visually distinct under navigation | SHOT/NATIVE | PASS |
| V07 | `chinese_no_brand_original_icons_only` | UI strings/a11y Chinese, no app title/debug IDs; new icons have original editable SVG/native parity | SEC/SHOT/NATIVE | PASS |

## R — Regressions, safety and review (5)

| ID | Test or proof key | Required assertion | Minimum evidence | Status |
| --- | --- | --- | --- | --- |
| R01 | `all_suites_actual_with_skip_accounting` | JVM/Python/native full runs and genuine start/pass counts; five earlier assumption skips separately disclosed | BUILD/NATIVE/REPORT | PASS |
| R02 | `normal_off_artifacts_and_lint_unchanged` | Ordinary Debug/unsigned Release/androidTest builds, lint message multiset, ABI/ID/manifest compared to baseline | BUILD/SEC | PASS |
| R03 | `native_video_claim_requires_frame_evidence` | Actual video frame/rendered-first-frame proves surface if claimed; WAV-only leaves video assertion BLOCKED | NATIVE/SHOT/REPORT | PASS |
| R04 | `independent_readonly_review_and_security` | Independent read-only source/visual/remote review; staged index security zero violations and actual fixes rechecked | REVIEW/SEC | PASS |
| R05 | `tested_staged_committed_blob_consistency` | Final tested candidate equals staged index equals local commits; both worktrees clean; no push/merge/release | GIT/SEC | PASS |

## Final accounting and stop

Fill this table with actual values only after execution:

| Result | Count |
| --- | --- |
| PASS | 58 |
| FAIL | 0 |
| BLOCKED | 2 |
| N/A (with justification) | 0 |
| Total | 60 |

Additionally report discovered/started/passed/failed/ignored/assumption-skipped/not-run unique native method counts; commands for the paired probe and actual AVD serial; separate two-phase PIDs; screenshot paths; baseline/final full HEAD; ordinary OFF artifact hashes; schema v1–v6 hashes; security-scan index scope; and review items with precise fixes or deferrals.

**Boundary:** Successful rows prove only the player-controls slice behind private Debug authorization. The 60-row matrix does not itself close T10, T11, M04, legacy upgrade, physical ARMv7/API24 TV, true CMS or release/signing gates.
