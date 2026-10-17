# 极简影院 · cinema-tv

给 **FFALCON 65S78A（雷鸟鹏7 2026款 / 灵控系统）** 定制的 Android TV 电影应用。
目标只有一个：**用遥控器舒服地挑电影、看电影**，没有任何多余功能。

---

## 现在处于阶段 0（骨架）

| 阶段 | 内容 | 状态 |
|---|---|---|
| **0. 骨架** | 首页界面 + 遥控器焦点 + 云端自动构建 | ← **你在这里** |
| 1. 数据 | 接在线影视接口（苹果CMS V10 标准格式）+ 筛选分类 | |
| 2. 详情 | 线路/剧集解析、详情页 | |
| 3. 播放 | Media3 播放器、换线路、播放进度记忆 | |
| 4. 打磨 | 搜索、设置页、动画精修、错误兜底 | |

**阶段 0 能看到的**：一屏首页（左边海报墙 + 右边常驻详情），分类筛选条，遥控器方向键移动时的焦点放大动效，OK 键提示。数据是假数据（24 部虚构电影），只为验证界面和交互。

---

## 电视适配的关键决定（都是踩过坑的）

| 决定 | 原因 |
|---|---|
| `minSdk 23` / `targetSdk 28` | 你的电视能装 sdk23 的包；targetSdk 故意压低，避免高版本触发系统限制 |
| 只出 `armeabi-v7a`（32 位） | 电视不支持 arm64，64 位包装上会崩 |
| 纯 Kotlin，零原生库 | 没有 `.so` 就没有 ABI 兼容问题，CI 里有自动检查 |
| `usesCleartextTraffic=true` | 在线接口和视频源大量是 `http://`，不开会白屏 |
| Leanback + 普通双入口 | 有些 TV 桌面只认其中一种，双保险 |
| `android:required="false"`（leanback feature） | 万一电视不认这个 feature，required=true 会让图标从桌面消失 |
| 启动背景纯黑 | 避免冷启动闪白屏（电视上特别廉价） |
| 界面全部按屏宽比例缩放 | 不同电视 dp 画布从 960x540 到 1920x1080 都有，写死 dp 必翻车 |

---

## 出包流程（全自动）

```
改代码 → 推到 GitHub main 分支 → GitHub Actions 自动编译+签名 → 下载 APK → U盘装电视
```

1. 打开仓库的 **Actions** 页 → 最新一次 `Build TV APK` → 等 3~5 分钟
2. 构建完成后，在该次运行的页面底部 **Artifacts** 下载 `cinema-tv-apk`
3. 解压得到 `app-release.apk`，拷进 U 盘，插到电视上安装

> 首次安装如果被系统安全检测拦下：换个 U 盘安装方式重试，或者改天我教你用 ADB（一次学会，以后 `adb install -r` 秒装）。

## 签名说明

- 签名文件：`keystore/release.jks`（PKCS12，别名 `cinema`）
- **已随仓库提交**，原因是：这样每次云端构建都用同一把钥匙，电视上的应用可以直接覆盖升级，不用卸载重装
- ⚠️ 前提是**仓库保持私有**。如果哪天要公开这个仓库，先把 `keystore/` 目录删掉并重新生成钥匙

---

## 自动检查（每次构建都会跑）

`scripts/check-apk.sh`：
1. APK 里不能有 `arm64-v8a` / `x86` 原生库（有几个就崩几个）
2. 必须开启 `usesCleartextTraffic`
3. 体积超过 30MB 会警告

---

## 目录结构

```
app/src/main/kotlin/com/ichichuang/cinema/
├── MainActivity.kt          # 入口
└── ui/
    ├── Theme.kt             # 色板 + 字号缩放（电视适配核心）
    ├── HomeScreen.kt        # 首页：筛选条 + 海报墙 + 常驻详情
    └── FakeData.kt          # 阶段0 假数据（阶段1 删除）
scripts/check-apk.sh         # 构建后的红线检查
.github/workflows/build.yml  # 云端构建流水线
keystore/release.jks         # 签名钥匙（私有仓库）
```

## 本地构建（可选，当前不需要）

只有装了 Android Studio / JDK17 + Android SDK 才能本地跑 `./gradlew assembleRelease`。
目前是**全云端构建**，本地电脑零安装。
