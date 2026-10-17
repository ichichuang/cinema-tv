# 本地构建脚本（Windows）—— 本项目出包的主路线
#
# 一键出 APK 并自动做红线检查：
#   powershell -ExecutionPolicy Bypass -File .\scripts\build-local.ps1
#
# （如果提示"禁止运行脚本"，就是你看到的那句话的意思：
#   Windows 默认不允许运行 .ps1，用上面那种带 Bypass 的写法即可，不用改系统设置）
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
        Write-Output ""
        # 自动跑红线检查：ABI / cleartext / Leanback 入口 / 体积
        $check = Join-Path $PSScriptRoot 'check-apk.ps1'
        if (Test-Path $check) {
            & $check -ApkPath $apk.FullName
        }
    }
}

exit $code
