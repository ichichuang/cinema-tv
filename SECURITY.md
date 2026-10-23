# Security and release hold

## Active signing incident

The former release keystore and its passwords were tracked in a repository that was public. Treat the old signing identity as compromised. Returning the repository to private, deleting files from the current tree, or changing the keystore password does not revoke copies of the private key.

Production APK signing and publishing are frozen. This is containment, not incident closure. A source-only public launch may proceed after the source-publication gates below and separate owner authorization; it does not require activating production updates or testing a television. It must expose no downloadable production APK. Do not rerun historical release workflows: old revisions still contain the old signing configuration.

## Current controls

- The current tree does not contain the release keystore or literal signing passwords.
- Release builds are unsigned by default. Signing cannot be enabled by a Gradle property while the source-controlled freeze is active.
- The external signing configuration accepts ignored local properties or environment variables only after the freeze is reviewed and lifted. It has no credential defaults and requires a keystore outside the checkout.
- Ordinary CI receives no release credentials, has read-only repository permissions, uses pinned actions, and does not upload APKs or publish releases.
- Debug builds use a separate application ID. The production ID, version code, data model, and installed application are not changed by this containment work.
- The updater rejects missing or mismatching complete current-signer sets; it does not treat an unverifiable signature as an acceptable update. This is not key rotation or compromised-key revocation.
- The repository guard checks indexed blobs and reports rule names, not matched secrets. It is a targeted regression guard, not an exhaustive secret scanner or a history scan.
- The manual workflow at `.github/workflows/build.yml` is disabled in GitHub management and has a fixed source gate that refuses publication; ordinary verification remains active. After repository recreation, locate and disable this workflow by path, then read back its new ID/state. Old repository workflow IDs must not be reused.
- An encrypted local recovery archive outside the repository was decrypted and compared against the historical keystore, signing configuration and verified Git bundle. Its recovery secret was retrieved from the owner's macOS Keychain; the historical keystore opened successfully. An offline copy is not established by this local check.
- Cached and newly downloaded APKs reject replay/downgrade and recheck size, optional SHA-256 integrity, package/version and all current signers. Interrupted downloads clean partial files. These tests do not establish independent publisher authentication or key migration.

## Required evidence for source-only publication

1. Copy the verified encrypted recovery material into an explicitly authorized, owner-controlled offline failure domain, with recovery material independently held. Recover from that copy and verify hashes, permissions, Git refs, source mapping/content, incident evidence and historical signing-material recoverability. A same-machine archive or another directory on the same disk does not pass this gate.
2. Validate the clean-root source against the latest approved revision, retaining Apache-2.0, copyright notices, asset provenance and third-party materials. Exclude signing secrets, private configuration, personal paths, subscriptions and private incident records. Reuse unchanged reviewed evidence, then run the relevant candidate checks without suppressing findings.
3. Obtain explicit confirmation immediately before deleting the contaminated repository and recreating the same name as Private. Publish only the clean root. Verify the new repository ID, sole main branch, absence of old PR refs/tags/compromised commits and historical Actions artifacts/caches. A force push of clean main is not a substitute. No permanent second repository is authorized.
4. Verify credential-free unsigned Android builds, tests, Lint and successful CI for the exact new commit. Keep production signing/authentication disabled, release workflow disabled by path, repository/environment production secrets absent, update metadata frozen and the media catalog empty. Do not upload validation APKs.
5. Obtain separate explicit authorization for Public visibility. If the plan prevents private branch protection, use the controlled sequence in [OPEN_SOURCE_READINESS.md](docs/OPEN_SOURCE_READINESS.md): immediately install and verify public main rules, non-main creation restrictions and supported security settings after the authorized transition. No production release is part of that transition.

The owner approved Apache-2.0 and confirmed code/image redistribution rights on 2026-10-23; see [LICENSE](LICENSE) and [asset provenance](docs/ASSET_PROVENANCE.md). An independent offline copy and remote history replacement remain unverified. The signing incident stays open even after a safe source-only launch; deleting GitHub history does not revoke leaked keys or external copies.

## Separate production APK migration and release gates

Production key custody, independent publisher trust, authenticated transport/DI/installation-state recovery, API 24–27 policy and real Android 11 migration remain separate, unpassed gates. They block production APK signing, download publication and removal of the old-client bridge; they do not block a source-only launch once the preceding source gates pass.

Before any separately authorized production migration, review the transition from the compromised key and independently establish the replacement identity. Android 11 compatibility cannot be inferred from lineage or emulator evidence. On an explicitly authorized television, verify normal in-place and subsequent updates, old-key rejection, package/UID/settings/sources/progress preservation, startup and playback. Do not uninstall, clear data, enable rollback to the compromised signer or bypass package verification. Do not generate production keys, activate authenticated production updates, sign releases or connect to the television as part of this source-publication operation.

Keep `ichichuang/cinema-tv-releases` unchanged as the temporary bridge for installed 0.9.8 clients. Its removal requires independently trusted migration, verified remaining-client impact and separate deletion confirmation. This temporary compatibility exception does not change the approved final single-repository architecture.

## Handling reports

Do not put passwords, keystores, private subscription URLs, APKs containing private configuration, or personal filesystem paths in issues, pull requests, logs, or screenshots. Enable and read back GitHub private vulnerability reporting in the controlled Public transition, then use Security / Advisories / Report a vulnerability. Until that channel exists, use the maintainer's established private channel rather than a public issue.

## References

- [Android app signing](https://developer.android.com/studio/publish/app-signing)
- [APK signature scheme v3 and its platform caveats](https://source.android.com/docs/security/features/apksigning/v3)
- [apksigner rotation options](https://developer.android.com/tools/apksigner)
- [GitHub sensitive-data removal](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)
