# Security and release hold

## Active signing incident

The former release keystore and its passwords were tracked in a repository that was public. Treat the old signing identity as compromised. Returning the repository to private, deleting files from the current tree, or changing the keystore password does not revoke copies of the private key.

Release signing and publishing are frozen. This is containment, not incident closure. Do not make this repository public while the gates below remain unresolved. Do not rerun historical release workflows: old revisions still contain the old signing configuration.

## Current controls

- The current tree does not contain the release keystore or literal signing passwords.
- Release builds are unsigned by default. Signing cannot be enabled by a Gradle property while the source-controlled freeze is active.
- The external signing configuration accepts ignored local properties or environment variables only after the freeze is reviewed and lifted. It has no credential defaults and requires a keystore outside the checkout.
- Ordinary CI receives no release credentials, has read-only repository permissions, uses pinned actions, and does not upload APKs or publish releases.
- Debug builds use a separate application ID. The production ID, version code, data model, and installed application are not changed by this containment work.
- The updater rejects missing or mismatching complete current-signer sets; it does not treat an unverifiable signature as an acceptable update. This is not key rotation or compromised-key revocation.
- The repository guard checks indexed blobs and reports rule names, not matched secrets. It is a targeted regression guard, not an exhaustive secret scanner or a history scan.
- Legacy release workflow ID `387692952` was disabled in GitHub workflow management and read back as `disabled_manually`; ordinary verification remains active. Recheck that administrator state before changing release policy.
- An encrypted local recovery archive outside the repository was decrypted and compared against the historical keystore, signing configuration and verified Git bundle. Its recovery secret was retrieved from the owner's macOS Keychain; the historical keystore opened successfully. An offline copy is not established by this local check.
- Cached and newly downloaded APKs reject replay/downgrade and recheck size, optional SHA-256 integrity, package/version and all current signers. Interrupted downloads clean partial files. These tests do not establish independent publisher authentication or key migration.

## Required evidence before reopening or releasing

1. Verify an encrypted offline backup of the old keystore and recoverability of its credentials. Do not destroy private history before this is verified. Record the installed APK certificate independently; an earlier document is not a fresh device measurement.
2. Design and review a transition from the compromised key. Do not generate an unrelated key and pretend that existing installations can update. Android 9+ supports proof-of-rotation, but Android's documentation explicitly discourages rotation on Android 12 and earlier. Android 11 requires device-specific validation, not an assumption based on API availability.
3. Verify in-place upgrade from the actual installed APK on the Android 11 TV. Preserve package name, UID, settings, playback progress, and data. Check the app's own updater as well as the system installer, and reject an old-key-only candidate after migration. Do not uninstall, clear data, enable rollback to the compromised signer, or bypass package verification.
4. Review all branches, tags, historical blobs, pull-request references, Actions logs/artifacts/caches, release assets, and known forks. Inventory and coordinate a history cleanup or a clean-history publication. Removing only the current file is not sufficient; an old branch in the same public repository is not a safe archive. No force push is authorized by this document.
5. Rotate or revoke any other exposed credentials at their issuers. A keystore password change alone does not replace an APK signing key.
6. Audit original-code ownership and third-party code, artwork, fonts, dependencies, notices, and media-source configuration. Obtain an explicit license decision and add LICENSE plus required notices before claiming the project is open source.
7. Complete build tests, updater security tests, and TV focus/navigation regression checks. Record tested commits, tool versions, APK hashes, and actual device results without credentials or personal paths.

The owner approved Apache-2.0 and confirmed code/image redistribution rights on 2026-10-23; see [LICENSE](LICENSE) and [asset provenance](docs/ASSET_PROVENANCE.md). Fresh device verification, an independent offline key-backup copy, production signing migration, service-credential rotation and remote history sanitization are still unverified. See the [historical audit](docs/SECURITY_REMEDIATION_REPORT.md), [migration review](docs/SIGNING_MIGRATION.md) and [source-publication procedure](docs/OPEN_SOURCE_READINESS.md). A local clean snapshot does not erase remote historical objects or third-party copies.

## Handling reports

Do not put passwords, keystores, private subscription URLs, APKs containing private configuration, or personal filesystem paths in issues, pull requests, logs, or screenshots. Use the maintainer's established private channel. A dedicated public security reporting channel must be configured before public launch.

## References

- [Android app signing](https://developer.android.com/studio/publish/app-signing)
- [APK signature scheme v3 and its platform caveats](https://source.android.com/docs/security/features/apksigning/v3)
- [apksigner rotation options](https://developer.android.com/tools/apksigner)
- [GitHub sensitive-data removal](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)
