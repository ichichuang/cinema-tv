# 本地构建脚本（Windows）
#
# 这个项目的默认路线是「云端构建」（GitHub Actions），
# 这个脚本只是给本机快速验证编译用 —— 不用装 Android Studio，
# 一键就能出 APK：.\scripts\build-local.ps1
#
# 依赖：便携工具链已就位
#   JDK 17  : C:\Users\cc\Tools\jdk17
#   Android SDK: C:\Users\cc\Tools\android-sdk

param(
    [string]$Task = "assembleRelease"
)

$ErrorActionPreference = 'Continue'

$jhome = (Get-ChildItem 'C:\Users\cc\Tools\jdk17' -Directory -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
if (-not $jhome) { Write-Output "找不到 JDK：C:\Users\cc\Tools\jdk17"; exit 1 }

$env:JAVA_HOME = $jhome
$env:ANDROID_HOME = 'C:\Users\cc\Tools\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

Set-Location (Join-Path $PSScriptRoot '..')

# 走本机独立 Gradle 分发（官方分发地址会 307 跳到 github.com，这台机器 HTTPS 不通；
# 所以 Gradle 已经从镜像下好放在本地）。没有就退回标准 gradlew。
$gradleBat = 'C:\Users\cc\Tools\gradle-9.8.1\bin\gradle.bat'

Write-Output "JAVA_HOME    = $env:JAVA_HOME"
Write-Output "ANDROID_HOME = $env:ANDROID_HOME"
Write-Output "TASK         = $Task"
Write-Output "----------------------------------------"

if (Test-Path $gradleBat) {
    Write-Output "gradle: 本地分发 9.8.1"
    & $gradleBat $Task --console=plain
} else {
    Write-Output "gradle: gradlew（wrapper）"
    & .\gradlew.bat $Task --console=plain
}
$code = $LASTEXITCODE

Write-Output "----------------------------------------"
Write-Output "gradle exit: $code"

if ($code -eq 0) {
    $apk = Get-ChildItem "app\build\outputs\apk\release\*.apk" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($apk) {
        Write-Output ("APK: {0}" -f $apk.FullName)
        Write-Output ("SIZE: {0} MB" -f [math]::Round($apk.Length / 1MB, 2))
    }
}

exit $code
