# Release publishing is frozen during the signing credential incident.
# This script intentionally performs no file, Git, network, or credential operations.
param(
    [string]$VersionName,
    [int]$VersionCode,
    [string]$Repo,
    [string]$WorkDir,
    [string]$Apk,
    [string]$NotesFile,
    [switch]$NoPush
)
throw "Release publishing is frozen. See SECURITY.md and docs/RELEASE.md. No files or remote objects were changed."
