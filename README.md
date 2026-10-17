# 极简影院 · cinema-tv

给 **FFALCON 65S78A（雷鸟鹏7 2026款 / 灵控系统）** 定制的 Android TV 电影应用。
目标只有一个：**用遥控器舒服地挑电影、看电影**，没有任何多余功能。

---

## 版本路线

| 阶段 | 内容 | 状态 |
|---|---|---|
| **0. 骨架** | 首页界面 + 遥控器焦点 + 云端自动构建 | ✅ 完成 |
| **0.5 地基** | 设计系统 / 焦点系统 / 四层架构 / 导航 / 依赖注入 | ✅ 完成 |
| **1. 数据** | 接在线影视接口（苹果CMS V10 标准格式）+ 真实海报 | ✅ 完成 |
| **2. 播放** | Media3 播放器、线路/剧集解析、换线路、进度记忆 | ✅ 完成 |
| **2.5 筛选** | 分类/语言/地区/年份筛选（**默认国语**）+ 交错入场动画 + 空结果兜底 | ✅ 完成 |
| **2.6 在线更新** | 应用内检查新版本 + 下载 + 校验签名 + 一键安装（+ 设置页可换更新地址） | ✅ 完成 |
| **2.7 操作回显** | 焦点/滚动/筛选/进度全场景回显 + 进程被杀后回到上次浏览位置 + 换线路不清零进度 | ✅ 完成 |
| **2.8 搜索 + 体验收口** | 片名搜索（系统输入法/拼音/语音）+ 搜索历史 + 筛选条滚动让位 + 全页面溢出体检 | ✅ 完成 |
| 3. 打磨 | 设置项落地、动画精修、错误兜底 | ← **你在这里**（本轮：全量手感 / 动效体检） |

---

## 架构：四层，依赖单向向下

```
feature/   界面层：首页 / 播放器 / 设置     —— 只认 domain，不认任何实现
domain/    领域层：Movie/MovieDetail 模型 + MovieRepository 接口   —— 纯 Kotlin，零依赖
data/      数据层：苹果CMS 适配器 + 设置存储 + http 层   —— 实现 domain 的接口
core/      地基：设计系统 / 焦点系统 / 通用组件
```

**关键约定（这些是这个工程能长期不乱的原因）**

| 约定 | 为什么 |
|---|---|
| UI 只认 `domain` 的 `Movie` / `MovieRepository` | 换片源格式（苹果CMS → 其它）只动 `data/` + DI 两处，UI 一行不用改（阶段 1 实测：首页组件零改动） |
| 所有动画时长/曲线只从 `core/design/Motion.kt` 取 | 页面手感才会一致；"高级感"来自动效时机，不是库 |
| 所有可点组件用 `Modifier.tvFocus(shape)` | 一行拿到统一焦点动效（放大 + 金边 + 回弹） |
| 所有尺寸用 `rememberTvMetrics().dp()/.sp()` | 不同电视 dp 画布从 960x540 到 1920x1080 都有，按屏宽比例缩放才不翻车 |
| 颜色只在 `core/design/Color.kt` 定义 | 防止每个页面各写各的灰 |

### 三大底层能力

1. **焦点系统** `core/focus/TvFocus.kt`
   `Modifier.tvFocus(shape, onFocusChanged)`：放大 1.06 + 暖金描边 + 弹簧回弹。
   边框用 `drawWithContent` 在内容之后绘制 —— 这点很关键，否则边框会被海报背景盖住（很多自绘焦点组件踩这个坑）。

2. **动效规范** `core/design/Motion.kt`
   140 / 200 / 280ms 三档时长 + Apple 式贝塞尔缓动 + 焦点弹簧参数。
   页面里**不许**出现裸的 `tween(180)`。

3. **骨架屏** `PosterSkeleton`（首页内）
   加载时是"微光占位"，不是白屏、不是转圈 —— 电视上尤其重要。

---

## 片源接入（阶段 1）：怎么用

**应用不内置任何片源地址**（这是红线）。第一次装好后首页会显示一句引导，去「设置」填自己的接口地址即可。

- 地址格式：`http://你的站点/api.php/provide/vod/`（苹果CMS V10 标准接口；不写 `http://` 会自动补）
- 想先看界面长什么样：地址栏里填 `demo`，用内置演示数据（不联网）
- 保存后首页自动重新加载，**不用重启应用**（地址变化是数据流的一部分）

适配器处理掉的现实问题（都写在 `data/remote/`）：

| 问题 | 处理 |
|---|---|
| 同一字段有的站返回数字、有的返回字符串 | 全部按 `JsonElement` 读再自己转（`asText/asInt/asDouble`） |
| 老站点默认返回 XML | 每次请求显式带 `at=json` |
| GBK 编码（还常谎报 utf-8） | 按字节读 → 严格 UTF-8 解不动就退 GB18030 |
| 防盗链校验 UA / Referer | 统一伪装成浏览器 UA + 带站点根 Referer |
| 图片是相对路径 | 按接口域名补成绝对地址 |
| 简介是 HTML | 剥标签后再显示 |
| 播放地址 `线路$$$线路` + `集名$地址#` | `parsePlaySources()` 解析成 `PlayLine/Episode`（阶段 2 直接用） |

---

## 播放（阶段 2）：怎么设计的

引擎是 **Media3 ExoPlayer 1.11.1**（纯 Java/Kotlin，解码走系统 MediaCodec，**不引入任何原生库** —— 对 32 位电视零 ABI 风险）。
`m3u8/HLS` 在 media3 里是独立模块（`media3-exoplayer-hls`），国内片源绝大多数是 m3u8，所以显式引了。

**遥控器键位**（全电视通用习惯，不用学）：

| 键 | 作用 |
|---|---|
| OK / 空格 | 播放 / 暂停 |
| ← / → | 快退 / 快进 10 秒 |
| ↑ / ↓ | 升起「选集与线路」面板 |
| 返回 | 收起面板；面板已收起则退出播放（自动存进度） |

**几个刻意的决定**：

| 决定 | 为什么 |
|---|---|
| 平时画面上**什么都不放** | 沉浸感的关键；一按键才浮出控件，4 秒后自己消失 |
| 进度条是一根极细的金线，**不做滑块** | 用遥控器拖进度本来就不好用，±10 秒更顺手 |
| 选集是底部升起的面板，**不跳新页面** | 选完立刻继续看，不用"进去再退出来" |
| **ExoPlayer 由 ViewModel 持有** | 屏幕重组/进后台不能重建播放器，否则闪黑 + 进度丢失 |
| 播放时 `FLAG_KEEP_SCREEN_ON` | 电视上最烦的就是看一半黑屏 |
| 线路失败**自动换下一条** | 国内片源最常见的问题就是某条线路挂掉；与其让用户看英文报错，不如自己换一条并用人话说明 |
| 换线路时尽量停在同一集 | 各线路集名通常一致，按名字匹配，不用重新找"看到第几集了" |
| 错误提示人话化 | 把 ExoPlayer 的错误码翻译成"连不上/线路挂了/这台电视解不了 H.265" |
| 进度存 DataStore，**不用 Room** | 就几十条键值对，为它引注解处理器（KSP）会让构建变慢、多一个版本风险 |
| 全片只剩不到 1 分钟就当成看完 | 否则按钮永远显示"继续观看 00:12" |

**进度记忆**：暂停 / 切集 / 切线路 / 退出 / 每 10 秒各存一次（最坏只丢 10 秒）。
回到首页，如果这部片看过，播放按钮直接变成 **「继续观看 12:34」**——少一次思考。

---

## 筛选（阶段 2.5）：默认国语，但永远不给你空白

筛选区分两行（不再多占地方的取舍：品牌 + 分类一行，三个筛选维度 + 摘要一行）：

| 分组 | 选项 | 默认 |
|---|---|---|
| 分类 | 全部 / 电影 / 电视剧 / 综艺 / 动漫…（**由接口的 `class` 树生成**，各站不同，客户端不硬编码） | 全部 |
| 语言 | 全部 / **国语** / 粤语 / 英语 / 日语 / 韩语 | **国语** |
| 地区 | 全部 / 大陆 / 香港 / 台湾 / 美国 / 韩国 / 日本 / 英国 / 法国 / 泰国 | 全部 |
| 年份 | 全部 / 近几年（只列接口**真能按年筛**的年份，"更早"表达不了，不做假选项） | 全部 |

右侧固定摘要实时显示：`国语 · 大陆 · 共 128 部`（接口不返回总数时就不显示数字，不编）。

**"默认国语"的三层降级**（否则遇到没标语言的老站会给你一片空白）：

1. 带 `lang/area/year` 问接口（服务器端筛，最省流量）
2. 返回 0 条 → 去掉这些参数重问，再**本地**按 `vod_lang` / `vod_area` 字段筛
   （`国语/普通话/中文/汉语/华语` 都算国语；`大陆/内地/中国` 都算大陆）
3. 仍然 0 条 → 放开筛选显示全部，并在筛选区下方用一行字说明「本站片源缺少『国语』的标注，已自动放开筛选」

放宽状态会**沿用到翻页**（否则第 2 页会突然变空）；筛选一变就重置判断。

**筛选记忆**：用户改过的筛选会存进 DataStore，下次开机还是他选的（第一次运行才是默认国语）。
一键"重置筛选"回到默认（全部分类 + 国语）。

**动画**（全部从 `Motion.kt` 取值，保证全应用一个手感）：
- 选中药丸弹簧填充（金底 + Ink 字），焦点药丸放大 + 白描边回弹
- 换筛选时海报**交错入场**（每张延 30ms，只给前 12 张做 —— 32 位电视不值当为第 20 张算动画）
- 换筛选自动回到列表顶部（否则停在上次滚动位置，看起来像"没反应"）
- 筛选条**焦点自动跟随滚动**（遥控器一直往右按也不会把焦点按出屏幕）
- 骨架屏数量与真实结果一致（加载不"跳格"）

---

## 操作回显（阶段 2.7）：来回切页，画面必须原样回来

电视上的"体验差"很少是功能缺，多半是**回来就变样**：去播放器转一圈回来，
列表跳回顶部、右侧详情换成别的片、焦点跑到筛选条上 —— 用户会觉得"我刚才翻的全没了"。
所以这一版专门做状态回显，分三层存，各管各的场景：

| 状态 | 存在哪 | 活多久 | 覆盖什么 |
|---|---|---|---|
| 影片列表、当前选中影片、详情缓存 | ViewModel | 进程活着 | 首页 ↔ 播放器 / 设置 往返 |
| 网格滚动位置、筛选条横向位置、"已入场"标记 | `rememberSaveable` | Activity 活着 | 导航往返、后台切换、界面被系统回收 |
| 上次浏览到哪部片、筛选条件、播放进度 | DataStore | 永久 | **进程被系统杀掉后重启**（电视上非常常见） |

四个关键设计：

1. **焦点回位靠一次性信号**（`HomeUiState.focusTarget`），不直接用 `selected` 驱动焦点。
   用 `selected` 的话，用户每按一次右键都会被"重新聚焦一次"，会和按键打架。
2. **分清什么时候该抢焦点**：首次加载、返回首页、换片源 → 抢；
   用户正在筛选条上按 → **绝不抢**（`reload(restoreFocus = false)`）。
3. **滚动位置不跳**：回来时若目标海报本来就在屏幕上就**不滚动**；
   只有它不在屏幕上（首次加载 / 翻了很多页 / 进程重启）才用 `scrollToItem` 把它带进来。
4. **入场动画不重播**：海报交错入场只跟"换筛选"（generation）绑定，
   且用 `rememberSaveable` 记住"哪些海报已经入场过"—— 否则每次从播放器回来整墙海报都会再闪一遍。

顺带修掉一个很伤的老问题：**换线路不再清零进度**。
以前手动换线路和线路自动容错切换都是 `playEpisode(ep, 0L)`，等于把"继续观看"直接抹掉；
现在把当前进度带过去，"像没换过一样"接着看。

---

## 搜索（阶段 2.8）：电视上打字最贵，所以每一步都要省

搜索本身是数据层早就备好的能力（苹果CMS `wd` 参数），缺的一直是**界面**。
难点不在界面，在电视的输入：

1. **不自绘键盘，用系统输入法**。要搜的是中文片名，自绘 26 个字母按不出汉字；
   系统输入法带拼音、联想、语音 —— 这是电视上唯一正确的选择。
   输入框设了 `ImeAction.Search`，输入法键盘上的"搜索"键一按就出结果。
2. **不边打边搜**。拼音拼字过程中全是半成品（"liulang" / "流浪"），逐字发请求既没用又费流量。
   只有按下"搜索"才真的搜。
3. **搜索历史必须留**（DataStore，最多 8 条）。遥控器按字母是最贵的操作，
   下次想看同一部片，在「最近」里按两下就能重搜 —— 这是电视搜索里回报最高的一个功能。
4. **焦点有讲究**：有结果时焦点落在结果墙（从播放器退回来落回原来那部）；
   只有"还没有结果"时才把焦点给输入框 —— 否则每次回页面系统输入法都会自己弹出来。
5. **结果墙复用首页那一套**：同样的海报卡、同样的右侧常驻详情、"继续观看 12:34"照旧。

### 顺手做的体验收口（体检出来的硬伤）

| 问题 | 后果 | 修法 |
|---|---|---|
| 详情面板内容按屏宽缩放后必然超高 | 16:9 屏上"播放"按钮被挤出屏幕 | 收紧尺寸 + 面板可滚动（焦点会带着按钮滚进来）+ 换片回到顶部 |
| 筛选条常驻在网格外面 | 海报墙只剩一行可见 | 筛选条改成网格第一项：往下翻自动滚走，把整屏让给海报 |
| 设置页内容超高 | 底部版本号被裁掉；三个"开关"其实是假的 | 收紧 + 可滚动；假开关全删，换成**真能用的**「清空图片缓存」 |
| 翻页失败/翻页中毫无反馈 | 用户对着"没反应的列表"发呆 | 底部三态：正在加载… / 这一页没加载上（会重试）/ 已经到底啦 |
| 播放中按 Home 键 | 画面没了、声音还在响 | 监听 `ON_STOP` 自动暂停并落盘进度 |

---

## 动效（阶段 3 开场）：手感不是"加动画"，是让每个状态变化都有来处

全部动效只从一个地方取值（`core/design/Motion.kt`）——
散落在页面里的 `tween(180)` 会让每个页面手感都不一样，这个文件里每条曲线都写清了"为什么是它"。

| 场景 | 动效 | 为什么这么设计 |
|---|---|---|
| 焦点移动 | 弹簧放大 1.06 + 金边 + **向内一圈软光晕** | 只画细金线的话，坐在 1.5 米外基本看不出来；深色底上"亮起来"靠的是那圈光晕 |
| 按下 OK | 弹簧回缩到 0.97（刚度高、阻尼低） | 遥控器按下到动作生效有一两百毫秒。**没有即时反馈，用户会以为没按上，然后连按两下** |
| 海报墙入场 | 每张延 30ms，只给前 12 张算 | 32 位电视不值当为第 20 张算动画；上限之外的直接显示 |
| 换片（右侧详情） | 旧片快速淡出、新片淡入 + 上浮 8% | 遥控器扫过一排海报时，右侧"硬切"会让整个扫视过程很躁；交叉过渡才有"换镜头"的从容 |
| 按钮文案变化 | `animateContentSize` | 「播放」→「继续观看 12:34」宽度会变，不处理就"啪"地抖一下 |
| 播放进度条 | 线性插值 460ms，**明确不能用缓动** | 位置每 500ms 上报一次，缓动会看出"一抽一抽"；线性才像真的在走 |
| 播放器控制层 | 顶部从上落、底部从下升 + 淡入淡出 | 控制层"凭空出现"很廉价；带方向感的位移才对得上"划拉出来"的直觉 |
| 缓冲 / 加载 | 淡入淡出；「正在加载…」带 900ms 呼吸明灭 | 静态文字像是在骂人，呼吸感才像"正在做事" |
| 更新浮层 | 缩放进（0.94 → 1），出场轻微缩回 | 入场出场对称，才不显得"被抽走" |
| 水波纹涟漪 | **全部关掉**（`indication = null`） | 那是手机的语言。电视上改用缩放 + 光晕做按压反馈 |

**所有可点组件共用一个焦点系统**（`Modifier.tvFocus`）：想拿到"放大 + 光晕 + 按压回弹"，
把 `clickable` 用的**同一个** `interactionSource` 传进去即可；忘了传也能跑，只是没有按压反馈。

---

## 在线更新（阶段 2.6）：能"应用内升级"，但不能"静默升级"

先说清楚**能力边界**，免得期望错位：

| 环节 | 能不能做到 | 说明 |
|---|---|---|
| 检查新版本 | ✅ 全自动 | 启动时悄悄问一次；查到只在首页右上角亮一个金色小金标，不弹窗 |
| 下载新包 | ✅ 全自动 | 带进度条；主地址失败了自动换镜像地址重试 |
| 安装 | ⚠️ 差一下遥控器 | 系统安装界面会弹出来，按一次「安装」。**这不是没做，是安卓不允许** —— 侧载应用没有系统权限，非 root 电视上做不到全静默 |

**为什么要做签名校验**（`GitHubUpdater.verify()`）：
更新地址是用户可改的，也就意味着它可以被换成一个恶意地址。所以下载完必须过三道关，
任何一道不过就直接删文件报错：**包名一致 + 版本号一致 + 签名指纹和当前应用一致**。
少了这三道，所谓"在线更新"就是个自己给自己装木马的通道。

**更新地址是设置项，不是写死的**（和片源地址同一个原则）：

```
默认：https://github.com/ichichuang/cinema-tv-releases/releases/latest/download/version.json
```

电视连不上 GitHub 时，在设置页把它换成镜像地址即可（例如 `https://gh-proxy.com/<原地址>`），
换完立刻能继续收更新，**不用重装应用**。

**清单格式**（`update/version.json`，发布脚本生成）：

```json
{
  "versionCode": 6,
  "versionName": "0.6.0",
  "apkUrl": "https://github.com/<用户>/<发布仓库>/releases/download/v0.6.0/cinema-tv-0.6.0.apk",
  "sizeBytes": 14000000,
  "notes": "1. ...\n2. ...",
  "mirrorUrl": "https://gh-proxy.com/<上面的 apkUrl>"
}
```

字段名宽容：`versionCode / version_code / code`、`apkUrl / apk_url / url / download_url` 都认。

**发布流程**（我这边的动作，一条命令）：

```powershell
.\scripts\publish-update.ps1 -VersionName 0.6.0 -VersionCode 6
```

它做三件事：找 APK → 生成 `update/version.json`（更新说明读 `update/notes.txt`）→
用 GitHub API 建 release 并把 **APK + version.json** 两个文件作为附件上传。

> ⚠️ **为什么必须走 release 附件，而不是仓库里的文件**：
> 仓库文件的下载域名是 `raw.githubusercontent.com` —— 国内连不上（实测超时）。
> 而 release 附件的下载域名是 `objects.githubusercontent.com` —— 实测可达。
> 这是"挂哪个域名"决定成败的地方，不是风格问题。
>
> 另外：发布仓库必须是 **public**（私有仓库下载要带 token，把 token 打进应用里等于泄露），
> 主仓库继续私有（里面有签名钥匙）。这也是为什么另开一个 `cinema-tv-releases` 仓库。

---

## 电视适配的关键决定（都是踩过坑的）

| 决定 | 原因 |
|---|---|
| `minSdk 24` / `targetSdk 28` | 电视能装 sdk23 的包 → 系统 API ≥ 23；navigation 2.10.2 硬性要求 minSdk ≥ 24。targetSdk 故意压低，避免高版本触发系统限制 |
| `compileSdk 37` | 2026 年 AndroidX 在 AAR 元数据里强制要求 ≥ 37（只影响可用 API，不影响装机） |
| 只出 `armeabi-v7a`（32 位） | 电视不支持 arm64，64 位包装上会崩 |
| 原生库只剩 2 个极小的（都带 v7a） | Compose 的 `libandroidx.graphics.path.so`（7KB）+ DataStore 的 `libdatastore_shared_counter.so`；`abiFilters` 保证只打 v7a，构建后有脚本自动检查 |
| `usesCleartextTraffic=true` | 在线接口和视频源大量是 `http://`，不开会白屏 |
| Leanback + 普通双入口 | 有些 TV 桌面只认其中一种，双保险 |
| `android:required="false"`（leanback feature） | 万一电视不认这个 feature，required=true 会让图标从桌面消失 |
| 启动主题纯黑 | 避免冷启动闪白屏 |
| 详情常驻右侧（不做独立详情页） | 布局不跳、不用"进详情再退出来"，按 OK 直接播 |
| 依赖注入用 Koin 而不是 Hilt | 没有注解处理器，构建更快、报错更好懂 |

---

## 出包流程

### 方式 A：本地一键构建（推荐，快）

本机已装好**便携工具链**（不用 Android Studio，不占系统）：

| 组件 | 位置 |
|---|---|
| JDK 17 | `C:\Users\cc\Tools\jdk17` |
| Android SDK | `C:\Users\cc\Tools\android-sdk` |
| Gradle 9.8.1 | `C:\Users\cc\Tools\gradle-9.8.1` |

```powershell
cd C:\Users\cc\Documents\默认项目\cinema-tv
.\scripts\build-local.ps1          # 编译 release APK
.\scripts\check-apk.ps1            # 红线检查（ABI / minSdk / Leanback 入口 / 体积）
.\scripts\publish-update.ps1 -VersionName 0.6.0 -VersionCode 6   # 发在线更新（可选）
```

编完 APK 路径会打印在最后一行（`app\build\outputs\apk\release\app-release.apk`），
拷进 U 盘插电视安装即可。

> **网络说明**（2026-10 实测，纠正过一版错误结论）：
> - `github.com` / `api.github.com` **能连**，但 Windows 的 curl 会因连不上证书吊销服务器
>   误报 `CRYPT_E_REVOCATION_OFFLINE` —— 加 `--ssl-no-revoke` 就通了。
>   安卓的 OkHttp 不做吊销检查，所以电视那边不受影响。
> - `raw.githubusercontent.com` **真的连不上**（这个是真被墙）→ 所以更新包必须走 release 附件。
> - `objects.githubusercontent.com`（release 附件下载域名）**能连** ✓
> - `gh-proxy.com`（GitHub 加速镜像）**能连** ✓，留作镜像方案
> - Gradle 官方分发地址会 307 跳到 github.com，所以本机用的是从镜像下好的独立 Gradle 分发，
>   仓库里的 `gradle-wrapper.properties` 保持官方地址不变（云端构建不受影响）。

### 方式 B：云端构建（备份 / 换电脑也能出包）

```
推到 GitHub main → Actions 自动编译+签名 → 下载 artifact → U盘装电视
```

> 首次安装如果被系统安全检测拦下：换个 U 盘安装方式重试，或改用 ADB（`adb install -r` 秒装）。

## 签名说明

- 签名文件：`keystore/release.jks`（PKCS12，别名 `cinema`）
- **已随仓库提交**：这样每次构建都用同一把钥匙，电视上的应用可以直接覆盖升级，不用卸载重装
- ⚠️ 前提是**仓库保持私有**。如果哪天要公开，先把 `keystore/` 删掉并重新生成钥匙

---

## 自动检查（每次云端构建都会跑）

`scripts/check-apk.sh`：
1. APK 里不能有 `arm64-v8a` / `x86` 原生库（有几个就崩几个）
2. 必须开启 `usesCleartextTraffic`
3. 体积超过 30MB 会警告

---

## 目录结构

```
app/src/main/kotlin/com/ichichuang/cinema/
├── MainActivity.kt              # 唯一 Activity：主题 + 导航
├── CinemaApplication.kt         # 依赖注入口
├── core/
│   ├── design/                  # 色板 / 动效规范 / 电视尺寸适配 / 主题 / 时间格式化
│   ├── focus/TvFocus.kt         # ⭐ 统一焦点系统
│   └── ui/                      # 通用组件：海报卡 / 海报图(Coil) / 详情面板 / 播放按钮 / 筛选药丸
├── domain/
│   ├── model/Movie.kt           # 干净的影片模型（不含任何接口字段）
│   ├── model/MovieDetail.kt     # 详情：简介 + 播放线路/剧集
│   ├── model/MovieFilter.kt     # 筛选条件 + 分页结果（含"被放宽"标记）
│   ├── model/UpdateInfo.kt      # 新版本信息（版本号/下载地址/更新说明/镜像地址）
│   ├── repository/MovieRepository.kt   # + Categories/Options（分类与筛选项）
│   ├── repository/FilterStore.kt       # 筛选记忆接口（实现放 data）
│   └── repository/Updater.kt           # 在线更新接口（实现放 data）
├── data/
│   ├── remote/MaccmsApi.kt      # 苹果CMS V10 适配器（字段解析 + lang/area/year 参数）
│   ├── remote/Http.kt           # OkHttp + UA/防盗链/GBK 处理
│   ├── update/GitHubUpdater.kt  # 读 version.json + 下载 APK + 包名/版本/签名三道校验
│   ├── settings/CinemaSettings.kt   # DataStore：接口地址 + 更新地址
│   ├── settings/PlaybackProgress.kt # DataStore：观看进度（看到哪了）
│   ├── settings/DataStoreFilterStore.kt # DataStore：筛选记忆（默认国语）
│   ├── settings/DataStoreBrowseStore.kt # DataStore：上次浏览到哪部片（杀进程重开也回得来）
│   ├── settings/DataStoreSearchHistory.kt # DataStore：搜索历史（电视上打字最贵）
│   ├── settings/ImageCache.kt       # 真能清掉的海报缓存（内存 + 磁盘）
│   ├── MaccmsMovieRepository.kt # 接口 → 领域模型 + **三层筛选降级**
│   ├── SwitchableMovieRepository.kt # 按设置路由：真实片源 / demo
│   └── demo/                    # 内置演示数据（填 demo 时使用，筛选行为跟真实实现一致）
├── feature/
│   ├── home/                    # 首页（筛选区 + 海报墙 + 常驻详情 + 新版本小金标）
│   ├── search/                  # 搜索页（系统输入法 + 历史 + 结果墙 + 常驻详情）
│   ├── player/                  # 播放页：PlayerViewModel(ExoPlayer) + PlayerScreen + 选集面板
│   ├── settings/                # 设置页（接口地址 / 更新地址 / 检查更新 / 清缓存）
│   └── update/                  # 更新中枢(UpdateCenter) + 更新浮层(下载进度/安装引导)
├── di/AppModule.kt              # 全应用唯一知道"实现是谁"的地方
└── nav/CinemaNavHost.kt         # 路由 + 转场动效
scripts/build-local.ps1          # 本地一键构建
scripts/check-apk.ps1            # 本地红线检查（Windows）
scripts/check-apk.sh             # 构建后红线检查（CI）
scripts/publish-update.ps1       # 发布在线更新（生成清单 + 上传 release 附件）
update/notes.txt                 # 本次版本的更新说明（发布时写进清单）
update/version.json              # 已发布的清单（发布脚本生成，仓库里留档）
.github/workflows/build.yml      # 云端构建流水线
keystore/release.jks             # 签名钥匙（私有仓库）
```

---

## 踩坑记录（都是真金白银试出来的）

| 现象 | 原因 | 处理 |
|---|---|---|
| 报 `The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0` | AGP 9 内置了 Kotlin 支持（自带 KGP），手动声明 KGP 属于非法 | 删掉 `kotlin.android` 插件；`kotlin.plugin.compose` 版本必须等于 AGP 自带 KGP 版本（AGP 9.4.1 → 2.2.10） |
| 报 `Error: Google Play requires that apps target API level 33 or higher. [ExpiredTargetSdkVersion]`（`lintVitalRelease` 失败） | AGP 9 把 lintVital 挂进 `assembleRelease`，而这条规则只管 Google Play 上架 | `android { lint { disable += "ExpiredTargetSdkVersion" } }`：只关这一条，其它 lint 错误照样卡构建 |
| 报 `Unresolved reference 'drawOutline'` | Compose 1.12 移除了 `androidx.compose.ui.graphics.drawscope.drawOutline` 扩展（已拆字节码确认，`DrawScopeKt` 里只剩 inset/transform） | 用 `DrawScope` 的**成员函数** `drawRoundRect` / `drawPath` 自绘描边（`core/focus/TvFocus.kt`） |
| 报 `requires libraries and applications that depend on it to compile against version 37 or later` | 2026 年的 AndroidX（Compose 1.12.1、navigation 2.10.2、core-ktx 1.19.1）在 AAR 元数据里强制要求消费方 compileSdk ≥ 37 | `compileSdk = 37`（只影响可用 API，不影响装机兼容性） |
| 报 `Your project path contains non-ASCII characters` | 项目目录是 `默认项目`（中文路径），AGP 在 Windows 上默认拒绝构建 | `gradle.properties` 里已开 `android.overridePathCheck=true` |
| `gradlew` 卡在 `Downloading https://services.gradle.org/...` | 该地址 307 跳 `github.com`，而本机 `github.com:443` 不通 | 本机用独立 Gradle 分发 `C:\Users\cc\Tools\gradle-9.8.1`；仓库配置保持官方地址（CI 不受影响） |
| 源码写了却推不上/提交里总是只有几个文件 | `.gitignore` 里写 `kotlin/`（想忽略根目录的临时目录）会**匹配任意层级的 kotlin 目录**，把 `app/src/main/kotlin/` 整个吃掉 | 写成 `.kotlin/`（只匹配根目录）；用 `git check-ignore -v 文件` 一查就知道是谁在忽略 |
| 报 `Class was compiled with an incompatible version of Kotlin` / 2026 年的库读不了 | Coil 3.6.3 等库是用 Kotlin 2.4 编译的，而 AGP 9.4.1 内置的 KGP 是 2.2.10（旧编译器读不了新元数据） | 按 AGP 官方通道把 KGP 抬到 2.4.20：根 `build.gradle.kts` 里 `buildscript { dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20") } }`，同时 `kotlin.plugin.compose` 版本必须改成同一个 2.4.20 |
| Koin 找不到 `androidContext` / `viewModel{}` | Koin 4.2 起 `koin-androidx-compose` 只有 8KB，不再传递依赖 `koin-android` | 两个依赖都显式写进 `build.gradle.kts` |
| Actions 页面打不开、artifact 下不了 | 同上：本机 `github.com:443` 不通（SSH 推送是通的） | 用本地构建出包，云端只做备份 |
| curl 报 `schannel: CRYPT_E_REVOCATION_OFFLINE`（连 github.com / api.github.com 时） | Windows 的 curl 走系统证书链，会去连**证书吊销列表服务器**，那个服务器在这边连不上 → 误报"证书不可用"。**不是墙**（TCP 探测是通的） | 加 `--ssl-no-revoke` 即可。安卓的 OkHttp 不做吊销检查，所以**电视端的更新检查不受这个影响** —— 差点因为这条误判放弃在线更新 |
| 更新包挂在仓库里（`raw` 路径）却下载失败 | `raw.githubusercontent.com` 在国内是真的连不上 | 走 release 附件（`objects.githubusercontent.com`，实测可通）；清单里再留一条 `mirrorUrl` 兜底 |
| 侧载应用想做"静默自动升级" | 安卓不允许非系统应用静默安装（`PackageInstaller` 的 `setRequireUserAction(false)` 对侧载应用无效） | 老实做"应用内下载 + 系统安装界面 + 遥控器确认一次"，并把这一步如实写进界面文案 |
| 从播放器/设置返回首页，列表跳回顶部、右侧详情变成别的片 | 焦点被"自动落到第一张海报"的逻辑抢走（`LaunchedEffect` 在**每次**重新进入组合时都会跑，不只是第一次），Compose 又把列表滚到焦点处 | 焦点改成 ViewModel 发的一次性信号 `focusTarget`（按 movieId 找，不靠索引）；滚动位置恢复后若目标已可见就**不滚动** |
| 从播放器回来，整墙海报又"闪一次"入场动画 | 交错入场标记用的是普通 `remember`，导航往返会重建组合 → 标记清零 → 重新从 alpha 0 淡入 | 标记改成 `rememberSaveable`（自定义 Saver 存"已入场 id 列表"），只跟 generation（换筛选）绑定 |
| 手动换一条播放线路，"继续观看"就消失了 | `switchToLine` 里写死了 `playEpisode(ep, 0L)` —— 换线路顺手把进度清零并落盘 | 换线路时带上当前 `positionMs`（手动换、自动容错换都带），`playEpisode` 里 `resumeMs > 5s` 才真 seek |
| 详情面板的"播放"按钮在电视上看不到、被挤到屏幕外 | `TvMetrics` 按**屏宽**缩放（基准画布恒为 1000×562，因为电视都是 16:9），而详情面板内容按基准算有 700+ 高 —— 必然超出 562 | 两条一起上：① 收紧尺寸（海报 170→140、标题 32→27、简介行高 28→26）让常见情况正好装下；② 整块加 `verticalScroll` 当安全网，焦点会带着按钮滚进来；换片时 `scrollTo(0)` 回顶部 |
| 海报墙只露得出一行，翻片像在爬山 | 筛选条常驻在网格**外面**，占掉 29% 屏高（164/562） | 筛选条改成 `LazyVerticalGrid` 的第一项（`span = maxLineSpan`）：往下翻自动滚走，整屏让给海报；注意网格下标整体后移 1，焦点回位和翻页判定都要减掉它 |
| 设置页最底下的版本号看不到 | 页面内容按基准算 570+ 高，超过 562；`Spacer(weight(1f))` 在固定高度 Column 里只会把底部顶出去 | 收紧间距 + `verticalScroll`；顺手删掉「字幕样式/跳过片头片尾」这类**按了没反应的假开关**，换成真能用的「清空图片缓存」 |
| 播放中按遥控器 Home 键，画面没了、声音还在响 | `DisposableEffect` 的 `onDispose` 在退到后台时**不会**触发（界面没销毁，只是停了） | 单独监听 `ON_STOP` 生命周期事件 → `vm.pauseAndSave()`。注意 `android.app.Activity` 不是 `LifecycleOwner`，要 `as? LifecycleOwner` |
| 按 OK 之后屏幕毫无反应，用户会连按两下 | `Motion.PRESS_SCALE` 定义了却**全项目没人用**：`tvFocus` 只管焦点，不知道按没按下 | `tvFocus` 增加可选参数 `interactionSource`：传进来就用它收 `collectIsPressedAsState`，焦点放大和按压回缩叠在**同一个** `graphicsLayer` 里（嵌套两层会互相打架） |
| 选集、线路按钮上冒出手机端的涟漪水波纹 | `clickable` 不写 `interactionSource`/`indication` 时用的是默认 indication = 涟漪 | 统一 `indication = null` + 把按压反馈交给 `tvFocus`；用 `.clickable(onClick = ...)` 简写就是漏网之鱼，全项目已清零 |
| 换片时右侧详情"跳字"，很躁 | 详情面板内容直接随 `movie` 硬切 | 包一层 `AnimatedContent(targetState = movie.id)`；**注意**：过渡期间新旧两份内容同时渲染，直接用外层的 `movie/detail` 会让两份都是新片（动画看着像没动），要各留一份**按 id 的快照**（顺手加了 64 条上限，32 位电视内存紧） |
| 给 `CircularProgressIndicator` 包 `AnimatedVisibility` 后编译不过（`align` 找不到） | `Modifier.align` 是 `BoxScope` 的，被 `AnimatedVisibility` 隔断后就不在作用域里了 | `align` 挪到 `AnimatedVisibility` 自己的 `modifier` 上（它本身还在 Box 里）；顺便记牢：`slideInVertically` 一个参数时是**位移量**，`{ it / 5 }` 是"五分之一屏" |
| 报 `Unresolved reference 'animateColorAsState'` | 它在 `androidx.compose.animation`，不在 `androidx.compose.animation.core` | 改 import（`animateFloatAsState` 才在 `.core`） |
| 提示条/错误面板消失时，淡出动画"没内容可淡" | 可见性由 `state.hint != null` 驱动，等它变 null 时文案也没了 | 留住最后一句（`lastHint`）；**在 `LaunchedEffect` 里写**而不是组合期直接赋值 —— 组合期写状态是反模式 |
