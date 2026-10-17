# 发布新版本 · 零密钥路线（不需要 GitHub token）
#
# 思路：把 APK 和 version.json 直接提交进一个**公开的发布仓库**，用国内可访问的
#       CDN / 代理域名对外提供下载。全程只用 `git push`（SSH 已免密），不碰任何密钥。
#
# 为什么不用仓库的 raw 地址：
#   raw.githubusercontent.com 国内连不上（实测超时）→ 所以对外链接走这两个域名：
#     · cdn.jsdelivr.net   —— 快，带缓存；APK 用版本 tag 固定，缓存正是我要的
#     · gh-proxy.com       —— 直通，无缓存；清单用它，保证"发完立刻能看到新版"
#
# 用法：
#   .\scripts\publish-to-repo.ps1 -VersionName 0.6.0 -VersionCode 6
#   再传：-Repo "ichichuang/cinema-tv-releases"  -WorkDir "$env:TEMP\cinema-releases"

param(
    [Parameter(Mandatory = $true)][string]$VersionName,
    [Parameter(Mandatory = $true)][int]$VersionCode,
    [string]$Repo = "ichichuang/cinema-tv-releases",
    [string]$WorkDir = "$env:TEMP\cinema-releases",
    [string]$Apk = "",
    [string]$NotesFile = "update/notes.txt",
    # 只准备文件、不推送（先人工看一眼再推）
    [switch]$NoPush
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$git = "C:\Users\cc\Tools\PortableGit\cmd\git.exe"
if (-not (Test-Path $git)) { $git = "git" }

function Write-Utf8NoBom([string]$Path, [string]$Text) {
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Text, $utf8)
}

# ── 1. 找 APK ────────────────────────────────────────────────
if ([string]::IsNullOrWhiteSpace($Apk)) {
    $found = Get-ChildItem (Join-Path $root "app\build\outputs\apk\release\*.apk") -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $found) { throw "找不到 release APK，先跑 .\gradlew.bat :app:assembleRelease" }
    $Apk = $found.FullName
}
$apkItem = Get-Item $Apk
$assetName = "cinema-tv-$VersionName.apk"
$tag = "v$VersionName"

# ── 2. 准备发布仓库的工作副本 ────────────────────────────────
if (-not (Test-Path (Join-Path $WorkDir ".git"))) {
    Write-Host "cloning $Repo -> $WorkDir"
    & $git clone "git@github.com:$Repo.git" $WorkDir
    if ($LASTEXITCODE -ne 0) { throw "克隆失败：先在 GitHub 上建好公开仓库 $Repo（空仓库即可）" }
}
Push-Location $WorkDir
try {
    & $git pull --ff-only 2>$null
} catch { }
Pop-Location

Copy-Item $apkItem.FullName (Join-Path $WorkDir $assetName) -Force

$notes = ""
$notesPath = Join-Path $root $NotesFile
if (Test-Path $notesPath) { $notes = (Get-Content $notesPath -Raw -Encoding UTF8).Trim() }

# APK 用 jsDelivr（tag 固定 = 内容永不改变，缓存是优点）
$apkUrl = "https://cdn.jsdelivr.net/gh/$Repo@$tag/$assetName"
# 兜底镜像：gh-proxy 直通 raw（两个域名都实测可达）
$mirrorUrl = "https://gh-proxy.com/https://raw.githubusercontent.com/$Repo/$tag/$assetName"

$manifest = [ordered]@{
    versionCode = $VersionCode
    versionName = $VersionName
    apkUrl      = $apkUrl
    sizeBytes   = $apkItem.Length
    notes       = $notes
    mirrorUrl   = $mirrorUrl
}
$json = $manifest | ConvertTo-Json -Depth 4
Write-Utf8NoBom (Join-Path $WorkDir "version.json") $json
Write-Utf8NoBom (Join-Path $root "update\version.json") $json

Write-Host "----------------------------------------------"
Write-Host "APK      : $assetName ($([math]::Round($apkItem.Length / 1MB, 2)) MB)"
Write-Host "tag      : $tag"
Write-Host "APK 直链 : $apkUrl"
Write-Host "镜像     : $mirrorUrl"
Write-Host "清单     : version.json（发布仓库根目录）"
Write-Host "----------------------------------------------"

if ($NoPush) { Write-Host "NoPush: 文件已就位，没有推送。"; exit 0 }

Push-Location $WorkDir
try {
    & $git add -A
    & $git commit -m "release: v$VersionName (versionCode $VersionCode)" | Write-Host
    & $git tag -f $tag
    & $git push origin HEAD
    & $git push origin $tag -f
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "发布完成"
Write-Host "电视那边的更新地址（应用默认值之一）："
Write-Host "  https://gh-proxy.com/https://raw.githubusercontent.com/$Repo/main/version.json"
