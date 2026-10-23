# Publication is frozen during the signing incident, including source catalogs.
param(
    [string]$Repo,
    [string]$WorkDir,
    [string]$Source,
    [switch]$NoPush
)
$ErrorActionPreference = 'Stop'
throw '发布已冻结。推荐源清单也不得写入分发仓库；请参阅 SECURITY.md 和 docs/RELEASE.md。'
