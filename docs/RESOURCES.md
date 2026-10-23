# Resource and evidence boundaries

This repository is being prepared for open source. Public documentation must describe reproducible workflows, not a maintainer's personal filesystem layout, subscription contents, tokens, or private device evidence.

| Resource | Location and policy |
| --- | --- |
| Application source, build configuration, documentation | Tracked here; subject to security and license review |
| Repository-local FongMi setup skill | .agents/skills/fongmi-tv-setup; independent of the application |
| Approved future APK releases | GitHub Releases in ichichuang/cinema-tv only; no approved asset is currently available |
| Existing legacy APKs and manifests | cinema-tv-releases temporary old-client bridge; 14 APKs are tracked Git files, not GitHub Release assets; unchanged |
| Android SDK and local tool state | Ignored .local/toolchain or the developer's configured SDK directory |
| Local audit evidence and migration manifests | Ignored .local/evidence and .local/consolidation; never publish raw contents |
| Signing keystore and credentials | Outside the checkout and encrypted offline storage; backup verification remains outstanding |
| Gradle caches and generated build outputs | Local/generated; never used as a signing-key archive |

Previous consolidation documents recorded local inventory counts and release hashes. Those are historical observations, not new verification results. Detailed personal paths and private evidence identifiers have been removed from this public-facing index. Existing local evidence was not moved or deleted by the repository changes.

Use official upstream distributions for Android tools and any independent television software. Verify current release identity and licensing separately. Private subscriptions, local backups, device identifiers, and media-source credentials do not belong in the open-source repository.

Before changing repository visibility, audit both current tracked files and all historical refs, logs, artifacts, releases, and known forks. This document does not certify that historical information has been purged. The release gates are in [SECURITY.md](../SECURITY.md).
