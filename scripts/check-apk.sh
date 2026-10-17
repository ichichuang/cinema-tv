#!/usr/bin/env bash
# TV 红线自动检查：每次云端构建后自动跑，任何一条不过就直接失败，不让你白下载一个装不上的包
set -euo pipefail

APK=$(ls -1 app/build/outputs/apk/release/*.apk | head -n1)
SIZE=$(stat -c%s "$APK")
echo "== APK: $APK"
echo "== 体积: $((SIZE / 1024 / 1024)) MB"

echo "== 原生库清单:"
if unzip -l "$APK" | awk '{print $4}' | grep -E '^lib/' ; then
  if unzip -l "$APK" | awk '{print $4}' | grep -qE '^lib/(arm64-v8a|x86)'; then
    echo "::error:: 检测到非 armeabi-v7a 原生库，FFALCON 65S78A 上会一启动就崩"
    exit 1
  fi
  echo "== 原生库只有 32 位，符合电视要求"
else
  echo "   (无原生库，符合预期：纯 Kotlin 工程)"
fi

BT=$(ls -d "${ANDROID_SDK_ROOT:-$ANDROID_HOME}"/build-tools/* 2>/dev/null | sort -V | tail -n1 || true)
if [ -n "${BT:-}" ] && [ -x "$BT/aapt2" ]; then
  echo "== 明文 HTTP 检查:"
  if "$BT/aapt2" dump xmltree --file AndroidManifest.xml "$APK" | grep -qi "usesCleartextTraffic"; then
    echo "   usesCleartextTraffic 已开启（在线接口必需）"
  else
    echo "::warning:: 未发现 usesCleartextTraffic，阶段 1 接在线接口时大概率白屏"
  fi
fi

if [ "$SIZE" -gt 31457280 ]; then
  echo "::warning:: APK 超过 30MB，电视安装体验会变差"
fi

echo "== 全部检查通过"
