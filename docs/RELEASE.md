# Release signing and distribution

## Release hold

Release signing and publishing are frozen because the former release keystore and its credentials were publicly exposed. The current tree is being hardened for a future open-source launch. Removing a tracked key is not revocation, migration, or history sanitization. See [SECURITY.md](../SECURITY.md).

The production package remains com.ichichuang.cinema, versionCode 18, versionName 0.9.8. No new version is declared by this remediation. The existing distribution repository, APKs, tags, and update manifests are unchanged.

## Existing identity and backup

Earlier release evidence recorded this certificate SHA-256 for versions 0.9.6 and 0.9.8:

`5c3ae14663ca57023e33e9775255aa2906c712e849fa0c542f799bd4edfa6abd`

This is a public certificate fingerprint, not a private key or a fresh measurement of the television. Verify it independently against the installed APK and archived release APK before designing a transition. Verify an encrypted offline backup of the old keystore and recoverable credentials before any history rewrite. The original private history has deliberately not been destroyed.

## Upgrade-compatible migration gate

Android supports proof-of-rotation starting with API 28, but its official documentation discourages rotation on Android 12 and earlier. The target Android 11 device therefore requires a real compatibility experiment. Do not assume that generating a new key, keeping the same alias, changing a keystore password, or using default apksigner flags preserves upgrades.

Review the signing lineage, capabilities granted to old certificates, supported platform ranges, and the app's own signature checks together. Current app-level equality checks do not establish rotated-key support. Modern apksigner defaults use the rotated key for API 33+; an API 30 migration needs explicit, reviewed platform handling. Do not enable rollback trust for a compromised signer as a shortcut.

Acceptance requires an in-place upgrade from the actually installed build with settings and playback progress retained, successful installation through the intended updater route, correct signer verification, and rejection of an old-key-only candidate after migration. No uninstall or data clearing is an acceptable substitute. Devices on API 24-27 need a separately documented support decision because they do not support v3 rotation.

## Build and secret handling

assembleRelease currently produces an unsigned validation artifact. The source-controlled freeze rejects signing requests even when external credentials are supplied. No implicit debug-key fallback is permitted.

When the migration is approved, supply the reviewed signer through ignored local properties or a protected CI environment. Keep the keystore outside the checkout, inject CI files into a restricted temporary directory, disable secret-bearing caches/logs, and remove temporary files even when the build fails. No signing secrets have been provisioned by this source-code change.

## Update protocol and outstanding work

The legacy updater reads the first parseable update manifest, compares versionCode, downloads APK bytes, checks package/version/signing information, then asks the system installer for confirmation. Existing manifests use versionCode, versionName, apkUrl, sizeBytes, notes, and optional mirrorUrl. The old implementation allowed missing signing fingerprints and did not authenticate a manifest or enforce an APK SHA-256 field. These are separate security work items, not solved by removing the Gradle password.

A digest received from the same untrusted manifest is not independent authenticity. A successful match against the compromised old certificate also does not prove that the publisher is legitimate. Do not describe transport hardening or certificate comparison alone as remediation of the compromised key.

## Publishing controls

publish-to-repo.ps1 and publish-update.ps1 now fail before modifying files, reading credentials, pushing Git refs, or contacting a release API. Their old force-tagging, force-pushing, and attachment-replacement behavior is no longer available in the current scripts. Do not run historical copies. The separate source-catalog publisher is not a release-signing workflow and still requires explicit review before use.

When publication is eventually approved, use a new monotonically increasing versionCode, immutable new tags, a reviewed manifest, signature verification, APK SHA-256 and size checks, and read-back verification of every distributed artifact. Never move old release tags or overwrite an existing release asset.

## References

- [APK signature scheme v3](https://source.android.com/docs/security/features/apksigning/v3)
- [apksigner](https://developer.android.com/tools/apksigner)
- [Removing sensitive data from GitHub](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)
