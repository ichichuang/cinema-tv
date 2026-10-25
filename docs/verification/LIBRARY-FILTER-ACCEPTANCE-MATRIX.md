# Round 14 — Native Library + Strict Filter Acceptance Matrix

**Scope:** 52 concrete proposed assertions for the **guarded native Library/filter UI**. These are *not* executed tests or prefilled passes. One test may support multiple assertions only if it actually asserts all of them; 52 rows do **not** imply 52 unique JVM or Android test methods. Use real candidate/catalog/query types. Record `PASS`, `FAIL`, `BLOCKED` or reasoned `N/A` for each row, with method, command, screen/ADB target, actual observed evidence path, review status and applicable source hash.

**Starting checkpoint reported by Codex:** local `codex/contract-foundation`, prefix `865401b` (record full SHA after local verification); parent `main` at `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`.

**Evidence labels:** `JVM` = actual Kotlin unit assertion; `NATIVE` = actual instrumented Compose/Room query behavior on owned disposable AVD; `SHOT` = actual native screenshot + screen layout/semantics audit; `PROC` = separately executed system lifecycle/ADB experiment, not automatically counted as an instrumentation method; `BUILD` = actual build/manifest/schema/ABI inspection; `SEC` = indexed security check + manual product/asset boundary review. A missing emulator/screenshot is `BLOCKED`, not `N/A`.

## G — Activation / authority (6)

| ID | Required test or inspection | Pass condition | Evidence |
| --- | --- | --- | --- |
| G01 | `ordinary_debug_keeps_legacy_library` | Probe OFF shows unchanged ordinary 片库 and DataStore writer; no Room candidate activation | NATIVE/BUILD |
| G02 | `ordinary_release_never_exposes_guarded_library` | Release cannot enable R14 guarded UI, even with fabricated property/flag | BUILD/SEC |
| G03 | `authorized_owned_profile_enters_library` | R13 verified consent/profile/gate required, actual Compose 片库 reachable via nav | NATIVE |
| G04 | `unauthorized_profile_cannot_seed_library` | No fixture injection, query side effect or saved user record with invalid authorization | NATIVE |
| G05 | `single_writer_and_user_data_preserved` | Guarded Library read path introduces no second source/user writer or silent Legacy migration | NATIVE/SEC |
| G06 | `default_source_catalog_remains_empty` | Normal fresh profile no fake films/posters/trusted providers and truthful Chinese unconfigured state | NATIVE/SHOT/SEC |

## F — Strict filter controls and full query (14)

| ID | Required test or inspection | Pass condition | Evidence |
| --- | --- | --- | --- |
| F01 | `exact_year_1994_no_false_match` | Known 1995, unknown or conflicting-year work never shown as verified 1994 match | JVM/NATIVE |
| F02 | `decade_1980_1989_inclusive` | 1980/1989 match, 1979/1990 do not; no arbitrary 2000 floor | JVM/NATIVE |
| F03 | `custom_range_1977_to_2006_inclusive` | Both endpoints match; earlier/later years rejected | JVM/NATIVE |
| F04 | `inverted_year_range_rejected_without_relaxation` | Invalid draft visibly rejected in Chinese, applied query/items untouched | JVM/NATIVE |
| F05 | `or_within_country_and_genre` | Country JP or TH, genre horror or thriller, but dimension-to-dimension AND | JVM/NATIVE |
| F06 | `all_dimensions_survive_edit_apply_reopen` | Content kind, work language, countries, genres, year span, sort, enabled sources and query scope all round-trip through Room v6 | JVM/NATIVE |
| F07 | `work_language_not_inferred_from_dub_or_subtitles` | Chinese audio or subtitles never converts an evidenced non-Chinese-language work to 华语 | JVM/NATIVE |
| F08 | `work_language_incomplete_not_non_chinese` | Unknown/incomplete/conflict language cannot satisfy strict 华语/非华语 assertion | JVM/NATIVE |
| F09 | `unknown_country_genre_not_match` | Unknown and conflicting facts do not join matching strict results or become fabricated chips | JVM/NATIVE |
| F10 | `focus_selected_disabled_states_independent` | Focus halo separate from active filter chip state; disabled controls semantically unavailable | NATIVE/SHOT |
| F11 | `cancel_back_discard_unapplied_draft` | Opening filter on deep list, changing values then Back preserves original full query, cursor and anchor | NATIVE |
| F12 | `same_filter_apply_is_no_op` | No-change Apply does not create new query ID, restart pagination or discard anchor | JVM/NATIVE |
| F13 | `explicit_remove_or_clear_issues_new_query` | Remove one chip / clear all only via user action; new query identity and Chinese audit reason; other untouched fields preserved | JVM/NATIVE |
| F14 | `dpad_year_range_entry_and_full_confirm` | Before-2000 year input, scope, country, genre, sort, source choices, Apply/Cancel reachable by D-pad/OK/Back only | NATIVE |

## Q — Query bridge and honest coverage (10)

| ID | Required test or inspection | Pass condition | Evidence |
| --- | --- | --- | --- |
| Q01 | `server_ignored_year_still_verified_locally` | Upstream advertises success but ignores year; screen lists R10 locally proven strict matches only | JVM/NATIVE |
| Q02 | `zero_match_first_page_not_complete` | HasMore true keeps pending/continue affordance, not '没有符合条件的影片' final | JVM/NATIVE |
| Q03 | `later_page_matches_keep_current_context` | Independent source page cursors find later real matches without resetting query/focus unexpectedly | NATIVE |
| Q04 | `one_source_failure_disclosed_with_results` | Show surviving verified items and partial coverage + failed source count, not complete/global | JVM/NATIVE/SHOT |
| Q05 | `unknown_global_total_not_fabricated` | Null/partial totals never rendered as global '共 N 部'; local displayed count clearly scoped if any | JVM/NATIVE |
| Q06 | `query_budget_exhausted_stays_incomplete` | No unlimited load loop; budget stop shown as incomplete with valid next action or accurate limitation | JVM/NATIVE |
| Q07 | `offline_saved_rows_not_playable_claim` | Offline cached records remain browseable, no unsupported assertion that media will play offline | NATIVE/SHOT |
| Q08 | `no_source_loading_complete_empty_failed_distinct` | Six truthful Chinese states and affordances are visually/semantically distinguishable; no dead focus | NATIVE/SHOT |
| Q09 | `late_prior_query_page_cannot_append` | Filter change or cancellation ignores older source callback, stale count/coverage/status does not override new query | JVM/NATIVE |
| Q10 | `identity_dedup_retains_playback_entries` | Only proven same work combines; each evidenced playback variant remains addressable, no title/year dedup | JVM/NATIVE |

## N — Navigation, focus, lifecycle (11)

| ID | Required test or inspection | Pass condition | Evidence |
| --- | --- | --- | --- |
| N01 | `home_to_library_to_home_real_origin` | Existing five Chinese nav destinations work and 片库 Back returns correct source | NATIVE |
| N02 | `deep_list_detail_back_recovers_query_anchor` | 100+ synthetic works, focus work near item 87, detail→Back restores stable ID, full query and real visible anchor | NATIVE |
| N03 | `filter_panel_traps_background_focus` | Only top filter panel receives D-pad until dismissed | NATIVE |
| N04 | `closing_panel_restores_filter_opener` | On Cancel/Back, return to actual filter control, not random poster or Home | NATIVE |
| N05 | `apply_new_query_clears_only_invalid_anchor` | Intentional new query starts proper new result context; favorites/history/home snapshots unmodified | NATIVE |
| N06 | `tab_contexts_remain_independent` | Library, favorites, history each retain their own scroll/filter/selected destination and focus | NATIVE |
| N07 | `dpad_input_revokes_late_focus_restore` | User D-pad wins over pending async layout/query/image restore after navigation | NATIVE |
| N08 | `one_ok_cycle_one_navigation_or_apply` | Repeated/held OK does not double-apply or navigate twice; long OK cannot execute short OK first | NATIVE |
| N09 | `missing_deep_anchor_valid_fallback` | Missing work recovers adjacent valid item → section control → safe page default, no irrelevant jump | NATIVE |
| N10 | `activity_recreation_reopens_committed_query` | Real Activity restart restores complete last committed query and stable available anchor; draft discarded | NATIVE |
| N11 | `process_restart_reopens_committed_query_no_history_mutation` | Distinct background kill and force-stop experiments reopen the last committed Room v6 query; any resumed playback remains paused, and mere browsing creates no watch history | PROC/NATIVE |

## V — Layout, original design, accessibility (7)

| ID | Required test or inspection | Pass condition | Evidence |
| --- | --- | --- | --- |
| V01 | `native_library_1280x720` | Actual AVD screenshot; year/filter, poster actions and source status not clipped or unreachable | SHOT/NATIVE |
| V02 | `native_library_1920x1080` | Actual configured AVD screenshot and focus/accessibility inspect; if impossible record `BLOCKED` | SHOT/NATIVE |
| V03 | `large_text_long_chinese_chips` | Huge title/chips/year range and long source names do not obscure Apply/Cancel or focus outline | SHOT/NATIVE |
| V04 | `portrait_and_missing_image` | Portrait aspect preserved, placeholder shows proven title; loading/failure does not steal focus | SHOT/NATIVE |
| V05 | `reduced_motion_nonblocking_navigation` | No queued motion or indefinite wait; keyboard focus feedback still visible | NATIVE/SHOT |
| V06 | `chinese_labels_no_in_app_brand` | UI-authored text/semantics Chinese, no app-brand/watermark/debug, no fake counts/ratings | NATIVE/SHOT/SEC |
| V07 | `original_token_and_icon_provenance` | Reuse Round13 vector/token authority; if added, new editable SVG matches native vector and is independently original | SEC/SHOT/BUILD |

## R — Regression, safety, evidence (4)

| ID | Required test or inspection | Pass condition | Evidence |
| --- | --- | --- | --- |
| R01 | `room_history_1_through_6_unchanged` | All 6 existing Room schema file bytes preserved; no untested v7, user data/route integrity preserved | BUILD/SEC |
| R02 | `ordinary_build_identity_signing_abi_unmodified` | Debug/unsigned Release/AndroidTest actual builds; version `0.9.8`/18, normal ARMv7 ABI, legacy writer, frozen signing and source JSON unchanged | BUILD/SEC |
| R03 | `jvm_native_python_and_lint_reexecuted` | Actual JVM, native unique methods, Python, two lint message sets and Gradle results recorded; off-path regression run | JVM/NATIVE/BUILD |
| R04 | `review_tested_to_committed_integrity` | Independent source/visual review, important fixes TDD and review disposition; staged-index scan accurately scoped; tested hashes match final local commits, no push | SEC/BUILD |

## Execution ledger rules

For every one of the **52 rows**, record: status `PASS/FAIL/BLOCKED/N/A`; test method or manual evidence; whether RED was observed before new behavior (if applicable); exact command, build variant, target serial, source fixture, expected and actual assertions; screenshot/ADB/Room/provenance and saved log path; reason for N/A (only if truly not applicable, not merely unimplemented). Reconcile native method names between discovered test source, start events and completion events and count independently from matrix rows and process experiments.

**Important open gates:** This round can only close its native Library/filter UI slice. Complete T02/T03–T11/M04, a verified real CMS, API24/ARMv7 physical TV, normal-app migration/activation, diagnostics deletion, video/player UI, performance budgets and release/signing must remain open until independently executed and accepted.
