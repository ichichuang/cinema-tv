# Development and verification

## Toolchain and compatibility

Use JDK 17 or a compatible installed JDK, the repository's Gradle 9.8.1 wrapper, and Android SDK platform 37. The Java compilation target remains 17. Configure ANDROID_HOME or ignored local.properties for your machine; do not commit absolute personal paths. Existing ignored .local/toolchain resources can be reused.

Production identity remains com.ichichuang.cinema, versionCode 18, versionName 0.9.8, minSdk 24, targetSdk 28, and armeabi-v7a. Debug builds append .debug to the application ID and -debug to the version name so contributor builds cannot overwrite production data.

## Credential-free validation

```sh
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check_repository_security.py
bash ./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest --no-daemon --no-configuration-cache
```

The security check reads the Git index, including staged content rather than unstaged replacements. Stage intended changes before running it. It reports selected credential, personal-path, and credential-bearing URL hazards without printing matched values. It does not scan historical commits or prove that no unknown credential type exists. Complement it with the installed scanner: `gitleaks git --redact=100 --no-banner --log-opts=--all .`. Never print unredacted scanner reports.

Release validation produces app/build/outputs/apk/release/app-release-unsigned.apk. This is not a signed distribution artifact and must not be installed as a production update. No APK is uploaded by the validation workflow. Use --offline only when all required tools and dependencies are already cached.

## Signing boundary

The old tracked keystore is removed from the current tree, not erased from Git history. An owner-controlled encrypted local backup has been recovered and compared, including the key and credential configuration. Its offline copy and migration still require separate verification. Do not pull old signing material into a new public commit, issue, or artifact.

Release signing is source-controlled as frozen. Merely setting cinema.enableReleaseSigning=true fails. After a separately reviewed migration, ignored signing.properties or the following environment variables provide signing inputs: SIGNING_STORE_FILE, SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD, and optional SIGNING_STORE_TYPE. The environment takes precedence and no password defaults exist. The keystore must be outside the checkout. signing.properties.example contains blank placeholders only.

Do not pass passwords as Gradle properties or command-line arguments. Disable configuration-cache, build scans, debug logging, and credential-bearing artifact/cache uploads in any future signing job. A protected GitHub Environment and least-privilege secret provisioning are prerequisites, not settings created by this change.

## Verification boundaries

Builds do not establish successful in-place installation, retained user data, video/audio playback, or D-pad behavior on the television. Inspect APK manifest, ABI, signatures, and SHA-256 separately. The existing APK inspection scripts cover only part of those checks and may retain platform-specific assumptions.

The legacy release workflow is disabled in GitHub workflow management; its source is also a manual-only refusal. The separate verification workflow runs on main and pull requests with read-only permissions and pinned actions; it builds debug and unsigned release, runs both variants' Lint and the registered debug unit-test task, reports actual JUnit XML totals, and does not publish artifacts. Historical workflow reruns are not disabled by editing today's workflow alone. Recheck administrator state before a future release.

Windows `build-local.ps1` uses the existing JAVA_HOME and ANDROID_HOME/ANDROID_SDK_ROOT and the repository wrapper. For debug/release assembly it invokes the existing packaged-APK checker and propagates failure. It never chooses an old signed release file. Windows execution still requires validation on that platform; this remediation was built on macOS and Linux CI.

## Architecture constraints

Preserve feature/domain/data/core/di/nav, production package identity, stored settings, playback progress, and navigation behavior during the security work. Reuse Color, Motion, TvMetrics, and tvFocus for subsequent UI changes. Do not combine visual redesign with signing identity changes.
