# Windows contributor build. Requires an existing JDK and Android SDK.
param([string]$Task = ":app:assembleDebug")
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { throw '请先使用已有工具管理器配置 JAVA_HOME。' }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT }
if (-not $env:ANDROID_HOME) { throw '请先配置 ANDROID_HOME 或 ANDROID_SDK_ROOT。' }
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
Push-Location (Join-Path $PSScriptRoot '..')
try {
    & .\gradlew.bat $Task --no-daemon --no-configuration-cache --console=plain
    $code = $LASTEXITCODE
    if ($code -ne 0) { exit $code }
    $variant = $null
    if ($Task -match 'assembleDebug$') { $variant = 'debug'; $apkName = 'app-debug.apk' }
    if ($Task -match 'assembleRelease$') { $variant = 'release'; $apkName = 'app-release-unsigned.apk' }
    if ($variant) {
        $apk = Join-Path (Get-Location) "app\build\outputs\apk\$variant\$apkName"
        $check = Join-Path $PSScriptRoot 'check-apk.ps1'
        if (-not (Test-Path $apk) -or -not (Test-Path $check)) {
            throw '缺少预期 APK 或验证脚本，构建不能视为验证成功。'
        }
        & powershell -NoProfile -File $check -ApkPath $apk
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    Write-Output '构建结束。正式签名与发布仍然冻结；产物保留在 app/build/outputs/。'
} finally { Pop-Location }
