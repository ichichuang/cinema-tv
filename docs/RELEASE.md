# Release signing and distribution

## Single-repository distribution target (not active)

The only intended long-term GitHub repository is `ichichuang/cinema-tv`, with `main` as its sole branch. Source and update metadata live at `main/update/version.json` and `main/update/sources.json`; approved, signed installation packages belong in **this repository's GitHub Releases** as immutable version-tagged assets (`releases/download/vX.Y.Z/cinema-tv-X.Y.Z.apk`). Keep release APK binaries out of Git-tracked source files. A future signed manifest must reference the verified release asset and record its exact size, SHA-256, version and independently authenticated publisher metadata. The present version.json is a non-installable frozen status with no APK URL; it deliberately fails the legacy installable-manifest parser. sources.json contains no real media services. No such new release is authorized or available yet.

The existing installed 0.9.8 APK is a separate compatibility constraint: it reads default manifests and source catalogs from `ichichuang/cinema-tv-releases` and checks the current APK signer. Changing Kotlin defaults in source cannot change an APK that is already installed. The old distribution repository remains an unchanged **temporary bridge**, not a second long-term product repository; do not delete it before an independently trusted migration has been completed and its old-client impact has been accepted. Releasing an APK signed by a replacement key alone would not pass the already-installed legacy updater's certificate policy. Do not silently break Android 11 in-place upgrades, remove historical artifacts, move tags, or permit the compromised identity to sign future production updates.

The original source repository also still contains sensitive Git history and PR references. Moving `main` or deleting the remediation branch is not sufficient history sanitization; keep private until its reachable and GitHub-retained references have been remediated and verified with an approved procedure. The owner has approved Apache-2.0 and code/image redistribution rights; the remaining security and compatibility gates still block publication. The present changes only prepare the one-repository layout; they do **not** authorize publishing, deleting, replacing, or making any repository public.


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

The legacy updater reads the first parseable update manifest, compares versionCode, downloads APK bytes, checks package/version/signing information, then asks the system installer for confirmation. Existing fields and aliases remain compatible. Optional `sha256` is checked when present; malformed values fail rather than falling back to legacy mode. Both downloaded and cached files are rechecked for size, package, exact manifest version, strictly newer installed version and complete current-signer equality. Missing metadata/signers, failed verification and interrupted partial files are rejected/cleaned; downloads are serialized. This does not accept rotated signers or revoke the compromised old key.

The updater uses a separate client that rejects only HTTPS-to-HTTP downgrades before each redirect hop. HTTP-to-HTTPS upgrades and same-scheme redirects remain compatible, and HTTP media playback is unchanged. Existing explicitly configured HTTP update URLs remain accepted for compatibility and are still an exposure. Independently authenticated metadata/artifacts and the migration bootstrap are designed together in [SIGNING_MIGRATION.md](SIGNING_MIGRATION.md), pending review and owner-controlled trust material.

A digest received from the same untrusted manifest is not independent authenticity. A successful match against the compromised old certificate also does not prove that the publisher is legitimate. Do not describe transport hardening or certificate comparison alone as remediation of the compromised key.

## Publishing controls

publish-to-repo.ps1, publish-update.ps1 and publish-sources.ps1 all fail before modifying files, reading credentials, pushing Git refs, or contacting a release API. Their old publication behavior is unavailable during the freeze. Do not run historical copies. GitHub workflow ID `387692952` is disabled in workflow management; ordinary verification remains active. Existing remote artifacts, tags and manifests are preserved as evidence.

When publication is eventually approved, use a new monotonically increasing versionCode, immutable new tags, a reviewed manifest, signature verification, APK SHA-256 and size checks, and read-back verification of every distributed artifact. Never move old release tags or overwrite an existing release asset.

## Offline release preflight and publishing gate

`scripts/verify_release_candidate.py` verifies a locally staged, independently signed candidate without network access, installation or private keys. It requires an exact versioned asset URL in this repository, the actual production package/version, strictly increasing versionCode and publisher sequence, exact bytes and SHA-256, the complete independently approved current signer set, and an AUTHUPD1 signature verified with a separately pinned public RSA key. It rejects the compromised old APK signer. Public inputs and the previously approved version/sequence floors must come from the maintainer's independent release record; never take them from the candidate itself.

```sh
python3 scripts/verify_release_candidate.py \
  --apk "$REVIEWED_APK" --manifest "$REVIEWED_VERSION_JSON" \
  --previous-version-code "$PREVIOUS_APPROVED_VERSION_CODE" \
  --previous-sequence "$PREVIOUS_APPROVED_SEQUENCE" \
  --expected-signer "$APPROVED_APK_CERT_SHA256" \
  --aapt2 "$ANDROID_BUILD_TOOLS/aapt2" --apksigner "$ANDROID_BUILD_TOOLS/apksigner" \
  --publisher-envelope "$REVIEWED_AUTH_ENVELOPE" \
  --publisher-public-key "$INDEPENDENT_PUBLIC_PEM" \
  --publisher-public-key-sha256 "$APPROVED_SPKI_DER_SHA256" \
  --publisher-key-id "$APPROVED_PUBLISHER_KEY_ID"
```

Repeat `--expected-signer` for every approved current signer. The manifest uses `versionCode`, `versionName`, `apkUrl`, `sizeBytes`, `sha256`, `sequence`, `publisherKeyId` and the sorted `signers` array; the corresponding authenticated fields must agree exactly. AUTHUPD1 bytes and their bounded protocol are described in [AUTHENTICATED_UPDATES.md](AUTHENTICATED_UPDATES.md). No production key or trust input is selected by this tool. A successful preflight explicitly reports `API30-tools-only`, `publicationAuthorized=false` and the current publishing freeze; it is not device or release approval.

The existing manual release workflow remains disabled in GitHub management, read-only, and stops at the fixed source publishing gate before any signing, upload or network publication. Ordinary verification stays credential-free. This is a frozen preflight entry, not an implemented production promotion path. After independent key custody, trust-design approval and device gates, separately review the production release workflow: keep APK and metadata signing outside ordinary CI; require an independently controlled protected release environment with no self-approval; verify the exact main SHA and successful CI; stage all signed assets and authenticated metadata in a draft in this repository; enable [immutable releases](https://docs.github.com/en/code-security/concepts/supply-chain-security/immutable-releases) before publishing; read back the tag/commit, every asset's size/hash/signature and matching main metadata. Do not invent active publishing steps or credentials while those prerequisites are missing.

## References

- [APK signature scheme v3](https://source.android.com/docs/security/features/apksigning/v3)
- [apksigner](https://developer.android.com/tools/apksigner)
- [Removing sensitive data from GitHub](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)
