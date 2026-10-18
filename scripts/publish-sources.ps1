# 发布「推荐源清单」（sources.json）到公开发布仓库的根目录
#
# 为什么单独一个脚本：清单和 APK 的发布节奏**不一样** ——
#   · APK：一个版本发一次（大头是构建 + 装机）
#   · 清单：随时可发（换域名、加源、调排序），改一个 JSON 推上去就行
#     → 老版本应用下次刷新列表就能用上新源，**不用重新装机**
#
# 用法：
#   .\scripts\publish-sources.ps1
#   再传：-Repo "ichichuang/cinema-tv-releases" -WorkDir "$env:TEMP\cinema-releases"
#
# 提醒（实测过的缓存行为）：gh-proxy 直通 raw 无缓存、发布立即可见；
# jsDelivr 的分支缓存会滞后十几小时 —— 验证时以 gh-proxy 那条为准。

param(
    [string]$Repo = "ichichuang/cinema-tv-releases",
    [string]$WorkDir = "$env:TEMP\cinema-releases",
    [string]$Source = "update/sources.json",
    # 只准备文件、不推送（先人工看一眼再推）
    [switch]$NoPush
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$git = "C:\Users\cc\Tools\PortableGit\cmd\git.exe"
if (-not (Test-Path $git)) { $git = "git" }

$src = Join-Path $root $Source
if (-not (Test-Path $src)) { throw "找不到清单文件：$src" }

# ── 1. 先做一次快速体检 ─────────────────────────────────────
# 这个文件推上去 = 所有用户的一键导入都靠它。格式错了，
# 用户端会直接变成"获取推荐源失败" —— 所以宁可在这里拦下。
$json = Get-Content $src -Raw -Encoding UTF8 | ConvertFrom-Json
if ($null -eq $json -or $null -eq $json.sources) { throw "清单里没有 sources 数组" }
$items = @($json.sources)
if ($items.Count -lt 1) { throw "sources 是空的" }
foreach ($s in $items) {
    if ([string]::IsNullOrWhiteSpace($s.name) -or [string]::IsNullOrWhiteSpace($s.url)) {
        throw "有一条源缺 name 或 url：$($s | ConvertTo-Json -Compress)"
    }
    if (-not $s.url.StartsWith("http")) {
        throw "接口地址格式不对：$($s.url)"
    }
}
$dup = $items | Group-Object url | Where-Object { $_.Count -gt 1 }
if ($dup) { throw "有重复的接口地址：$($dup[0].Name)" }
Write-Host ("清单体检通过：" + $items.Count + " 条源")

# ── 2. 准备发布仓库的工作副本 ────────────────────────────────
if (-not (Test-Path (Join-Path $WorkDir ".git"))) {
    Write-Host "cloning $Repo -> $WorkDir"
    & $git clone "git@github.com:$Repo.git" $WorkDir
    if ($LASTEXITCODE -ne 0) { throw "克隆失败：先在 GitHub 上建好公开仓库 $Repo" }
}

Push-Location $WorkDir
try {
    & $git pull --ff-only 2>$null
} catch { }

Copy-Item $src (Join-Path $WorkDir "sources.json") -Force
& $git add sources.json

$staged = & $git diff --cached --name-only
if ([string]::IsNullOrWhiteSpace($staged)) {
    Write-Host "清单内容没有变化，不用再推一次"
    Pop-Location
    exit 0
}

if ($NoPush) {
    Write-Host "已准备好（-NoPush）：$WorkDir\sources.json"
    Pop-Location
    exit 0
}

& $git commit -m "sources: 更新推荐源清单（$(Get-Date -Format 'yyyy-MM-dd HH:mm')）"
& $git push origin main
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) { throw "推送失败（退出码 $code）" }

Write-Host "已发布。验证用的直通地址（无缓存，立即可见）："
Write-Host "  https://gh-proxy.com/https://raw.githubusercontent.com/$Repo/main/sources.json"
