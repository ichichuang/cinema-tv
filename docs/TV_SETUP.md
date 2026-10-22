# 电视与 FongMi 运维

cinema-tv 是独立点播应用；FongMi 用于独立中文直播，Aerial 只是部分设备已有的桌面。二者都不是 cinema-tv 的运行依赖。以下设备操作只在用户明确授权安装／配置、提供 ADB 地址后执行；文档整理和技能创建不授权连接电视。

## 连接与预检

复用 `command -v adb`、现有 SDK 或应用附带 ADB。本机曾使用 HBuilderX 的 `plugins/launcher-tools/tools/adbs/adb`，不是唯一位置，不要求安装 HBuilderX。不得扫描网段、猜测设备或端口。

由实际输入设定 `ADB`、`TV_SERIAL`（IP:端口）、`ADB_SERVER_PORT`。定义定向入口，设备操作全部使用它；主机级只读设备列表除外：

```sh
tv_adb() { "$ADB" -H 127.0.0.1 -P "$ADB_SERVER_PORT" -s "$TV_SERIAL" "$@"; }
tv_adb connect "$TV_SERIAL"
tv_adb get-state
tv_adb shell am get-current-user
tv_adb shell getprop ro.product.model
tv_adb shell getprop ro.product.name
tv_adb shell getprop ro.build.version.release
tv_adb shell getprop ro.build.version.sdk
tv_adb shell getprop ro.build.version.incremental
tv_adb shell getprop ro.product.cpu.abilist
tv_adb shell df -k /data /sdcard
tv_adb shell pm path com.fongmi.android.tv
tv_adb shell dumpsys package com.fongmi.android.tv
```

每条命令由执行器设超时，普通读操作约 10–20 秒，传输和安装按大小合理放宽。`unauthorized` 时停止，提示 Owner 在电视接受「允许 USB 调试」；离线、拒绝和不可路由按真实错误报告。配对由 Owner 完成，不猜配对码。

保留其他 ADB 会话，禁止全局 disconnect、kill-server 和进程清场。历史共享 5037 服务曾错误报告不可路由，独立 5038 成功；只有证据支持且不打断其他会话时才创建本任务独立服务，结束仅关闭自己的服务。

记录当前安装、订阅 URI、文件和依赖、可用空间、相关权限及保护值。HOME、无障碍、屏保、超时和网络只读比较；不能写回旧默认覆盖 Owner 后来的选择。

## 安装或复用

合适且工作的 FongMi 直接复用，不例行更新、降级、清数据或重装。需要安装时从 [FongMi/Release](https://github.com/FongMi/Release/releases) 选择正式电视 APK，匹配实际 SDK 和 ABI；源码为 [FongMi/TV](https://github.com/FongMi/TV)。ARM32 要选真正含 armeabi-v7a 的 leanback 资产。

使用 aapt2、apksigner 和 SHA-256 核对 `com.fongmi.android.tv`、版本、最低 SDK、原生库、电视入口、有效签名和官方 digest（有提供时）。升级需检查与已装包的签名连续性。没有官方摘要时记录限制，不能把自行计算的哈希称为官方认证；缺必要工具时报告缺口，不能仅凭下载完成安装。

普通电视用定向正常 ADB 安装，新装不加覆盖／降级／自动授权参数。已有应用更新须由当前请求确实需要。安装后独立查询包、版本、用户状态，必要时读回 pm path 指向的 APK 计算摘要；解析真实电视启动入口并验证启动。

## TCL 有条件安装特例

历史设备为 `tcl_mt5879_cn / tcl_mt5879_cn_db`、Android 11 / SDK 30、ARM32。该固件普通 ADB 安装实际返回 `INSTALL_FAILED_VERIFICATION_FAILURE`；FongMi 5.7.8 经多屏互动、临时 loopback HTTP 和 ADB reverse 安装成功，由 Owner 在电视同意。这些是匹配历史证据，不是未来版本或设备默认值。

参考 [TCL 说明](https://github.com/pengchujin/aerial-tv/blob/v1.22/docs/devices/tcl-mt5879-cn.md)、[install.sh](https://github.com/pengchujin/aerial-tv/blob/v1.22/tools/install.sh)、[T-Cast helper](https://github.com/pengchujin/aerial-tv/blob/v1.22/tools/tcast-push.py)。**不要执行完整 Aerial install.sh**，它还会改权限、守护、HOME 和屏保。仅复用审阅后的传输机制，保留系统校验与用户确认。

1. 匹配已知固件限制，或一次正常安装明确返回该错误时，才走此路线；已知匹配设备不重复失败安装。确认 `com.tcl.MultiScreenInteraction_TV` 存在且启用，仅检查指定电视 TCP 6553。
2. 从固定提交取得 helper 并审阅，记录提交和摘要。历史提交 `80ea58b90b5b7429609c284149845e26bd0eeaa5`，helper SHA-256 `5a84e9e08de0f10013ec499f778d9745d031c0d23369f256fe504dfe63e881bf`；新下载需重新核对，不盲执行远程脚本。
3. 本任务临时目录仅放已验证 APK，HTTP 仅绑定 Mac 127.0.0.1，记录自己持有的进程／线程与端口。先记 reverse 列表，再用 `reverse --no-rebind tcp:电视临时端口 tcp:Mac临时端口`，不覆盖映射。
4. helper 参数为本次电视 IP、`http://127.0.0.1:电视临时端口/FongMi.apk`、实际包名、显示名、实际 versionCode 和有限超时（如 120 秒）。Owner 亲自同意，自动化不得代点「同意」。
5. helper 成功后仍需查询实际包、版本、摘要与启动。下载失败最多一次有依据的修正，不重复安装循环。
6. finally／trap 清理本次那一条 reverse，正常关闭自己创建的 HTTP 服务并确认无监听。禁止 remove-all、pkill、killall、kill -9。

历史 `reverse tcp:0` 的随机端口曾导致原 ADB 服务自行崩溃、下载失败，明确空闲端口配合 no-rebind 后成功。因此该环境不默认使用 tcp:0。多屏不可用或 Owner 拒绝时停止，说明可由 Owner 使用受支持的 U 盘／系统安装入口；不装额外商店、不关闭校验。

## 直播配置、部署与恢复

先读取当前订阅与安装版本对应的 [官方格式](https://fongmi.github.io/TV/config/)。历史最终使用 `/sdcard/TV/Family/live-config.json` 引用 `channels-family.json`（Group 数组），早期使用 M3U；不能混淆格式或误改点播配置。频道整理规则集中在 [直播配置参考](../.agents/skills/fongmi-tv-setup/references/live-config.md)。

备份当前原文件、相对依赖及 URI／名称。原生导出仅为额外保险：历史备份写入 `TV/backup/日期.tv`，同日可能覆盖、只保留有限份，需立即另存。原生恢复会清空数据库表再导入，不能当无损合并；通常只恢复本任务改过的直播文件和 URI，保留后来的历史与收藏。

优先沿用电视持久目录；复制后读回比较摘要，确认所有依赖存在才切换订阅。新设备可用其他共享目录，不强制历史路径。5.7.8 已验证的订阅 URI 是共享根内 `file://TV/Family/live-config.json`，相对依赖为 `./channels-family.json`；台标另曾使用 `file:///sdcard/TV/Family/logos/...`。这是不同用途，不能把 URI 写法当通用别名；新版本核对实际解析器和文件权限。

通过「设置 → 直播」原生对话框确认／刷新。局域网管理只用应用实际显示的地址和端口；历史实现从 9978–9998 选可用端口，不能扫描或硬认 9978。已验证 `/action` 支持 URL 编码的 `do=setting`、`text=配置URI`、`name=订阅名`，只填写**当前打开的配置对话框**，随后仍需电视确认。HTTP 200 不代表订阅刷新，不写私有数据库。

失败时保留工作正常的旧订阅。恢复只依据本次实际备份和变更清单，不恢复历史网络／系统默认；仅为修复本任务造成的故障执行必要的最小恢复。

## 有限验收与保护

通常抽查央视、卫视、地方台各一台，总共约 3–5 台，每台 15–30 秒；没有长测和全表换台循环。检查启动、订阅、分组、顺序、代表台标、返回／重开保持。合并一次 Owner 对画面、人声和实体遥控器的确认；用户要求不测时停止测试。

默认无人值守主动工作预算约 10 分钟，预留清理时间。Owner 等待单独记录，重叠工作不能虚减；到时停止新操作、清理临时设施、报告部分结果。

最新历史设备已使用 Aerial Home Guard；保持 Aerial、TCL HOME、无障碍、屏保、显示、声音、网络和其他应用原样。正式 HOME 解析与守护实际接管可以不同，不授权本技能修复守护。禁止把安装 Aerial、授权系统权限或接管桌面加入 FongMi 工作流。
