# 本地 APK 红线检查（等价于 CI 里的 scripts/check-apk.sh）
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File .\scripts\check-apk.ps1
#   powershell -ExecutionPolicy Bypass -File .\scripts\check-apk.ps1 -ApkPath xxx.apk
#
# 检查内容（每条都是"上电视会出事"级别）：
#   1. 原生库只能是 armeabi-v7a（64 位库装到这台电视必崩）
#   2. 必须开启 usesCleartextTraffic（在线片源多为 http）
#   3. minSdk / targetSdk / 应用名 / 横幅 / Leanback 入口
#   4. 体积（超过 30MB 提醒）
#
# 注意：本文件必须保存为「UTF-8 带 BOM」，否则 Windows PowerShell 5.1
#       会按 ANSI 读取，中文全部变乱码导致解析报错。

param([string]$ApkPath)

$ErrorActionPreference = 'Continue'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$sdk  = 'C:\Users\cc\Tools\android-sdk'
$repo = Split-Path $PSScriptRoot -Parent
$fail = 0

if (-not $ApkPath) {
    $ApkPath = (Get-ChildItem (Join-Path $repo 'app\build\outputs\apk\release\*.apk') -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}
if (-not $ApkPath -or -not (Test-Path $ApkPath)) { Write-Output '✗ 找不到 APK'; exit 1 }

Write-Output "APK : $ApkPath"
$sizeMB = [math]::Round((Get-Item $ApkPath).Length / 1MB, 2)
Write-Output ("大小: {0} MB" -f $sizeMB)
if ($sizeMB -gt 30) { Write-Output '! 超过 30MB，后面要考虑开 isMinifyEnabled' }

# ── 1. 原生库 ABI 红线 ───────────────────────────────────────
$zip  = [System.IO.Compression.ZipFile]::OpenRead($ApkPath)
$libs = @($zip.Entries | Where-Object { $_.FullName -like 'lib/*' } | ForEach-Object { $_.FullName })

if ($libs.Count -eq 0) {
    Write-Output '原生库: 无（纯 Kotlin，零 ABI 风险）✓'
} else {
    $abis = @($libs | ForEach-Object { ($_ -split '/')[1] } | Sort-Object -Unique)
    Write-Output ("原生库 ABI: " + ($abis -join ', '))
    foreach ($l in $libs) { Write-Output ("    {0}" -f $l) }
    $bad = @($abis | Where-Object { $_ -ne 'armeabi-v7a' })
    if ($bad.Count -gt 0) { Write-Output ("✗ 出现非 32 位库: " + ($bad -join ', ')); $fail = 1 }
    else { Write-Output '✓ 只有 armeabi-v7a' }
}

# ── 2. 明文流量开关：从 APK 内的二进制清单里找（badging 不列这个属性）──
$entry = $zip.Entries | Where-Object { $_.FullName -eq 'AndroidManifest.xml' } | Select-Object -First 1
if ($entry) {
    $ms = New-Object System.IO.MemoryStream
    $entry.Open().CopyTo($ms)
    $bytes = $ms.ToArray()
    $ms.Dispose()
    # 二进制 XML 的字符串池可能是 UTF-8 也可能是 UTF-16，两种都查
    $asUtf8 = [System.Text.Encoding]::UTF8.GetString($bytes)
    $asUni  = [System.Text.Encoding]::Unicode.GetString($bytes)
    if ($asUtf8.Contains('usesCleartextTraffic') -or $asUni.Contains('usesCleartextTraffic')) {
        Write-Output '✓ 已开启 usesCleartextTraffic（http 片源不会白屏）'
    } else {
        Write-Output '✗ 未开启 usesCleartextTraffic：http:// 的接口和片源会全部失败'
        $fail = 1
    }
}
$zip.Dispose()

# ── 3. 用 aapt2 读真实 APK 清单 ──────────────────────────────
$bt = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory -ErrorAction SilentlyContinue |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
$aapt2 = if ($bt) { Join-Path $bt.FullName 'aapt2.exe' } else { $null }

if ($aapt2 -and (Test-Path $aapt2)) {
    $out = & $aapt2 dump badging $ApkPath 2>&1

    ($out | Select-String '^package:' -List)        | ForEach-Object { Write-Output $_.Line }
    ($out | Select-String '^application-label:' -List) | ForEach-Object { Write-Output $_.Line }
    ($out | Select-String "^application: " -List)   | ForEach-Object { Write-Output $_.Line }
    ($out | Select-String '^sdkVersion:' -List)     | ForEach-Object { Write-Output $_.Line }
    ($out | Select-String '^targetSdkVersion:' -List) | ForEach-Object { Write-Output $_.Line }
    ($out | Select-String '^native-code:' -List)    | ForEach-Object { Write-Output $_.Line }

    # Leanback 入口（aapt2 里叫 leanback-launchable-activity，不是 LEANBACK_LAUNCHER）
    if ($out | Select-String 'leanback-launchable-activity') {
        Write-Output '✓ 有 leanback-launchable-activity（电视桌面能认）'
    } else {
        Write-Output '✗ 缺少 leanback-launchable-activity：TV 桌面上不会出现图标'
        $fail = 1
    }

    # 电视横幅图（banner）—— 在 application 行里
    $appLine = ($out | Select-String "^application: " -List | Select-Object -First 1)
    if ($appLine -and $appLine.Line -match "banner='(res/[^']+)'") {
        Write-Output ("✓ 有电视横幅图: " + $Matches[1])
    } else {
        Write-Output '! 没读到横幅图：部分 TV 桌面会显示成灰块'
    }
} else {
    Write-Output '（build-tools 未安装，跳过清单检查）'
}

Write-Output '----------------------------------------'
if ($fail -eq 0) { Write-Output '红线检查通过 ✓' } else { Write-Output '红线检查失败 ✗' }
exit $fail
