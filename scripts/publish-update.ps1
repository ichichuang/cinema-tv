# 发布新版本（在线更新的发布端）
#
# 做三件事：
#   1. 找到刚编译好的 release APK，算出大小
#   2. 生成 update/version.json（有线电视那端就是读这个文件判断要不要升级）
#   3. 有 token 的话：用 GitHub API 建 release + 上传 APK 和 version.json 两个附件
#      （没有 token 就只写文件 + 打印接下来的手工步骤）
#
# 用法：
#   .\scripts\publish-update.ps1 -VersionName 0.5.0 -VersionCode 5
#
# Token 从哪来（二选一，都不写进仓库）：
#   - 环境变量 GITHUB_TOKEN
#   - 文件 C:\Users\<你>\.github_token（第一行就是这个 token）
#
# ⚠️ 为什么要 token：GitHub 的 release 上传只有 REST API 一条路，git push 做不到。
#    而我们**必须**用 release 附件（不用仓库里的 raw 文件），因为
#    raw.githubusercontent.com 在国内连不上，release 附件走的域名却是通的 —— 实测结论。

param(
    [Parameter(Mandatory = $true)][string]$VersionName,
    [Parameter(Mandatory = $true)][int]$VersionCode,
    [string]$Repo = "ichichuang/cinema-tv-releases",
    [string]$Apk = "",
    [string]$NotesFile = "update/notes.txt",
    # 镜像前缀（可选）：填了就在清单里额外输出一个 mirrorUrl，给电视连不上 GitHub 时备用
    [string]$MirrorPrefix = "https://gh-proxy.com/",
    [switch]$SkipRelease
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

function Write-Utf8NoBom([string]$Path, [string]$Text) {
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Text, $utf8)
}

# ── 1. 找 APK ────────────────────────────────────────────────
if ([string]::IsNullOrWhiteSpace($Apk)) {
    $found = Get-ChildItem "app\build\outputs\apk\release\*.apk" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $found) { throw "找不到 release APK，先跑 .\gradlew.bat :app:assembleRelease" }
    $Apk = $found.FullName
}
$apkItem = Get-Item $Apk
$sizeBytes = $apkItem.Length
$assetName = "cinema-tv-$VersionName.apk"
$tag = "v$VersionName"

# ── 2. 生成 version.json ─────────────────────────────────────
$notes = ""
if (Test-Path $NotesFile) {
    $notes = (Get-Content $NotesFile -Raw -Encoding UTF8).Trim()
}

$downloadBase = "https://github.com/$Repo/releases/download/$tag/$assetName"
$manifestUrl = "https://github.com/$Repo/releases/latest/download/version.json"

$manifest = [ordered]@{
    versionCode = $VersionCode
    versionName = $VersionName
    apkUrl      = $downloadBase
    sizeBytes   = $sizeBytes
    notes       = $notes
}
if (-not [string]::IsNullOrWhiteSpace($MirrorPrefix)) {
    $manifest["mirrorUrl"] = "$MirrorPrefix$downloadBase"
}

$json = $manifest | ConvertTo-Json -Depth 4
Write-Utf8NoBom (Join-Path $root "update\version.json") $json

Write-Host "----------------------------------------------"
Write-Host "APK    : $apkItem"
Write-Host "大小   : $([math]::Round($sizeBytes / 1MB, 2)) MB ($sizeBytes bytes)"
Write-Host "标签   : $tag"
Write-Host "清单   : update\version.json"
Write-Host "清单地址: $manifestUrl"
Write-Host "----------------------------------------------"

if ($SkipRelease) {
    Write-Host "SkipRelease: 只写了清单，没有动 GitHub。"
    exit 0
}

# ── 3. 发 release ────────────────────────────────────────────
$token = $env:GITHUB_TOKEN
$tokenFile = Join-Path $env:USERPROFILE ".github_token"
if ([string]::IsNullOrWhiteSpace($token) -and (Test-Path $tokenFile)) {
    $token = (Get-Content $tokenFile -First 1).Trim()
}
if ([string]::IsNullOrWhiteSpace($token)) {
    Write-Host ""
    Write-Host "没有找到 GitHub token，APK 还没上传。"
    Write-Host "手工两步（各一次，之后都不用再做）："
    Write-Host "  1. 在 GitHub 上建一个 public 仓库：$Repo"
    Write-Host "  2. 生成 fine-grained token（只勾这个仓库 + Contents: Read and write），"
    Write-Host "     存到 $tokenFile 的第一行，然后重跑本脚本。"
    exit 2
}

# curl 要绕开 Windows 的证书吊销检查（这台机器连不上 CRL 服务器，报错是假阴性）
$curlBase = @("--ssl-no-revoke", "-sS", "-L")
$headers = @("-H", "Authorization: Bearer $token", "-H", "X-GitHub-Api-Version: 2022-11-28")

function Invoke-GitHub([string[]]$CurlArgs) {
    $out = & curl.exe @CurlArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw "curl 失败: $($out -join ' ')" }
    return $out
}

# 建 release（已存在同 tag 就复用）
$bodyFile = Join-Path $env:TEMP "cinema-release.json"
$body = [ordered]@{
    tag_name   = $tag
    name       = $VersionName
    body       = $notes
    draft      = $false
    prerelease = $false
} | ConvertTo-Json -Depth 4
Write-Utf8NoBom $bodyFile $body

$api = "https://api.github.com/repos/$Repo/releases"
$resp = Invoke-GitHub ($curlBase + $headers + @("-X", "POST", "-H", "Content-Type: application/json", "--data-binary", "@$bodyFile", $api))
$release = $resp | ConvertFrom-Json

$releaseId = $release.id
if (-not $releaseId) {
    # 大多数情况是 tag 已存在 → 取回来
    $resp = Invoke-GitHub ($curlBase + $headers + @("$api/tags/$tag"))
    $release = $resp | ConvertFrom-Json
    $releaseId = $release.id
}
if (-not $releaseId) { throw "没能创建或找到 release（检查 token 权限 / 网络）" }
Write-Host "release id: $releaseId"

function Upload-Asset([string]$Path, [string]$Name, [string]$ContentType) {
    # 先删同名旧附件，否则 GitHub 会报 422
    $listResp = Invoke-GitHub ($curlBase + $headers + @("$api/$releaseId/assets"))
    $existing = $listResp | ConvertFrom-Json
    foreach ($a in $existing) {
        if ($a.name -eq $Name) {
            Invoke-GitHub ($curlBase + $headers + @("-X", "DELETE", "$api/assets/$($a.id)")) | Out-Null
        }
    }
    $uploadUrl = "https://uploads.github.com/repos/$Repo/releases/$releaseId/assets?name=$Name"
    Invoke-GitHub ($curlBase + $headers + @(
        "-X", "POST",
        "-H", "Content-Type: $ContentType",
        "--data-binary", "@$Path",
        $uploadUrl
    )) | Out-Null
    Write-Host "uploaded: $Name"
}

Upload-Asset $apkItem.FullName $assetName "application/vnd.android.package-archive"
Upload-Asset (Join-Path $root "update\version.json") "version.json" "application/json"

Write-Host ""
Write-Host "发布完成 ✅"
Write-Host "电视那边的更新地址（也是应用默认值）："
Write-Host "  $manifestUrl"
Write-Host "电视上打开应用 → 设置 → 检查更新，应该能看到 v$VersionName"
