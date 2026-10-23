# Round 02: File-Backed Storage and Isolated Legacy Import Implementation Plan

> For agentic workers: Please execute this scoped plan task by task using the available execution, test-driven development, debugging, and verification skills. Reuse the existing isolated worktree and stop at the stated Round 02 boundary.

**Goal:** Prove that the accepted domain values survive real database writes and reopening, and that legacy user records can be preserved atomically in an isolated candidate database without activating migration or changing the running application.

**Architecture:** Add a minimal Room schema, versioned storage encodings, candidate browse/favorite stores, and an explicitly invoked legacy snapshot importer alongside the existing application. Keep production readers and writers unchanged. Preserve unresolved legacy data in staging rather than inventing canonical identities; session-safe playback committing and the runtime cutover remain later work.

**Tech Stack:** Existing Kotlin, Android, coroutine Flow, DataStore, JSON support, and tests, plus the smallest compatible stable Room/compiler/schema-export and Android test additions. Verify actual AGP/Kotlin/KSP compatibility before choosing and pinning versions. Do not refactor to multiplatform merely to obtain host tests.

**Spec:** `docs/superpowers/plans/2026-10-23-tv-experience-rebuild.md`, chapters five, six, eleven, fourteen and fifteen; `docs/architecture/tv-data-contracts.md` and the actual domain sources at the verified Round 01 HEAD.

**Scope:** A bounded T03 storage-and-import slice, plus the persistence-encoding continuation of T02. Do not mark all T03, T02, M01, M02 or any A01-A24 acceptance scenario complete. T03 production activation, real legacy-data validation, and T04 session ordering are outside this round.

**Reported starting point:** Branch `codex/contract-foundation`, HEAD `4ddd1530bb06f4faa8ac2f05cec0f440d2a453ee`; original `main` at `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`. The user reports 100 app unit tests, 62 Python tests, successful debug/unsigned-release assembly, and clean worktrees. These are handoff observations, not fresh execution evidence. Verify locally before relying on them.

**Handoff status:** This package contains instructions, not application changes or evidence that database tests have already passed. The coordinator has not inspected the unpushed Round 01 code; its actual signatures and documented rulings take precedence over inferred names.

## Global Constraints

- Continue the existing isolated worktree; do not create another worktree, move the worktree, merge to main, cherry-pick from an assumed remote branch, or repeat the broad repository audit. Check local instructions and preserve unrelated changes. No fetch is needed just to start this round.
- Keep production identity `com.ichichuang.cinema`, `.debug` isolation, version 0.9.8 / versionCode 18, minSdk 24, targetSdk 28, ABI configuration, signing freeze, release assets, update metadata, and repository visibility unchanged.
- Allow only justified build edits for Room, its compatible processor/schema export, and focused test infrastructure. Keep Gradle, AGP, JDK, the existing Kotlin/Compose configuration and unrelated dependencies unchanged. No dynamic dependency versions or blanket lint suppressions.
- Do not modify existing production DataStore classes, navigation, ViewModels, screens, app initialization, manifests for runtime auto-start, or dependency-injection bindings to activate the candidate storage.
- Do not implement or bind `PlaybackProgressStore.commit` in this round. Database rows alone do not satisfy its session/sequence guarantees. Do not infer session order from timestamps, lexicographic UUIDs, or a larger position/sequence from a different session.
- Do not implement catalog identity matching, source normalization, a query coordinator, strict filtering, subscription refresh, UI, original icons, or player checkpoints. Candidate persistence is real code, not permission to implement later tasks.
- Keep existing legacy stores as the only active runtime writers. No dual writing, migration-on-startup, global completed flag, delete-after-import, or production source read from a test's default Context.
- Never delete or rebuild user tables to make schema or migration tests pass. No destructive migration fallback, `clearAllTables` recovery, database-file deletion on open failure, or repair-by-uninstall.
- Use only synthetic fixtures clearly named as test material. Never pull production records, secrets, or diagnostic reports from a television or search personal directories for a missing fixture. Synthetic migration success is not real-user upgrade acceptance.
- A verified, dedicated disposable Android emulator containing no user data is the only permissible installation target for this round's test APKs. No physical devices or existing personal-device profiles. Pin and verify the target; no unqualified adb or multi-target connected-test invocation. If no compatible isolated target is available, compile instrumented tests and report execution as blocked.
- Keep logs, local paths and detailed fixture payloads out of committed evidence. Treat raw legacy keys/values as potentially sensitive; use application-private candidate storage and never include their contents in error messages or backup/export defaults.
- App-owned user-facing text remains Simplified Chinese. This round adds no UI. New implementation comments and commit messages should be clear English; the delivery report should be Simplified Chinese.

## Review Focus

1. Storage encodings silently normalize legal edge values or lose missing/conflicting/known-empty states: owned by R02-B, with round-trip and rejected-future-format tests.
2. An import marker survives while its rows do not, a repeat duplicates rows, or a changed legacy snapshot is silently skipped: owned by R02-D, with real-transaction rollback and deterministic digest tests.
3. Cache eviction cascades into favorites/progress or malformed records cause global data reset: owned by R02-C/R02-E, with file-backed integrity and corruption tests.
4. A database-ready claim relies only on mocks, in-memory objects, or test compilation: owned by R02-E/R02-F, with explicitly classified actual Room execution and close/reopen evidence.
5. The candidate is accidentally wired into production, or low-level playback storage claims stale-session protection: owned by R02-A/R02-F, with source/diff call-site inspection and explicit unimplemented-contract status.

## Decisions for this round

**Legacy import is not a Room version upgrade.** The legacy format is DataStore. This round introduces the first exported Room schema and an explicit DataStore-snapshot import workflow. Do not invent a historical Room v0 or fabricate a 1-to-2 migration solely to produce a green migration test. Preserve the exported v1 schema for later real version transitions. Test that an unsupported schema/format fails without deleting original data.

**Preserve first; associate later.** T05 has not established canonical work and episode matching. Import old keys, typed values, decoded legacy coordinates and unresolved/malformed status into a dedicated staging area. Do not manufacture work IDs from titles, source URLs or integer indexes; do not claim source-qualified IDs identify a canonical work. Preserve URL-plus-ID keys exactly, including ambiguous delimiters, without exposing them in logs. Do not invent older episodes, favorites, watch timestamps or completed states that the legacy format never stored.

**Candidate stores do not activate migration.** Implement actual `BrowseStateStore` and `FavoritesStore` candidates against Room and exercise them directly in tests. Keep them absent from Koin and runtime entry points. A low-level playback row DAO is permitted for durability tests, but it does not implement or substitute for `PlaybackProgressStore` and must remain internal to the data module/package.

**No additional public contract inventions.** First read the actual Round 01 types and freeze a storage mapping table. Preserve all existing signatures and validation. Use narrow internal DTOs, codecs and DAO methods; never duplicate domain models with incompatible public shapes. If a genuine contract incompatibility is discovered, document the smallest ruling and its test before changing it; do not relax a domain invariant merely to fit SQL.

**Legal edge values stay legal.** Preserve nullable duration and total, pre-2000 year values, inclusive query ranges, known-empty fact sets distinct from missing facts, conflicts, negative wall-clock timestamps permitted by the domain, and nonnegative positions beyond a stale duration. Do not clamp or discard these values. A zero position remains representable; preventing a loading zero from becoming a checkpoint belongs to T04.

## File map and ownership

Use `app/src/main/kotlin/com/ichichuang/cinema/` as the production source root. The following are planned paths; confirm exact existing domain names in R02-A before coding. Tests mirror the respective package.

| Planned file or area | Responsibility |
| --- | --- |
| `data/local/CinemaDatabase.kt` | Candidate Room database v1 and DAO accessors; not a runtime singleton |
| `data/local/CinemaDatabaseFactory.kt` | Explicit factory taking a Context and database name; no default old-store import or app-start hook |
| `data/local/entity/WorkReferenceEntity.kt` | Supplied opaque work ID only, with no guessed metadata |
| `data/local/entity/WorkMetadataCacheEntity.kt` | Rebuildable metadata/evidence payload and work association, independent of user-owned references |
| `data/local/entity/FavoriteEntity.kt` | Favorite membership keyed by work ID |
| `data/local/entity/BrowseSnapshotEntity.kt` | Versioned full snapshot keyed by contextKey |
| `data/local/entity/PlaybackSnapshotEntity.kt` | Encoded snapshot keyed by work/episode/variant; no session arbitration algorithm |
| `data/local/entity/LegacyImportRecordEntity.kt` | Preserved legacy entry, its source-store identity, status and digest association |
| `data/local/entity/LegacyImportBatchEntity.kt` | Transactionally committed import identity and accounting |
| `data/local/dao/` | Focused DAOs for user rows, metadata-cache eviction and import batches |
| `data/local/codec/StorageCodec.kt` | Internal `StorageCodec<T>` with `encode(value: T): String` and `decode(encoded: String): T`; malformed/unsupported payloads throw typed sanitized exceptions, not fabricated defaults |
| `data/local/codec/` | Versioned storage DTOs and codecs for actual catalog query, browse snapshot, playback snapshot and fact-evidence types used by persisted records |
| `data/userdata/RoomBrowseStateStore.kt` | Implements the existing BrowseStateStore signature without runtime binding |
| `data/userdata/RoomFavoritesStore.kt` | Implements the existing FavoritesStore signature without runtime binding |
| `data/local/migration/LegacySnapshot.kt` | Immutable, canonically ordered collection of typed entries and a content digest; no original-machine path |
| `data/local/migration/LegacySnapshotReader.kt` | Read-only capture from explicitly supplied, exclusively owned fixture DataStore handles |
| `data/local/migration/LegacySnapshotImporter.kt` | Idempotent preservation into staging with atomic batch accounting |
| `app/schemas/` | Actual compiler-generated Room schema artifacts, tracked with reproducible relative paths |
| `app/src/test/kotlin/com/ichichuang/cinema/data/local/` | Pure codec/digest/parser tests; do not label them native database tests |
| `app/src/androidTest/kotlin/com/ichichuang/cinema/data/local/` | Room schema, file-backed reopen, DAO, candidate-store and isolated import tests |
| `docs/architecture/tv-storage-contracts.md` | Mapping, schema, formats, failure/cancellation semantics, migration/cutover boundary and verified dependency choices |
| `docs/verification/2026-10-23-round-02-storage.md` | Actual baseline, test execution, review and commit evidence |
| `docs/superpowers/progress/2026-10-23-tv-rebuild.md` | Update actual slices and blockers, retaining previous records |

Do not add an entire catalog database or unused future entities to satisfy this map. Store only fields required by the accepted values and this round's tests. A metadata-cache table must have an explicit versioned payload and a real DAO eviction operation, not a fake no-op added to make a test pass. Group very small related DAO/DTO files when this improves clarity; record exact final paths.

## R02-A: Verify the local handoff and lock the storage mapping

**Consumes:** Actual Round 01 HEAD, `tv-data-contracts.md`, the master plan, and the existing progress ledger.

**Produces:** A fresh baseline report and exact source-to-storage mapping shared by all subsequent tasks.

- [ ] Confirm current worktree, branch, HEAD, status and staged content. Verify the expected Round 01 commit exists locally. If the branch contains later commits, examine only the relevant diff and retain them; do not reset to the reported hash.
- [ ] Verify both previously imported plan files against the package reference hashes. Different content requires a recorded diff/ruling, not overwrite. Import this new scoped plan only.
- [ ] Read the final contract document and actual eleven model/five interface files. Record their real signatures, constructors, invariants and equality semantics. Reuse `FactIds` and actual typed IDs; do not assume guessed enum cases.
- [ ] Read only the legacy progress, browsing and filter implementations needed for the importer. Freeze a table of real DataStore file/key/value formats, legacy ambiguity, and fields that were never stored. Global settings, credential-bearing subscription configuration and diagnostic data are excluded from this import slice.
- [ ] Read the final Round 01 review rulings. Verify the known-total invariant and deep-list focus fallback are still present; do not redo all original implementation work or claim a second independent review merely from this check.
- [ ] Run the current Python suite and full app unit suite before build edits. Discover the available Gradle verification tasks from the checked-in guide. Record actual counts rather than assuming the reported 100 and 62.
- [ ] Verify stable Room/compiler/schema-export and KSP compatibility against official documentation and a real dependency-resolution/build result. Record exact versions and the resolved graph. Add only compatible dependencies; no toolchain upgrade or built-in-Kotlin opt-out as an unexplained workaround.
- [ ] Record the allowed instrumentation target and execution method. Prefer Android instrumented tests on a dedicated disposable emulator. Host database tests are additional evidence only if supported by the already appropriate toolchain; do not introduce a new KMP architecture or silently substitute Robolectric for native execution.

**Gate:** Source vocabulary and runtime boundary are explicit. A missing emulator does not block codec/code work, but it blocks claiming native storage acceptance. A missing or unresolvable Room toolchain blocks affected build tasks; do not invent versions or downgrade security requirements.

## R02-B: Add versioned, lossless storage encodings

**Files:** The codecs/DTOs and pure tests above; update `tv-storage-contracts.md`.

**Consumes:** Exact actual Round 01 types and the R02-A mapping.

**Produces:** Internal codecs, versioned serialized forms and round-trip tests. Existing public types remain the single domain vocabulary.

- [ ] First add tests named `query_with_old_years_round_trips`, `browse_snapshot_preserves_all_fields`, `playback_snapshot_preserves_all_fields`, `fact_evidence_states_round_trip`, and `future_payload_version_is_not_silently_decoded`.
- [ ] Assert preservation of context/route/parent/query/source set/result revision/focus/anchor/relative offset and saved time, including signed offsets where the actual domain permits them. Assert every playback identity, session/sequence, watch state, timestamp, position and null duration.
- [ ] Include known-empty `FactIds`, missing evidence, conflicting values and their references as different cases; preserve domain-valid negative wall-clock values and positions beyond the known duration unchanged. Assert unknown remains null and never becomes zero or an empty known value.
- [ ] Use a small explicit envelope containing payload kind and format version. Use stable string tags, not enum ordinals, `hashCode`, Java object serialization or a comma-delimited replacement format. Prefer the existing JSON library without adding a serializer plugin solely for convenience. Preserve semantics of every field that is stored.
- [ ] Canonicalize unordered sets only for deterministic encoding/digests; do not sort fields whose order is meaningful. Preserve raw legacy strings separately from normalized storage forms.
- [ ] Decode malformed values or a future format into a typed, sanitized failure without mutating original bytes. `BrowseStateStore.load` returns null only for a missing row; a corrupt existing row is an explicit documented storage-read failure, not an invented default or silent absence.
- [ ] Run the focused tests before and after implementation, then the whole app unit suite. A compilation scaffolding failure is not the only red evidence: add executable behavioral failures and demonstrate the intended assertions.

**Gate:** Reopening an encoded value reconstructs equivalent domain state. This proves encoding, not file persistence or user-experience recovery.

## R02-C: Add the minimal real Room database and candidate stores

**Files:** Build/plugin declarations as necessary, `data/local/`, `data/userdata/`, schema artifacts, native storage tests.

**Consumes:** R02-B codecs, opaque supplied domain IDs, and unchanged public store interfaces.

**Produces:** A compilable database with real SQL constraints, a documented factory, candidate browse/favorite stores and low-level snapshot persistence.

- [ ] Write native tests `file_backed_values_survive_close_and_reopen`, `browse_contexts_do_not_overwrite_each_other`, `favorite_setting_is_idempotent`, and `cache_eviction_preserves_user_records`.
- [ ] Use temporary application-private file-backed databases with unique test names for reopen tests. Write data, await actual transaction completion, close all Room instances, reopen the same file and read through fresh DAOs/stores. In-memory databases are allowed for auxiliary constraint tests but never for the durability claim.
- [ ] Implement schema v1 with explicit keys/indices. Reference work IDs supplied by callers; do not allocate canonical IDs from titles or raw source URLs. Favorites may ensure a reference-only work row for the supplied ID within the same transaction; unsetting an absent favorite must not create a fake work record. No fake metadata is needed.
- [ ] Keep rebuildable metadata separate from work references and personal records. Favor foreign keys with deletion restrictions or explicit retention, not cache-to-user cascades. Metadata eviction removes only cache payloads. Do not add a public reset-all operation or change the existing image-cache behavior.
- [ ] Store browse snapshots by contextKey with atomic replacement. Define direct candidate-store calls as serialized accepted writes; `savedAt` is data, not proof of cross-session order. Do not claim cancellation-safe UI recovery or stale-browse arbitration before the navigation coordinator exists.
- [ ] Implement `RoomBrowseStateStore.save/load` and `RoomFavoritesStore.setFavorite` using the existing exact signatures. Return the existing Committed case only after a real transaction commits; map expected storage errors to the existing failure representation without leaking payloads. Rethrow coroutine cancellation; do not swallow fatal exceptions.
- [ ] Low-level playback DAO upserts are internal storage mechanics only. They must not be registered, named, or documented as a complete PlaybackProgressStore implementation. Preserve session/sequence fields as data for T04 without inventing an order policy now.
- [ ] Export schema from a real build and check it into version control. Do not hand-author generated schema JSON. Rebuild debug and release and verify schemas are compatible/identical where expected. Do not hardcode a misleading path if the plugin uses variant directories.
- [ ] Test unsupported/corrupt data handling without deleting or recreating the file. For actual schema tests, validate fields and data, not just a matching schema hash. There is no legacy Room v0 and no genuine app schema transition yet.

**Gate:** Actual Room reads/writes and reopen tests pass on a recorded Android test target, or are explicitly pending. The running app still uses the old readers/writers and no new candidate factory is invoked during app startup.

## R02-D: Preserve legacy snapshots atomically in an isolated database

**Files:** `data/local/migration/`, isolated fixture builders, `LegacySnapshotCodecTest` and `LegacyUserDataImportTest`.

**Consumes:** Explicitly supplied fixture DataStore handles, a read-only captured LegacySnapshot, and an explicitly supplied candidate database.

**Produces:** A read-only reader, a deterministic snapshot identity and a transactional preservation importer. No global migration flag and no active-user upgrade occur.

- [ ] Generate synthetic fixture files with the actual checked-in legacy encodings and typed preference keys. Include source-qualified keys, bare numeric keys, colliding IDs from two sources, old filter selections, deep-list IDs/indexes, empty/unknown fields and malformed records. Clearly label them synthetic.
- [ ] Create DataStore handles with exclusive ownership and no registered migrations or corruption replacement handlers for the reader. Read only the supplied fixture stores. Never instantiate a second DataStore for the same file or resolve a production store through a default Context/extension property.
- [ ] Capture typed values without trimming or normalizing raw content. Define digest input as a versioned canonical sequence of store-kind, exact key, value type and exact value, sorted by stable store/key order. Exclude capture time, absolute path and file mtime. The exact bytes used for the digest must be the bytes logically represented by the snapshot passed to the importer.
- [ ] Import all entries into staging, with recognized/unresolved/malformed status and original raw typed values preserved. Well-formed legacy line and episode indexes remain legacy coordinates, not new episode or variant IDs. A malformed item is retained with a sanitized reason code, not dropped or used to abort unrelated valid entries.
- [ ] Keep the batch identity, per-entry uniqueness and accounting in one Room transaction. Before committing, assert that every captured entry is represented exactly once as preserved or quarantined. A source-read failure produces no completed batch. An import report says preserved/unresolved, never fully migrated/activated/playable.
- [ ] Use `(import format version, snapshot content digest)` as the idempotency identity. The same snapshot twice adds nothing; a changed snapshot is not skipped by an earlier marker. Preserve changed batches as staging evidence; do not overwrite newer canonical user records or resolve chronological conflicts now.
- [ ] Prove `import_is_idempotent`, `changed_snapshot_is_not_silently_skipped`, `same_content_different_iteration_order_has_same_digest`, `same_content_new_capture_time_is_not_new_data`, `unresolved_legacy_id_is_preserved`, `malformed_entry_is_quarantined`, and `legacy_files_are_unchanged`.
- [ ] Add `failure_before_marker_rolls_back_rows`, `failure_after_marker_before_commit_rolls_back_all`, `cancellation_before_commit_rolls_back_and_propagates`, and `retry_after_commit_acknowledgement_loss_is_idempotent`. Use deterministic barriers/fault injection inside real Room transaction paths. Check actual database rows after reopening, not only a returned enum or mock calls.
- [ ] Distinguish cancellation before commit from cancellation after a durable commit: before commit, rollback is expected; after commit, the caller can lose acknowledgement while rows remain. Rerunning with the same snapshot must reconcile idempotently. Do not promise that every cancelled coroutine implies nothing was written.
- [ ] Add an injected I/O/storage-full error test that yields no false success and no partial batch. Label it fault injection; do not fill the developer's disk or claim real power-loss/disk-full device validation.
- [ ] Compare fixture bytes and typed values before and after; never delete old files or change legacy markers. No imported record is linked into the current application's runtime path.

**Gate:** Synthetic-fixture preservation is atomic, repeatable and inspectable. Full migration activation, user-data association, later changes to legacy stores, and production upgrade acceptance remain open.

## R02-E: Execute the storage verification matrix and classify evidence

**Files:** Native tests, verification report and storage document.

- [ ] Run pure codec/digest tests under the normal unit-test task; verify actual JUnit counts and skipped tests.
- [ ] Compile instrumented tests using the task available in this project, normally `:app:assembleDebugAndroidTest`, in addition to normal debug and unsigned-release assembly.
- [ ] Execute real Room and DataStore fixture tests only on the named disposable emulator. Check the emulator identity with a pinned serial before each install/instrument command. Derive APK paths, test package and runner from actual build outputs/manifests; do not guess them.
- [ ] Prefer an explicitly named managed-device task or serial-pinned `adb -s <verified-test-serial>` install/instrument commands. Do not use an unqualified connected test task that could fan out to a connected television. Never clear a personal package's data. A test APK installation on the disposable emulator is a test action, not a product installation acceptance claim.
- [ ] If execution is unavailable, keep the test sources, record successful compilation separately, and mark native database/import execution blocked. Do not fall back to mocks and report the gate passed.
- [ ] Reopen-test acceptance proves closed-instance persistence, not process death or abrupt-power-loss survival. Report these separately from actual schema validation and from injected transaction failures.
- [ ] Repeat the existing Python tests, full app unit tests, debug/release assembly and both lint tasks. Compare warnings individually to the fresh baseline; do not hide new warnings or update unrelated dependencies to erase an old hint.
- [ ] Review all call sites for candidate stores/factory/importer. Production app startup and DI must have none. Verify no test fixture is packaged in the main assets/resources or release source sets.

Suggested baseline/final commands, without changing checked-in signing rules:

```sh
python3 -m unittest discover -s scripts/tests -v
bash ./gradlew :app:testDebugUnitTest --no-daemon --no-configuration-cache
bash ./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease --no-daemon --no-configuration-cache
bash ./gradlew :app:assembleDebugAndroidTest --no-daemon --no-configuration-cache
```

For focused tests, use the exact class names introduced above. Before instrumentation, record the selected runner and pinned emulator execution command in the report. `ANDROID_SERIAL` alone is not evidence that every Gradle task targets only one device; verify the actual task behavior or use serial-pinned instrumentation directly.

## R02-F: Review, commit evidence, and stop

- [ ] Self-review the source-to-storage mapping against every actual Round 01 field. Inspect generated schemas, FKs, codec version checks, raw-preservation behavior, transaction boundary and candidate call sites.
- [ ] Obtain a fresh read-only review when the execution environment provides a reviewer. Supply the Round 02 range, this scoped plan, tests and schemas. Otherwise label the review as self-review, not independent review.
- [ ] Resolve introduced findings with focused failing/passing tests and the relevant regression run. For important review corrections, have the reviewer recheck those corrections when available; otherwise explicitly report the missing recheck rather than saying independently cleared.
- [ ] Stage only intended changes, rerun `python3 scripts/check_repository_security.py` against that index, and inspect both staged and unstaged diffs. Raw fixtures containing real credentials or device exports must not appear in any staged file.
- [ ] Hash the verified candidate files and compare with the committed tree. Record application/test/schema hashes and actual tested commit ranges. Do not change code after tests without rerunning affected tests. Keep the master and Round 01 plan unchanged.
- [ ] Make coherent local commits only. No merge, push, publish, signing, tag changes, repository visibility changes or worktree deletion. Verify the original main checkout is untouched without resetting anything.
- [ ] Update `tv-storage-contracts.md`, the Round 02 evidence report, and the existing progress ledger with precise completed slices, unresolved prerequisites, schema/format versions and the next activation boundary. Keep paths repository-relative in tracked reports.
- [ ] Finish with a Simplified Chinese report: baseline/final HEAD, worktree/branch, local commits, changed files, dependency changes, real commands, actual unit/instrumented counts, schemas, reopen/import outcomes, faults injected, review/recheck status, blocked checks, and actions not performed.

**Stop condition:** End after R02-F. Do not start player checkpoint/session management, canonical work matching, runtime import activation, UI redesign or T04-T16 implementation.

## Acceptance and evidence classification

| Scope | Evidence required | Maximum claim in this round |
| --- | --- | --- |
| Domain encoding | Actual pure round-trip and invalid-format tests | Storage format behavior tested |
| Room build/schema | Real build and generated tracked schema | Candidate database compiles and schema is exported |
| File-backed persistence | Actual write/close/reopen/read on identified native test target | Candidate storage persistence verified for those tests |
| Legacy preservation | Real synthetic DataStore fixture capture and atomic Room import tests | Synthetic fixture import behavior verified |
| Transaction failures | Actual Room transaction with injected errors/cancellation | Injected rollback/acknowledgement behavior verified |
| Real old user data | Authorized sample and separate validation | Not verified unless genuinely supplied; do not access a TV to obtain it |
| T03 runtime cutover | Future single-writer activation design and implementation | Not performed |
| T04 stale-session progress | Future coordinator/store arbitration plus tests | Not performed; low-level DAO is not this evidence |
| A01-A24 native user journeys | Actual end-to-end application behavior | No scenario automatically accepted from storage-only tests |
| Power loss / production upgrade | Separate controlled device evidence and signing gate | Not performed |

## Official references for dependency and testing decisions

R01: Android Developers, Room release notes and build/schema configuration: https://developer.android.com/jetpack/androidx/releases/room
R02: Android Developers, database testing: https://developer.android.com/training/data-storage/room/testing-db
R03: Android Developers, migrations and schema export: https://developer.android.com/training/data-storage/room/migrating-db-versions
R04: Android Developers, built-in Kotlin and processor compatibility: https://developer.android.com/build/migrate-to-built-in-kotlin

These references guide verification; the current Room release line and the general guide can show different artifact namespaces. Do not mix `androidx.room` and `androidx.room3` samples or select a new major release solely because a guide displays it. Verify one coherent stable dependency set against the actual project, record the choice, and keep the existing toolchain. Documentation access is not evidence that the chosen versions resolve or build.
