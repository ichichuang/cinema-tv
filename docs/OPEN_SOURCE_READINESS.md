# Open-source readiness and UI refactoring gates

The intended outcome is a maintainable open-source Android TV application. Private visibility is temporary containment, not the final product policy.

## Security before publication

The signing incident must be closed with verified offline key custody, an upgrade-compatible migration decision, real Android 11 in-place upgrade evidence, and a coordinated history/reference cleanup. Read [SECURITY.md](../SECURITY.md). No force push, visibility change, signing-key rotation, or APK publication is performed implicitly by a source cleanup.

Audit the whole repository, including historical refs and generated release material, for credentials, subscriptions, personal paths, account tokens, and signing blobs. The included Python guard catches selected regressions in the index only; use a full secret scanner and a human review for the historical audit. Configure branch protection, required checks, private security reporting, and protected release environments before reopening. These administrator settings remain to be verified.

## License and ownership

The maintainer has stated an intention to open source the project but has not selected a license in this remediation task. Do not invent third-party ownership or silently relicense imported code. Record original-code ownership, contributor permissions, and every bundled third-party code/artwork/font/license obligation. Select and approve a license, add LICENSE, and preserve required attribution/notices before declaring the repository open source. Dependency metadata and removed META-INF notice files also require review. No license clearance is claimed by this change.

The concrete recommendation is Apache License 2.0 for owner-confirmed original material; the decision remains unapproved. [The materials audit](THIRD_PARTY_NOTICES.md) records source/resource provenance limits and checked dependency metadata. Shared license resources are now merged instead of stripped. Final transitive notices and artwork rights still require clearance.

## Clean-history publication preparation

`scripts/prepare_public_source.py --output "$PUBLIC_SOURCE_REVIEW_DIR"` exports a clean committed tree to a new owner-selected directory outside this checkout. It rejects dirty worktrees, signing/local material, detected credential content, personal paths, symlinks, gitlinks, unsafe paths and an existing destination. It preserves source bytes, executable modes and contributor names in `SOURCE_HISTORY.md`; it copies no `.git` objects and does not initialize remotes, push or change visibility. `PUBLIC_SOURCE_REVIEW_DIR` must identify a private local review location; do not put its personal absolute value in shared logs.

Before any real publication, separately verify the encrypted private recovery archive and offline copy, obtain the license and migration approvals, run both the targeted guard and an independent scanner on the candidate, and build it without inherited `.local/` or signing credentials. An isolated local root-commit experiment proves absence of old refs only in that candidate; it does not remove historical GitHub objects, old artifacts/caches or third-party copies. Replacing existing remote history, deleting references/evidence, contacting other owners, and changing visibility require separate approval. The existing source repository remains private.

## UI workstream after the security baseline

Keep UI refactoring in separate commits from signing and updater security changes. Preserve package identity, settings keys, stored sources, playback progress, and the existing supported device contract.

1. Capture the current home, details, player overlay, settings, update, loading, empty, and error screens on the target TV before restructuring. Record the source commit and viewport; do not use screenshots containing private source addresses.
2. Consolidate existing color, spacing, typography, motion, focus ring, and focused/pressed/disabled states. Reuse the existing Compose/TV Material stack and avoid a framework replacement without evidence.
3. Separate screen state and side effects from reusable visual components. Reduce oversized composables incrementally, with loading/empty/error/retry states and stable list keys.
4. Validate every action with D-pad only: initial focus, directional movement, tab switching, dialogs, scroll restoration, return from details, player controls, settings entry, and return navigation. Never require touch to recover from an error.
5. Check 720p/1080p/4K readability, overscan-safe margins, focus contrast, long Chinese titles, image failures, and reduced-motion behavior. Measure recomposition and scrolling on the existing 32-bit target before changing performance assumptions.
6. Record build/lint/unit/UI test results and actual TV navigation/playback evidence. Do not call an unrendered code refactor a completed visual redesign.

No redesigned screenshots or device-tested interface changes are delivered by the initial signing-containment commit. This file defines acceptance criteria, not completed work.
