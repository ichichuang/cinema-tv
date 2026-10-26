# TV 重构进度

Round 01–14 已留存，当前完成限定的 Round 15 原生作品详情（线路/集数/收藏/续播）切片及执行验证；真实缺陷（缺图占位图标被遮盖、动作行窄屏溢出屏外、org.json "null" 字面量伪装图源、两处 settle 竞态）均以像素证据定位并 RED→GREEN 修复，R14 缺锚焦点断言以真实组件直连断言补齐，进程死亡证据以两阶段宿主编排单独执行。普通生产接入与完整 T02/T03–T11/M04 仍未完成。执行状态以本记录及验证报告为准，最初基线 `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`，隔离分支 `codex/contract-foundation`。

## Round 15 执行记录（原生作品详情切片）

- 在七级授权链（既有六探针 + 新增 `cinema.detailProbe`）的隔离原生路径内交付
  T10 详情有界切片：诚实元数据（未知年份/证据冲突不填充、同名线路不合并、集数
  序号/总数不伪造）、多线路与剧集选择（弹层陷阱 + Back 精确恢复打开者）、收藏与
  精确续播（三元组精确回查、暂停待确认、绝不跨版本携带检查点）、可靠 D-pad 焦点
  （焦点≠选中、迟到恢复被用户输入撤销、刷新令牌不覆盖选择）、正确返回（弹层→详情
  →播放器→来源逐层、四来源恢复查询/锚点/焦点）、已提交上下文跨 Activity 重建与
  真实进程死亡恢复。
- 复用既有组件：Room/Media3（`RoomPlaybackProbe.select` 三元组精确回查）、R12 单写者
  （`PersonalLibraryRuntime.favorite/prepareTarget`）、R11 子层导航（`back`/来源恢复）、
  R13 视觉令牌（`TvPrimaryButton`/`tvFocus`/`PosterImage`/`TvOriginalIcons`）。详情层
  自身零 Room/DataStore 写入；不新增 schema/迁移/图标权威。
- 修复真实缺陷（视觉复核两轮，像素证据定论）：缺图占位图标被 `PosterImage` 不透明
  背景遮盖（0 亮像素）且 org.json 显式 null 经 `optString` 得 `"null"` 字面量伪装图源
  ——图标叠画于其上且仅缺图显示、`reproject` 与 `WorkDetailContextStore.load` 统一
  归一化口径（独立评审 Minor-2）；动作行不换行致 1080p
  "返回"、720p "选择集数/收藏/返回"整体被推出屏外（旧断言因 bounds 字符串格式失配
  从未发现）——`Row`→`FlowRow` 两行换行；两处 settle 竞态（详情打开后节点晚帧入树、
  `applyUserQuery` 挂钩异步重派发焦点抢走走格焦点）——测试等待真实 settle 信号。
- 过程披露：一次 Gradle 增量编译脏状态（设备跑旧 APK，dex 字面量核对发现后
  `--rerun-tasks` 重建并全相位重跑）；模拟器久跑后片库入口偶发 flake 以全新 AVD 实例
  消除；720p 语义 `boundsInWindow` 对滚动内容普遍报零（Row/FlowRow 两版一致的观测
  伪影，与像素矛盾）——可见性改由像素断言/截图背书，已记入架构文档。
- 验证：新增 JVM 30/30（投影 17 + 状态机 13）、原生 R15 37/37（UI 14 + 动作 7 + 导航
  9 + 生命周期 1 + 邻居焦点 1 + 截图 5；含独立评审补强的超长线路名真实聚焦/激活断言）
  + 两阶段进程实验 2/2（新 PID、已提交上下文与
  8000ms 暂停续播恢复）+ 详情 OFF 1/1；既有原生按配对逐类回归（R12 浏览 59+5 假设
  跳过保持披露、个人库 121、播放 34、R13 首页 29、R14 片库 27、普通 OFF 29）、JVM
  全量 431/431、Python 62/62、双 lint 与 R14 基线零消息差异、门禁 6/6、schema 1–6
  逐字节不变、armeabi-v7a/0.9.8/18/签名冻结不变；56 项矩阵全部实际执行（见验证报告）。
- 证据：`docs/verification/2026-10-25-round-15-work-detail.md`、
  `docs/architecture/tv-work-detail-ui.md`、忽略目录 `.local/evidence/round-15/`
  （含 6 张真实 AVD 截图）。
- 停止于 Round 15；完整 T02/T03–T11/M04、生产激活、物理电视与发布验收保持开放；不推送、
  不合并、不联系真实媒体源、不进入 Round 16。

## Round 14 执行记录（原生片库与严格筛选切片）

- 在六授权链（既有五探针 + 新增 `cinema.libraryFilterProbe`）的隔离原生路径内交付
  T10 片库/筛选 UI 有界切片：真实 AppleCMS 合成分页 + R09 `QueryCoordinator`
  （预算/游标/代次/取消）+ R10 严格评估的完整查询管线、年份优先筛选面板
  （不限/具体/年代/起止，步进器 D-pad 可达 2000 年之前）、语言范围（华语按作品语言
  证据，配音/字幕不迁移）、国家/类型/内容类别/排序/来源多选、条件片摘要与删除、
  六种真话状态与部分覆盖不虚构总数、深列表稳定焦点与详情返回恢复、Room v6 关闭重开
  与真实进程死亡恢复。
- Ruling: 新增编译期门 `cinema.libraryFilterProbe` 链式要求全部五个既有探针与精确
  Debug/DebugAndroidTest 任务对；运行期令牌 `round14-library-filter.authorization`
  缺失在数据库打开前失败关闭；探针关闭时“片库”导航逐字保持 R13 浏览占位路径
  （换缝受门控，R13 配对回归零改动通过）。片库上下文只写 `library-ui:<queryId>` 行
  与私有指针；内存候选存储不新增 Room 写入者；查询身份只在用户真实意图下更换。
- 修复真实缺陷：网格焦点解析漏 `focused`、首页 UP 处理与片库分支顺序导致的 held 集
  不成对清理（隔次吞确认键新变体，含面板开关/子层/返回首页三处域切换）、
  `openWork` 回焦目标漏记、子层关闭收集器只认首页记账、新查询安装不启动防抖落盘
  （`begin` 只挂 pending 不起定时器）、条件片行溢出（改 FlowRow）。
- 验证：新增 JVM 27/27（草稿 9 + 投影 9 + 查询桥接 9）、原生 R14 类 27/27 + 两阶段
  进程实验 2/2（阶段 A 提交→`am force-stop`→阶段 B 新 PID 恢复）+ 普通 OFF 1/1；
  既有原生按配对逐类回归（R13 29 + 浏览 59 + 个人库 121 + 播放交接 34 + 普通 28）、
  Python 62/62、普通 OFF 全回归与双 lint、门禁 5/5、schema 1–6 逐字节不变；
  52 项矩阵全部实际执行（见验证报告）。
- 证据：`docs/verification/2026-10-25-round-14-library-filter-ui.md`、
  `docs/architecture/tv-library-filter-ui.md`、忽略目录 `.local/evidence/round-14/`。
- 停止于 Round 14；完整 T02/T03–T11/M04、生产激活、物理电视与发布验收保持开放；不推送、
  合并、发布或签名。

## Round 13 执行记录（原创视觉系统与功能化首页切片）

- 在五授权链（ARM64 存储 + Room 播放 + 浏览恢复 + 个人记录 + 新增 `cinema.homeVisualProbe`）
  的既有隔离原生路径内交付 T02 第一组原创视觉切片与 T10 功能化首页切片：10 枚原创图标
  （可编辑 SVG 母版 + 同一份数据的原生矢量）、单一 token 权威复用、五个可复用组件
  （导航/按钮/瓦片/状态/骨架）、真状态首页投影（UNCONFIGURED/LOADING/READY/PARTIAL/OFFLINE/FAILED）、
  中文五目的地顶层导航（首页/片库/我的收藏/最近观看/设置）、候选设置切片与真实截图证据。
- Ruling: 新增编译期门 `cinema.homeVisualProbe` 链式要求全部四个既有探针与精确
  Debug/DebugAndroidTest 任务对；Release/bundle/publish/install 由 GradleException 拒绝；
  运行期令牌 `round13-home-visual.authorization` 缺失在数据库打开前失败关闭。普通
  Debug/Release 行为、DataStore 唯一写入者、schema 1–6、包身份 0.9.8/code 18、
  armeabi-v7a ABI 与签名冻结全部不变。
- Ruling: 首页为只读投影 —— 共享浏览运行时的个人库实例与候选 fixture，无第二目录或记录写入者；
  持久化仅写 `home:<queryId>` 上下文键；焦点/恢复复用 R11 令牌管线（独立协调器实例），
  图片完成与迟到投递不抢焦点，激活以可见焦点为准，浏览根返回不被保存失败困死。
- 关闭 R12-M1：真实 UI 注入"仓储已提交、UI 回执丢失"后重试；测试先暴露并修复真实缺陷
  （回执丢失后待定意图被清除导致重试反向切换），现待定意图延续、幂等收敛、文案如实、焦点稳定。
- 验证：新增原生 29/29（UI 24 + 真实截图 5）、JVM 新增 19/19（图标 4 + 投影 15）、既有原生 247 项按配对逐类隔离回归（浏览 59 + 个人库 121 + 播放交接 34 + 多进程 7 + 普通 26），全轮唯一方法 277/277
  全部通过、Python 62/62、普通 OFF 全回归与双 lint 告警增量为零、门禁 5/5、schema 1–6
  逐字节不变、暂存区安全检查零违规；54 项矩阵全部实际执行（见验证报告）。
- 证据：`docs/verification/2026-10-25-round-13-visual-home.md`、
  `docs/architecture/tv-original-visual-system.md`、忽略目录 `.local/evidence/round-13/`。
- 停止于 Round 13；完整 T02/T10/T11/M04、生产激活、物理电视与发布验收保持开放；不推送、
  合并、发布或签名。

## Round 12 执行记录

明确聊天授权仅既有隔离分支本地交付，起点 `e9f6cc1875a8a2eb93060feb115cf3b74b9a9204`。ZIP 四项 SHA-256 与三份独立附件字节核对通过，原件留在忽略证据；仅导入 scoped plan，个人路径最小脱敏且不修改安全规则。

- 复用同一 R11 原生导航／writer lease、R05 仲裁／逐集检查点、R06 Media3 桥和原 RoomFavoritesStore，加入真实收藏／历史／继续观看投影。真实播放确认独立于 5000ms 续播门槛；短播放历史仍保留准确集／版本；旧 v5 合格续播不因缺少 R12 新历史证据而消失。
- 三种持久动作分离；固定 generation 范围抵抗旧请求重试和迟到回调；取消收藏／撤销使用持久动作修订及真实提交。缓存、停用来源、保守拆分不清除或改指个人记录。新实际播放可在同进程删除后重新建立会话。
- v5→v6 新增三表（总25），旧22表定义与 schema1–5字节不变；有效多集／版本、三browsecontext、来源／暂存／查询／订阅种子通过真实迁移、失败回滚、FK、identity与关闭重开。
- 最终新增候选原生41/41（存储17、迁移3、UI21）；前轮与新增R07防复活回归206/206；全轮唯一方法 **247/247**，0失败／跳过／未运行／重复。方法 starts/terminal 与当前源码多重集一致，重复的离线／截图／外部准备不重复计数。
- 普通四门OFF：JVM351/351，Python62/62，Debug／unsignedRelease／AndroidTest与双Lint验证；实际全量140任务重新执行，Lint消息多重集新增／移除0。包版本0.9.8/code18、min24/target28、普通ARMv7 JNI／manifest、签名冻结、旧DataStore与update保持。
- 独立外部 app PID `12152→12558→12897`，分别证明后台 am kill 与 force-stop 后显式启动：History原context／work、实际播放按钮焦点、episode2／original variant／8000ms暂停保持；已删除98不复活，收藏98/99保留。Activity recreate 单独原生验证，未混称进程重建。
- 自有 API30 ARM64 `emulator-5586`，独立ADB5041，每次安装／执行前核验ownedPID/boot/AVD/API/ABI/runner/同签名配对；实际关闭该AVDwifi/mobile且无active default network后复跑本地个人列表。没有访问物理设备或真实源、没有扰动宿主网络或共享ADB5037。
- 独立审查 S-I1/S-I2 与整分支 R12-I1/R12-I2 均真实 RED→GREEN；后两项通过限定复审，最终无未关闭 Critical/Important。Conditional Minor R12-M1（尚未实际UI复现的回执丢失重试文案歧义）如实保留，数据层回执丢失已原生验证。
- 实际视觉第二轮复查发现旧clipped-bounds断言不足；加强完整测量高度／真实content viewport断言取得RED，再用现有滚动容器的BringIntoView修复完整撤销按钮可见性，没有进入T10。编译／测试就绪错误与真实产品RED分别记录，未删断言或抑制检查。
- Ruling: 保留无upstream本地分支；明确不推送优先于通常Git自动推送。仅metadata fetch，不pull／merge／rebase／push／tag／publish／sign。

[53项矩阵及完整证据](../../verification/2026-10-25-round-12-personal-library.md)、[个人记录契约](../../architecture/tv-personal-library.md)。52项适用通过，仅MIG-01=N/A（真实执行5→6）。完整T09/M04/A06–A11/A18、ARMv7/API24电视、物理遥控器、真实旧用户升级／生产源仍未验收。停止于Round12，不进入T10/T11。

## Round 11 执行记录

明确聊天授权按ZIP执行，仅既有隔离分支本地交付；起点 `32b6bfdcfce6e730af68c246f87c694d09e30915`。ZIP四项SHA-256和三个独立文件逐字节通过；原附件留在忽略证据，工作树与main开始时均干净。

- A/B：默认OFF构建/运行门、候选writer lease、完整query/context/active/latest原子保存、可取消恢复与布局token；不改schema5。独立审查的四项B问题均真实RED→GREEN。
- C：真实Compose/NavHost、稳定opaque ID、三页来源、深列表、模态与FocusRequester确认；三项审查竞态均真实RED→GREEN，最终原生29方法。
- D：真实文件库重开/回滚/取消/失败/旧文件保护；停止时Insets污染、首次零高度offset、迟到Q1和modal同排回退均实际失败驱动修复。最终30原生方法，独立限定复审spec/quality通过。
- E：JVM349/349、Python62/62、普通三门OFF140任务全部执行；双lint0Error，告警ID/级别/消息多重集与基线完全相同。最初5项lint失败保留，remember及仅ON公开Window.Callback修复后重新执行原生/外部实验。
- 最终执行：205/205原生（C29+D30+OFF1+旧145），0失败/跳过/未运行/重复；外部后台am kill、force-stop、未提交中断三组通过。11个owned AVD实例均关闭，普通构建输出已恢复OFF并与保存产物hash一致。
- 独立终审：深锚点删除与同token并发flush两项Important均有真实RED→GREEN，统一修复内的新无锚点回归保留58/59失败后补守卫，最终59/59。限定复审F-I1/F-I2均ADDRESSED、修复新增0/0/0，无延期；只读核对证据，不是独立重跑。最终实现3c47c8c，当前index337文件0违规，20项受测源码与提交一致。
- Ruling: 导入计划仅将个人绝对路径脱敏，原指令/矩阵作为输入证据保留。代价：原始字节须查忽略附件，仓库计划不是原ZIP字节。
- Ruling: 复用R07签名/profile/consent/emulator与writer互斥，browse ON需要playback编译门但不准备或激活handoff，ON立即拒绝旧writer。代价：本轮只在严格授权profile可运行。
- Ruling: 保留既有无upstream分支；显式local-only覆盖通常push收尾，只fetch metadata，不pull/merge/push。代价：无远端备份。
- Ruling: 计划的home-feature路径表述亦作最小脱敏以符合当前index扫描；原件保留，不修改扫描规则。代价：原件校验须回到附件。
- Ruling: 精确query context、顶层latest与global active使用现有v5单事务payload，不升schema。代价：MIG-02为N/A，不声称不存在的5→6迁移。
- Ruling: 不新增Compose测试依赖；既有SemanticsOwner、AndroidX Test、平台按键和外部XML验证实际焦点。代价：这些是合成API30 ARM64证据，不等同目标TV或生产验收。

[42项矩阵与完整结果](../../verification/2026-10-24-round-11-browse-restoration.md)、[导航与恢复契约](../../architecture/tv-navigation-restoration.md)。完整T08/A03/A04/A05/A19/M02/M04仍未验收，普通导航/旧DataStore保持；不进入T09/T10、不访问真实来源或物理设备、不发布。

## Round 10 执行记录

明确聊天授权按ZIP执行，仅本地提交，不推送/合并/发布或进入后续任务。起点 `87303304947649a4a464c781b57feae8e01dbb04`，既有隔离工作树初始干净，ZIP与四项SHA-256全部通过。

| 步骤 | 实际状态 |
| --- | --- |
| R10-1 | 实际接口/旧运行边界先映射，真实RED后候选边界及Preferences哨兵通过；JVM281/281 |
| R10-2 | AND/OR三值、作品语言作用域、出处/冲突及精确映射；真实RED→JVM290/290 |
| R10-3 | 精确年、整十闭区间、范围、旧/未来年份及类型化校验；真实RED→JVM300/300 |
| R10-4 | 既有协调器接入、完整query payload、严格facts摘要、范围计数/排序、显式移除审计；JVM313/313，真实Room丢选择RED→候选native104/104 |
| R10-5 | FAC-03原样矩阵夹具补充后完整JVM314/314、Python62/62、全native145/145；普通140任务与双Lint执行通过，新增告警0；全轮独立终审0/0/0、index319文件0违规，本地Git收尾见验证报告 |

- Ruling: 范围以Round10候选切片为准，不替换主计划T07的普通接口。代价：产品严格筛选、UI及全T07仍未接入。
- Ruling: 包内原件保留忽略证据；导入计划仅脱敏个人绝对路径以满足既有index规则。代价：原始字节须对照附件校验，不放宽检查。
- Ruling: 保留既有无upstream本地分支，允许metadata-only fetch，不创建upstream、不pull/push/merge。代价：本地交付没有远端备份。
- Ruling: 每个任务用未提交精确diff独立审阅，完整回归后统一作本地提交。代价：不提供临时stub/失败阶段的中间提交。
- Ruling: 按原R10-N标题提取brief，证据与账本保留，不为技能脚本重写计划或删除工作树。代价：人工对应步骤并占用本地证据空间。
- Ruling: 复用schema5现有candidate_query_sessions.payload保存完整选择，schema1–5字节不变，无schema6；迁移矩阵DB-02为N/A。代价：只有新旧候选payload兼容证明，不声称执行不存在的5→6迁移。
- Independent review: 四个实现任务及全轮23路径独立只读终审均通过，0Critical/0Important/0Minor，无待修复或延后项；未独立重跑运行证据。18项应用/测试指纹与受测源码/index相等，精确23路径本地交付，最终HEAD见聊天与final-delivery.json；不push/merge/publish。
- Native: 本轮145个方法全部实际通过，start/terminal/source清单多重集相等，无skip/not-run/duplicate；两台API30 ARM64自有AVD serial5574/5576已按身份门禁正常关闭，host PID消失。原Room1→5、身份、订阅、播放/交接全部重新执行。
- Preserved: 普通路径、旧筛选默认、DataStore/Room writer authority、旧normalizer、schema1–5、空sources、包/版本0.9.8/code18、普通ARMv7、unsigned Release和签名冻结保持。未访问物理/个人设备或真实来源，未接UI/DI/生产。
- Evidence: [35项矩阵及实际证据](../../verification/2026-10-24-round-10-strict-filtering.md)、[结构说明](../../architecture/tv-strict-filtering.md)，原始命令/报告/指纹在 `.local/evidence/round-10/`。仅34项适用合成检查及1项条件N/A；完整T07/M03/A12–A16、真实数据/TV/API24/ARMv7/生产验收未完成；停止于Round10。

## Round 09 执行记录

聊天明确授权按附件执行，仅本地提交，不推送、合并、发布或进入后续轮次。起点 `96962f6a5255431bf9d316a4bfacac0d55ef7ca3`；复用既有干净隔离工作树。ZIP、四项清单及三个单独附件全部校验一致；metadata-only fetch 后 main 与 origin/main 仍为原基线，未 pull 或改变分支。

| 步骤 | 实际状态 |
| --- | --- |
| R09-1 | 原接口/十九表/schema1–4与两个Minor已核；真实seed两行缓存并淘汰、A–B–C三成员拆分和重开均实际native通过，原代码立即通过，不伪造RED |
| R09-2 | 复用原始观察，字段供给/样本过滤证据/健康/新鲜度分开；精确alias失效、Room重开、时钟回拨及反序列化约束已测试；最终能力JVM17/17 |
| R09-3 | 独立有限游标、预算、循环停止、代次/取消、部分覆盖、未知总量及既有证明分组；原始证据和两源入口保留；查询JVM17/17、native6/6 |
| R09-4 | 声明式限额解析、显式发布者根、验签/重放/SSRF防护、提交时重验授权和停用项；订阅JVM21/21、native7/7；默认空目录兼容且原文件未改 |
| R09-5 | 最终JVM276/276、Python62/62、native143/143逐方法一致；普通双门禁OFF全量140任务实际执行；双Lint0Error且告警多重集与Round08一致；schema1–4字节及十九旧表定义保持 |

- Ruling: 原T02 `knownMatchedCount` 为可靠匹配总量；部分覆盖保留null，候选快照单独给出当前已证数量，不改变原公共契约。完整T07多维谓词/排序/年份UI均未实现。
- Ruling: 新持久状态无法存入原十九表，采用唯一显式4→5迁移，仅增三个candidate表；真实v4十九表全行种子升级、外键检查及关闭重开通过，原schema1–4不改。
- Ruling: 附件原件保留于忽略证据；仓库计划仅将个人绝对路径替为占位符，不放宽index安全规则。代价是原始字节需对照保存的附件。
- Independent review: 各切片首审的能力1项、查询3项、订阅1项Important均有实际失败断言和修复后定向复审；全改动终审0Critical/0Important/1Minor。该Minor为较晚超时后回拨复活旧证明，作为新鲜度契约问题补JVM真实RED17/1→GREEN17/17并重跑native102项；限定复审确认addressed、修复新增0/0/0。审查者未独立运行测试/ADB，不把源码复审称为独立重跑。
- Final verification: 143个原生方法来自最终修复后的102项候选/存储套件，加此前已通过且源码未变化的41项播放/交接回归；无skip/not-run/duplicate。独立进程恢复分别实测5544ms、7522ms且暂停。两台专用API30 ARM64 AVD固定serial5570/5572、逐操作门禁，现已关闭且host PID消失；共享ADB及合成数据保留。
- Preserved: 二十九条关键普通运行/发布路径逐字节保持，默认sources为空，应用ID/0.9.8/code18、普通ARMv7、unsigned Release及签名冻结不变。未操作物理或个人设备、未扫描真实来源、未加生产信任根或媒体目录、未接运行DI或旧writer authority。
- Local delivery: 实现提交 `3cb684fabcf0a98f9bc0845ecb40bcecaff6a562`，27个明确路径、3905增行/7删行；25项受测应用/测试/schema与暂存和commit blob一致，实现index302文件0所检查规则违规。本账本与验证报告另作本地文档提交，最终完整index303文件同样0违规；最终HEAD由Git日志与忽略证据final-delivery.json记录。仅保留既有无upstream本地分支，不建立远端分支、不push/merge/publish。
- Evidence: [逐项验证及命令](../../verification/2026-10-24-round-09-source-query-subscription.md)、[实际接口与边界](../../architecture/tv-query-subscription.md)，原始日志/指纹/审阅在 `.local/evidence/round-09/`。只标记本轮合成T06切片与两个覆盖缺口；完整T06/M03/A14/A15/A17/A18/A21、真实来源、API24/ARMv7 TV/UI/生产验收均未完成。停止于Round09。

## Round 08 执行记录

用户明确授权按附件执行，仅本地提交、不推送；原生仅限已确认专用模拟器。起点 `4097694bef7107ca1c6953d55bf9c78c3914b8ac`；隔离分支和main均干净，附件四项清单和三份独立附件字节一致。限定候选实现及本轮验证已完成，不代表产品或生产接入验收。

- Pre-flight: R08-1 的实际契约与原始字段输入供R08-2标准化；R08-2证据供R08-3持久化；R08-3稳定身份与事务供R08-4投影；R08-5验证全部接口及门禁。
- Ruling: 沿用本账本R08标题及忽略证据 `.local/evidence/round-08/`，不改写为技能数字Task脚本或删除原始证据。代价：步骤人工对应、保留本地证据空间。
- Ruling: 附件原始计划含个人绝对路径，会触发现有index检查；完整原件留在忽略证据，仓库同名计划仅把工作树绝对路径替为占位符，原始SHA-256为 `16a8dc1e89c1ed550c30c8e48e46b74ef5d50885a521793db5e70b08f3354471`。代价：需原件校验原始字节，不放宽安全规则。
- Ruling: 不修改旧CMS解析和运行契约；候选投影接受原始JSON及显式身份并复用RemoteVod DTO，保留旧解析无法表达的字段存在性与原文。代价：本轮只验证离线候选入口。
- Ruling: 复用既有ID与FieldEvidence；字段名、观察时间、字幕和关联历史作为候选记录补足，不新建平行公共模型或更改旧codec。代价：候选记录与现有展示契约需显式投影。
- Ruling: 现有分支无upstream为既定local-only用途；本轮明确禁止push，不建立远端分支。代价：无远端备份。

- Ruling: 错误关联拆分保守解散整个候选组，各条目恢复各自原work key；撤销该组证明，原证据及历史保留，原证明不可复用。代价：组内可能正确的关联也需新证明恢复，优先避免传递误合并。
- Round07有限独立复审：fresh-context审查核心修复、proof接线及对应测试设计，未发现受控合成profile范围内剩余Important/Critical；未重新执行103方法、未核验历史日志与AVD，不推断生产／TV验收。

- Final review: fresh-context独立只读审阅全部候选实现与测试、原schema及运行接入，0 Critical／0 Important／2 Minor。实现指纹保持；审查者未运行测试、构建或ADB。
- Final: minor (deferred): 缓存淘汰用例未seed缓存行，证明范围限于空缓存删除；既有原生缓存测试另覆盖实际缓存删除与个人记录保留，未宣称组合场景全覆盖。
- Final: minor (deferred): 整组拆分用例仅二成员一个proof；代码全组撤销已审阅，三成员传递拆分的独立原生断言未补充。
- Final: Ruling: 生产核验authority、命名空间注册和证明取得流程保持未接入。代价：合成证明不能代表生产信任。
- Final: Ruling: 查询协调、严格筛选、实时来源和可播放性不在本轮实现。代价：不能作完整T05/T06/T07或片源可用声明。
- Final: Ruling: 历史容量／自动回收策略仍未实现，候选历史普通运行不生成。代价：将来生产接入前须另定保留策略。
- Final: Ruling: ARMv7 TV、物理设备、UI和真实用户升级保持未验收。代价：ARM64合成原生不能替代产品验收。
- Final: Ruling: 独立审查不验证运行日志；实际命令、计数、产物与设备证据由执行者核对。代价：审查结论仅源码／测试设计层，不是独立重跑。

| 步骤 | 状态 |
| --- | --- |
| R08-1 | 契约与schema核对完成；基线JVM实际重跑28任务通过；前轮有限独立复审未发现剩余Important/Critical（未运行测试） |
| R08-2 | 标准化／身份／投影／codec实际RED→GREEN，完整JVM221/221通过 |
| R08-3 | 原生20项先失败，补足边界后23/23实际通过；schema3完整11表种子→4逐字段一致，close/reopen与事务／SQL约束通过 |
| R08-4 | 离线DTO投影和明确entry/episode/variant身份已实现；列表详情追加、路由变更、冲突和未解析保留有JVM／原生证据 |
| R08-5 | 最终JVM221／Python62／native126逐方法全部通过，140普通Gradle任务实际执行；双Lint无变化；0 Critical／0 Important／2 Minor，精确本地提交中 |

- Ruling: 标准化、投影、事务与新schema共同构成可验证候选，使用一个通过完整回归的实现提交，另作证据文档提交。代价：不提供未验证或临时stub的中间commit。
- Local delivery: 实现提交 `f3da504dfece3ee2600d04b197b10d24da9284ca`，21个明确路径；19项应用／测试／schema与审查、最终测试、index和commit blob一致。提交前index278文件、0所检查规则违规；main与fetch后origin/main仍为原基线。文档提交及最终HEAD由Git日志和忽略证据final-delivery.json记录，不push／merge／publish。
- Final verification: 新增23项文件数据库原生与既有103方法全部实际通过，0 skip／not-run／duplicate；两台专用AVD已按身份门禁关闭、host PID消失，合成数据及共享ADB保留。普通门禁OFF、ARMv7、unsigned Release、旧schema1/2/3及25条运行／发布路径保持。
- Evidence: [本轮验证](../../verification/2026-10-24-round-08-work-identity.md)、[结构说明](../../architecture/tv-catalog-identity.md)，原始证据在忽略目录 `.local/evidence/round-08/`。T05/T03/T04整体、M02、A13/A16/A18整体验收、生产信任/目录/迁移、UI及ARMv7 TV仍未完成；停止于Round08。

## Round 07 执行记录

用户明确授权按附件执行，仅本地提交，不推送、合并或发布。起点 `a7deea8a88ff0afa2f7ba06fd7c4f615f8018af1`，隔离分支及 main 均干净；包内四项 SHA-256 和三个独立附件逐字节一致，原计划导入。

- Pre-flight: R07-1 的默认关闭授权和实际旧 writer 图供 R07-2 持久状态及 R07-4 探针使用；R07-3 实际单例捕获和精确映射供 R07-2 激活事务使用；R07-5 验证全部边界。
- Ruling: 沿用既有账本及 R07 标题和 `.local/evidence/round-07/`，不改写附件来适配技能数字 Task 脚本，不删除原始证据。代价：人工对应步骤及保留本地证据空间。
- Ruling: 原工作树不是 App managed worktree，原生附加工具已明确拒绝；继续使用现有 Git linked worktree，不另建或重置。代价：本轮不增加 App 管理附件。
- Ruling: v2 无交接 authority 表，新增 v3 单独状态表及显式2→3迁移；原1／2 schema字节保持，1→2→3与2→3均做native保留测试。代价：候选数据库的schema增加一版，普通生产路径仍不开库。
- Ruling: 演练只启动播放器；browse／filter／来源原始值完整staging并保留旧只读访问，激活后这些旧writer显式拒绝修改，不提供新配置UI或生产导航切换。代价：本轮不能宣称完整产品迁移。
- Ruling: 分支没有 upstream 是既定 local-only 用途；用户本轮明确禁止 push，保留该状态，不建立远端分支。

| 步骤 | 状态 |
| --- | --- |
| R07-1 | 基线原生68＋独立进程2、JVM177实际通过；最终授权8项及单例捕获／门禁原生4项通过，普通生产入口保持 |
| R07-2 | 真实 Room 状态、AtomicFile 意图、屏障内重新捕获及单事务激活已核验；17项状态／导入／故障原生测试通过，缺库／缺授权／缺意图均不退回旧写入 |
| R07-3 | 四个既有 DataStore 单例捕获，完整 typed staging／accounting；10项主机精确映射测试及原生幂等、内容变化、损坏／未解析保留通过；原文件哈希一致 |
| R07-4 | 真实 WAV／Media3：旧2000ms暂停恢复→新Room7520ms提交→PID5829换5935完整相同快照暂停恢复；实际Probe OFF拒绝回退；媒体替换及撤销授权4项原生通过 |
| R07-5 | 最终JVM199／Python62／native103逐方法全部通过，无skip／not-run／重复；140个普通Gradle任务实际执行；双Lint与Round06相同；一次独立审查的3项Important均实际RED→GREEN，未二次复审 |

- Final review: 35个审查时候选文件前后SHA一致，独立只读审查0 Critical／3 Important／0 Minor。修复为：测试profile持锁等待已接受的DataStore actor真实IO确认再传播取消；prepare／激活重验实际媒体证明；切集／retry在副作用前重验目录。新增4项原生断言先4失败，分项修复后4通过，再执行完整回归。作者负责修正后验证，没有第二次独立审查。
- Ruling: 交接AVD保留了本轮合成旧文件，不能满足原Round06空白profile前置条件；关闭并保留原数据后，用现有镜像新建空白自有AVD跑67项既有核心及2项进程／1项Legacy OFF测试，不清空旧profile。两个AVD逐操作门禁、最后均已关闭，host PID消失，共享ADB保持。
- Ruling: 状态、审计、屏障及探针互相依赖，采用验证后的一个完整实现提交，再单独记录交付；不提交临时stub或未通过状态。
- Local delivery: 实现提交 `e8fecd200c1cfe32f18e94dff974d7844a30401b`；36个明确暂存路径，33个应用／测试／schema指纹与验证及commit blob一致；实现index260文件、0所检查的违规。main与刷新后origin/main均仍为原基线，隔离分支仅本地保留，无push／merge／publish。
- Final documentation: 验证报告与本账本定稿独立本地提交；最终完整index261文件、0所检查的违规，33项受测源码保持与实现提交相等；最终HEAD由Git日志及忽略证据final-delivery.json记录。
- Evidence: [完整本轮验证](../../verification/2026-10-24-round-07-legacy-handoff.md)；[本轮结构](../../architecture/tv-legacy-handoff.md)；原始证据在忽略目录 `.local/evidence/round-07/`。普通Debug／Release仍旧存储独占，T03／T04整体、M02、A05–A11／A20、真实用户升级、ARMv7／TV及签名发布仍未验收；停止于Round07。

## Round 06 执行记录

起点实测 `d75cb40622a5adc8d9680a198063d0a0fc01cec4`，分支 `codex/contract-foundation`；隔离工作树与 main 起始均干净。包内三项 SHA-256 及独立文档字节一致，计划原样导入。仅授权本地提交，禁止 push/merge/publish/真实设备及后续轮次。

- Pre-flight: R06-1 门禁产出唯一 writer 模式，R06-2 bridge 消费；R06-2 不可变事件与真实身份供 R06-3 Media3/Room 和独立进程验证；R06-4 在两属性关闭后重建普通产物。
- Ruling: 沿用本账本和 R06 标题，不套用技能数字 Task 脚本；原附件不改写。代价是手工记录每步证据。
- Ruling: 旧 MovieDetail 无稳定 episode/variant ID，只允许测试仓库显式注入既有 PlaybackTarget 和私有本地 URI；线上仓库无法证明身份则拒绝探针播放。代价是本轮不能用于真实来源或宣称生产接入。
- Ruling: 运行时拒绝任何旧 progress/browse/filter 文件，包括空文件；不解析、隐藏或清除旧记录。代价是只能使用全新可丢弃 profile。

| 步骤 | 状态 |
| --- | --- |
| R06-1 基线和门禁 | 起点／源码／既有工作树已核；新跑基线JVM149/149；六类非法请求均在执行前拒绝，默认和旧记录门禁有用例 |
| R06-2 事件和生命周期 | 最终JVM177/177；真实retry与两项审阅竞态均实际RED→GREEN；单调事件时间、切集屏障、单次请求取消及后台暂停保护已接入Probe |
| R06-3 实际 Media3 / Room 与进程恢复 | 最终71/71，逐方法源清单与start/pass对应：68主套件＋2独立进程＋1 Legacy OFF；AVD afb36413／emulator-5564 已关闭；PID2677→2783恢复5554ms且暂停 |
| R06-4 回归、独立审阅、安全和本地提交 | 两属性OFF全量140任务实际执行；Python62/62；双Lint 0Error，21/22Warning各1Hint与Round05相同；实现本地提交23dfac4；最终完整index安全检查238项0违规；文档提交见验证报告 |

- Final review: 一次独立只读审阅核对15个候选文件指纹；0 Critical、2 Important、0 Minor。发现“慢切集／retry覆盖较新的后台暂停”和“取消单次await仍可能执行旧媒体副作用”；两项均补真实SQLite锁／单次Job取消的确定性RED并修正，完整回归已通过。修正不再独立复审，作者用红绿和全量证据负责确认。
- Ruling: 双进程 Phase A 的预期取自真实 pause onEvents 处理后、明确 boundary 提交之后，Phase B 保留完整快照比较。首次失败是预期记录早于合法的下一次同位置提交，不能将较早回执称为最后提交。代价是测试需显式等待播放器事件，不能以任意固定延迟冒充同步。

- Final verification: 首次全量Lint暴露3个API26调用错误；改为兼容min24的canonical File边界校验，路径／URI越界测试3项红绿后重跑全部。schema1/2、原coordinator/store、DataStore、Koin、manifest及发布身份不变。
- Local delivery: 实现提交 `23dfac429d5fc1cf8a547f8cb69304490593b617`，暂存236个index文件安全检查0违规；原main／刷新后origin/main仍为 `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`。无upstream的本地分支用途明确，不建立远端分支，不push/merge/publish。
- Evidence: [完整Round06验证](../../verification/2026-10-24-round-06-playback-runtime.md)；[运行时结构](../../architecture/tv-playback-runtime-probe.md)。真实旧用户迁移、ARMv7／TV视频和遥控、Activity导航自动恢复、T03／T04整体、M02、A06–A09均未验收。本轮停止，不进入后续工作。

## Round 05 执行记录

用户明确授权按附件实施、仅本地提交不推送。起点 `a46a8a97947d1b72fefd3361f9742df9540a5be6`，原 main `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`，两处干净；复用原工作树。计划原字节导入，SHA-256 `662e6d80c8b1ebc30f2e25abe91c5bf35d689d34a246be24aa1d6c579677a039`。完整证据见 [Round 05 验证](../../verification/2026-10-24-round-05-playback-checkpoints.md) 与 [候选结构](../../architecture/tv-playback-checkpoints.md)。

| 步骤 | 实际状态 |
| --- | --- |
| R05-1 真实契约／事件映射 | 沿用原 snapshot／commit；现有播放器、schema、旧文档和官方 Media3 语义已核对；生产源码保持 |
| R05-2 单调钟与候选协调器 | JVM 红绿及最终 25/25；五秒实际播放、零值／时长、快退、预加载、切集屏障、完成及取消 |
| R05-3 原子提交／持久仲裁 | 真 Room generation／sequence／payload 幂等及独立作品指针；原 schema 1 不改，真实 1→2 增量迁移；原生全部 61/61 实际通过 |
| R05-4 未来事件接入 | 记录 Media3 ready、playing、buffering、seek、ended、后台语义；未接现有播放器或 DI，不将候选视为运行时修复 |
| R05-5 回归／审阅／本地交付 | 修正后 JVM 149/149、Python 62/62；134 个普通 Gradle task 实际重跑；Debug／Release Lint 21／22 Warning，各 1 Hint、0 Error；暂存安全检查和提交信息见验证报告交付段 |

- Pre-flight: 真实 domain/schema 是策略与 adapter 的输入；策略事件和持久代次分别控制调度及跨会话顺序；迁移与实际 AVD 共同决定 native 证据；property-off 产物必须在 ARM64 验证后独立重建。
- Ruling: 继续使用原账本及 R05-1–5 标题，不改写附件以适配技能数字 Task 脚本；计划原字节导入。代价：人工对应步骤。初期“原文含私人路径”的旧轮次假设已由本轮实际扫描纠正，没有因此改正文。
- Ruling: 新选普通观看以 5,000ms 实际播放提升；4,999ms 误点退出不提升；真实短片结束可完成当前集。显式 seek／完成的失败或取消保存独立保留待确认状态。代价：阈值是待未来产品／实机评估的政策，不能宣称故障下最多丢五秒。
- Ruling: schema 2 新增三张仲裁／续播表，原七表和 1.json 字节保持；不从旧记录推测会话代次或作品指针。代价：真实旧 DataStore 迁移、身份关联与 runtime cutover 仍未完成。
- Final review: 一次 fresh-context 独立只读审阅，0 Critical、3 Important、0 Minor；三项均实际 RED→GREEN 修正。五项 JVM 回归先失败；真实 Room 两项组合先失败；修正后 149 JVM／61 native／62 Python 全绿。修复为短会话显式保存重试、真正重看恢复进行中、锁前捕获事件时间防止慢写入虚增观看。没有第二次独立复审，没有延后 Minor。
- Final: Ruling: 审阅保留项继续未验证／范围外：生产 Media3／生命周期／TV、真实旧 DataStore 升级回滚备份、跨源身份与时间轴、进入 API 前的外部事件乱序、违约复用 sessionId／低层 DAO 写入、长期保留与删除政策、真实断电／进程／磁盘故障、全剧完结及 UI 展示。代价：候选测试不提供这些产品关口的通过证据；本轮不扩张去实现。
- Ruling: 用户已选 local-only，按保留分支／工作树收口，无需再次询问 merge／push 菜单。原始证据保留于忽略目录，不按技能默认清理证据。代价：没有远端备份，AVD 文件占用保留但已关闭。
- 本地实现提交：`194040bf92868c15b8570e136d515ac3546a41eb`，15 个精确路径；提交前 index 223 文件、0 违规。本报告／账本另作本地结项提交，最终文档 index 224 文件、0 违规，已核对暂存 blob 与工作区字节；最终 HEAD 见交接记录，不推送。

## Round 04 执行记录

起点 `541b7ce5370693c5f96c848bb0cd3e8ec8cbc7b5`，两处 checkout 干净。唯一执行计划 `2026-10-24-round-04-abi-native-verification.md` 的 SHA-256 为 `67c5a1c73022423ed574ec3ebe9f57d27b151e16790c14dfd0dbc6bffff096cc`；包内校验及独立附件字节均一致。原始计划和日志在忽略的 `.local/evidence/round-04/`；详见 [Round 04 记录](../../verification/2026-10-24-round-04-abi-native.md)。

| 步骤 | 实际状态 |
| --- | --- |
| R04-1 ABI 根因与路线 | property-off 重建、实际 APK / AAR / ELF / AGP 合并逻辑已核实；选择 Route B，复用现有 API 30 ARM64 镜像 |
| R04-2 隔离测试产物 | 打包前置断言 RED → GREEN；真实 ARM64 库、482 个非 JNI 条目相同；property-off 三个 manifest / JNI 与基线一致；Release / bundle / publish 及依赖注入门禁通过 |
| R04-3 单目标原生执行 | 新建唯一 API 30 ARM64 AVD，emulator-5562；15 + 19 方法全部实际执行 / 通过，0 失败 / 跳过 / 未运行；目标已关闭 |
| R04-4 实际缺陷修复 | 没有原生缺陷可修复，全部存储 / 测试源码保持；仅修正了本轮构建门禁误判 |
| R04-5 回归、审阅、本地交付 | 应用 124/124、Python 62/62、135 个 Gradle 任务实际重跑通过；Debug / Release Lint 21 / 22 Warning，各 1 Hint、0 Error；独立只读审阅无 Critical / Important / Minor；审阅后 index 212 files、0 违规；仅本地交付，最终提交 HEAD 随终端交接记录，不推送 |

- Pre-flight: R04-1 的实际依赖 / ABI 决定 R04-2 产物；R04-2 的兼容产物及 R04-3 的目标身份共同决定能否执行；仅 R04-3 复现的缺陷允许 R04-4 修复；R04-5 必须独立重建 property-off 产物，不能混用 opt-in 结果。
- Ruling: 沿用既有账本对应 R04-1–R04-5，保留原计划字节而不改写为技能脚本的数字 Task 标题；原始证据不删除。原因：与既有记录及附件证据要求一致。代价：步骤需人工对应。
- Ruling: 原始计划含个人路径，按既有 index 安全规则仅保存在忽略证据中，Git 记录文件名及 SHA-256。代价：复核原字节需要本地附件 / 忽略证据。
- Ruling: 本轮明确只允许本地提交，不推送；专用分支无 upstream 是既定交付状态，不建立远端分支。代价：本轮没有远端备份。
- Ruling: 使用现有 ARM64 镜像与真实 AAR 库，不下载其他镜像；明确两个 buildType 的 ABI，避免实际 addAll 合并造成混合 ABI。代价：测试结果只能证明 ARM64 候选行为，ARMv7 实机仍未验证。
- Ruling: 保留 `.debug` 测试身份，因为原 fixture 明确要求此身份；以新建一次性 AVD 和逐命令 serial 门禁隔离数据，不修改原测试。代价：测试 APK 使用常规 Debug ID，必须坚持目标身份门禁。
- Ruling: 保留两处 buildType ABI 声明造成的额外一条重复 ChromeOS 告警；实际普通产物与基线相同，不压制 Lint 或增加范围外 ABI。代价：Debug / Release Warning 数各增加 1。
- Final review: 一次独立只读审阅，无 Critical / Important / Minor；未重跑测试，无审阅修正或二次复核。九项保留边界逐项裁定见 R04 报告：TV / ARMv7、进程 / 断电、真实耗尽、真实迁移 / T04、完整读失败编排、其他 DataStore 类型、设备端回提均未验证；index / commit 由作者随后实查；整个 T03 / M02 / A01–A24 与发布继续不作通过声明。代价：本轮原生证据严格限于所列候选操作。

## Round 03 执行记录

唯一执行计划为附件 `2026-10-24-round-03-native-storage-verification.md`，SHA-256 `4f0b86c7a3efe4a4349e6fd2a97a4a93b4fd829b87c7205e15b41cb479cc7f17`；原字节及原始输出保存在忽略的 `.local/evidence/round-03/`。实查起点 `f588ed11d6f998b36de16feaa7a141fda1cc2bef`，两处 checkout 干净、无 HEAD 漂移。详细证据及全部 34 项方法见 [Round 03 记录](../../verification/2026-10-24-round-03-native-storage.md)。

| 步骤 | 实际状态 |
| --- | --- |
| R03-1 交接、工具链与兼容目标 | 清点 15 + 19 项原生方法，无 Ignore；构建成功；最小新增 emulator 37.2.12 与一个 API 30 arm64 镜像；APK 仅 armeabi-v7a，而镜像 abilist32 为空，NATIVE_TARGET_BLOCKED |
| R03-2 单目标原生执行 | 未创建 AVD / serial，未安装 APK、未启动 runner；executed / passed / failed / skipped 均 0，not run 34 |
| R03-3 实际失败修复 | 未开展；没有原生失败或红绿证据，应用 / 测试 / schema / 契约全部保持 |
| R03-4 可用回归与证据 | Python 62/62、应用 124/124；134 个 Gradle 任务实际重跑成功；debug Lint 20 Warning + 1 Hint，release 21 Warning + 1 Hint，新增一条 DataStore 版本提示；一次独立只读审阅无 Critical / Important / Minor；实际暂存 index 211 文件、0 违规；仅本地证据提交，最终 HEAD 随终端交接记录，不推送 |

- Pre-flight: R03-1 的真实 APK / runner / 兼容目标是 R03-2 的输入；R03-2 的实际失败才允许 R03-3 修改代码；R03-4 必须区分 host、compiled-only、native 和 blocked。ABI 不匹配使原生执行与修复步骤不可进入。
- Ruling: 沿用现有账本对应 R03-1–R03-4，不改写原计划以适配技能数字任务脚本。代价：人工对应状态。
- Ruling: 只裁定已核实路径因 ABI 受阻，不把未验证的 ARM32 / 其他镜像路径说成不可能；停止额外下载或 ABI 改造，完成可用主机检查并据实交付。代价：34 项仍为 compiled-only，整个 T03 / M02 / A01–A24 未验收。
- Ruling: 原始计划含个人路径，保留在忽略证据中；Git 仅提交报告与本账本，保留原计划文件名 / 摘要。代价：原字节需通过本地附件复核。
- Ruling: 按本轮 scoped plan 允许的阻塞证据提交完成本地交付，没有 upstream 也不推送 / 合并。代价：没有远端备份；T04 未开始。
- Final review: 一次独立只读审阅，无 Critical / Important / Minor，没有修正或二次复核。审阅者重读实际产物并重新解析现有结果，未重跑测试。其保留项均维持原生 / 目标门禁 / 其他环境未验证、真实迁移 / 实机 / 生产关口范围外、历史操作全面证明不成立；暂存与提交终态由作者随后实查，不把本轮解释为产品验收。

## Round 02 执行记录

计划：`docs/superpowers/plans/2026-10-23-round-02-storage-foundation.md`。实查起点为 `4ddd1530bb06f4faa8ac2f05cec0f440d2a453ee`，原 main 及隔离工作树均干净，无基线漂移。ZIP 四项清单哈希、独立附件和两份既有计划哈希均一致；仅导入 scoped plan。

| 步骤 | 状态与依赖 |
| --- | --- |
| R02-A 基线与映射 | 已核对十一模型／五接口及三类旧 DataStore；新鲜基线应用 100/100、Python 62/62、完整构建与 Lint 通过 |
| R02-B 编码 | 已实现并验证；24 项新增纯测试，含审阅修正；全部应用 124/124 |
| R02-C Room 候选 | 七表、真实生成 schema、browse／favorite 候选及低层 playback DAO 已编译；原生 close/reopen／SQL 约束仍阻塞，不接运行时 |
| R02-D 保留式导入 | reader／typed snapshot／无损 digest／staging＋batch 事务已实现；合成导入与真实事务故障仅有原生测试编译证据，执行阻塞 |
| R02-E 验证矩阵 | 应用 124/124、Python 62/62、debug／unsigned release／双 Lint／原生 APK 编译成功；34 项原生测试 0 执行，无已核实的一次性 emulator |
| R02-F 审阅与本地提交 | 整轮独立审阅及 Important 修正定向复核已完成；实现提交 49a2032，25 个候选文件与提交字节一致；实现／最终 index 扫描 209／210 文件，均 0 违规；本记录另做文档提交，不推送、不合并、不发布 |

- Ruling: 继续沿用 R01 对字母步骤的记录方式，保留 scoped plan 原字节，原始输出放在忽略的 `.local/evidence/round-02/`；不改写为技能脚本专用数字任务。代价：步骤状态需人工核对。
- Ruling: 用户常驻规则要求简体中文提交信息，优先于附件中 English commit messages 的一句要求。代价：附件该句不执行，代码注释仍用英文。
- Pre-flight: A 的真实契约是 B 的输入，B 的格式是 C 的输入，D 的 snapshot 和 C 的事务共同决定 E 的测试；没有新增公共接口，也不实现 PlaybackProgressStore.commit。
- Ruling: 独立审阅实证 UTF-8 替换会合并不同 String；JSON surrogate 转义、精确 UTF-16 BLOB 键及已有 batch 的完整行比较共同修正，四项新测试实际 RED → GREEN，原审阅者定向复核清除 Important。代价：首个未发布 schema 使用内部键编码，后续版本必须按该格式真实迁移，不能按 TEXT 身份猜写。
- Ruling: 原生目标不可用时仅编译已先写出的真实 Room／DataStore 测试，不以主机 codec 或 mocks 代替原生红绿。代价：34 项原生测试及存储／导入的原生验收保持阻塞，本轮只交付已通过可用检查的隔离候选。
- Final review: 一次整轮只读独立审阅及同一审阅者的 Important 修正复核；没有未解决 Critical／Important／Minor。原生执行、真实旧数据、断电、升级、运行时 cutover、T04 仲裁均不作通过裁定；最终 Gradle 与解析图以终态日志为准。

## Round 01 历史步骤

| 步骤 | 状态 | 证据 |
| --- | --- | --- |
| R01-A 输入、隔离、本地基线 | 本地部分已核实，设备/旧样本未验证 | [基线记录](../../verification/2026-10-23-round-01-baseline.md) |
| R01-B 数据值对象与单测 | 已实现并完成定向测试 | 最终 25/25；原 9 个输入校验断言及审阅新增总量断言先失败后通过 |
| R01-C 纯接口及迁移边界 | 已定义并编译 | 五个接口，无实现或运行绑定；[契约文档](../../architecture/tv-data-contracts.md) |
| R01-D 回归、审阅、本地提交 | 本轮本地交付已核实，停止于 Round 01 | 应用 100/100、脚本 62/62、两种构建及 Lint 成功；186 个暂存文件安全扫描无违规；保留本地分支 |

## 执行决定

- Ruling: 桌面工具无法识别子仓库后使用原生 Git worktree 隔离；保持同一基线与独立分支。代价：该工作区未登记为桌面托管附件，保留明确的相对位置。
- Ruling: 分轮计划使用 R01-A–D 标题，技能脚本要求数字 Task 标题；保留原计划不改写，以本记录对应步骤并保存测试原始证据。代价：不能依赖脚本自动识别步骤。
- Ruling: 按分轮计划暂不定义序列化格式；总计划的序列化要求留给后续明确存储格式时完成。代价：当前类型尚不能宣称可落盘或兼容迁移。

## 独立审阅裁定

审阅范围 `7f3e063..6a56515`。两个发现已在 `41bb688` 处理：已知总量矛盾有实际红绿测试；焦点回退文档已逐句对齐主规格 4.2。修复后完整应用单测为 100/100。没有第二次独立复审，亦无延后处理的 Minor。

- Ruling: 将审阅者标为 Minor 的回退文档冲突作为本轮契约一致性问题修正；否则后续实现可能依据错误规则跳离深列表。代价：这里只修正文档，真实恢复与 UI 仍须在 T08 验证。
- Ruling: 序列化和存储格式继续按已记录决定暂缓；当前没有编码器或格式消费者。代价：不提供持久化或兼容性保证。
- Ruling: 真实查询、分类、身份关联和资料去重仍留在 T05–T07；本轮只校验结果总量不能自相矛盾。代价：不提供真实筛选或关联正确性的运行证据。
- Ruling: 数据库事务、跨会话排序、取消竞态和实际持久性仅记录接口责任，不引入假实现。代价：这些运行行为未验收。
- Ruling: 不新增任意泛型负载的通用深层冻结机制；FieldEvidence 的现有资料字段只使用不可变 Int / FactIds，其他调用方仍必须遵守不可变 T 约束。代价：未来新增可变负载时必须重新设计或验证。
- Ruling: 保留负 Unix 时间戳、超过陈旧时长的位置、已知空语言集合的表示能力，不在值对象中臆测为非法。代价：未来显示与业务策略须正确处理，不能拿时间戳排序授权或把空语言直接归类。
- Ruling: UI、遥控器、播放、设备、迁移、签名和发布继续保持未验收，符合明确授权边界。代价：不能把本轮本地测试解释为产品可发布。
- Ruling: 审阅时报告中的“待审阅”是过程状态，最终记录在修复和回归后更新。代价：这些记录的更新不构成新的独立复审。

最终代码提交为 `41bb6885f5945159d825b53eef2cecb59d623865`，之前两笔为计划导入 `f0afef7`、契约实现 `6a56515`。结项记录另行本地提交，最终 HEAD 在用户答复中记录。原始 main 保持基线；没有执行 T03 或后续任务。

## G01–G12

| 目标 | 状态与本轮边界 |
| --- | --- |
| G01 中文、无品牌 | 未实现 |
| G02 删除诊断 | 未实现，旧诊断保持原样 |
| G03 全新视觉、原创图标、状态 | R13 候选测试路径第一组切片（10 枚原创图标、token 复用、六态首页、五目的地中文导航）原生验证；完整视觉盘点与生产激活仍开放 |
| G04 遥控器、焦点、动效 | R13 首页沿 R11 焦点管线（纯方向键可达、迟到投递不抢焦、防双导航）；T11 全局遥控与动效系统仍未完成 |
| G05 页面、筛选、焦点、进度持久化 | 快照契约与候选存储／编码已实现；R04 的 34 项候选存储原生检查在专用 ARM64 AVD 通过，未接运行时或验证用户体验 |
| G06 续播、历史、收藏 | Round12候选原生三列表、独立动作、真实播放及恢复已验证；生产与完整验收仍开放 |
| G07 多源统一数据、稳定身份 | ID 与字段依据类型已实现和单测，无身份关联算法 |
| G08 严格分类、国家、类型、年份 | 字段与年份边界校验已实现和单测，无查询引擎 |
| G09 一键订阅维护 | 状态契约已实现和单测，默认目录未接通 |
| G10 性能、低配置、升级保数据 | 未验证 |
| G11 筛选组合、备份、睡眠等增强 | 未实现 |
| G12 字幕音轨、追更、外部资料增强 | 仅区分版本音轨元数据，增强功能未实现 |

## T01–T16

| 任务 | 状态 |
| --- | --- |
| T01 基线与隔离 | 本地隔离、原有测试/构建/Lint 已核实；旧样本、设备矩阵等未完成 |
| T02 规格冻结 | 数据契约与持久化编码切片已通过纯测试；R13 交付第一组原创图标（可编辑 SVG+原生矢量）与语义 token 复用；完整图标盘点、页面状态设计与 M01 仍开放 |
| T03 数据库与迁移 | Round 02 候选与 synthetic staging 保持；R04 在专用 ARM64 AVD 实际执行 34/34 通过；真实旧数据、runtime cutover 及整个 T03／M02 未完成 |
| T04 播放检查点 | Round 05 候选策略／真实持久仲裁与合成 schema 迁移已测试；149 JVM／61 native 全量通过；生产播放器未接入，整个 T04 未验收 |
| T05 身份与字段标准化 | 未开始 |
| T06 来源能力与订阅 | Round09合成候选切片及两个前轮覆盖缺口已测试；来源能力/独立有界分页/授权订阅候选已验证，普通路径未接入，完整T06/M03及A14/A15/A17/A18/A21未验收 |
| T07 严格多维查询 | 未开始 |
| T08 导航快照与焦点恢复 | Round11候选合成切片已有原生/真实Room/独立进程证据；完整T08及M02/M04仍未验收 |
| T09 续播、收藏、历史与播放器 | Round12测试授权原生个人记录切片已验证；普通生产接入与完整T09/M04仍未验收 |
| T10 新界面 | R13 首页 + R14 片库 + R15 详情 + R16 播放器控制四层候选测试路径已就位（各自原生切片验证，见 docs/verification/ 各轮报告）；设置重设计与生产激活仍开放 |
| T11 遥控器与动效 | R16 候选测试路径播放器控制切片：物理按键周期状态机（短/长 OK、方向重复、丢失/孤儿/取消全形态）、时间轴预览/确认/取消、线路/集数选择器、逐层 Back、空闲隐藏与禁用真话，47/47 原生 + 41 JVM 用例；完整全局 T11（物理设备、菜单键、全局焦点模型）仍未验收 |
| T12 设置、中文化、删除诊断 | 未开始 |
| T13 增强能力 | 未开始 |
| T14 性能与耐久 | 未开始 |
| T15 回归与升级 | 未开始 |
| T16 发布安全 | 未开始 |

## A01–A24

所有 A 场景均未验收。值对象测试只可能提供局部证据，不证明未来存储、查询或设备行为。

| 场景 | 状态与本轮可能关联的证据 |
| --- | --- |
| A01 中文界面 | 未验收 |
| A02 原创图标 | 未验收 |
| A03 基础遥控路径 | 未验收；R16 在一次性候选路径以真实 D-pad/OK/Back 覆盖播放器层五键全操作（非物理设备证据） |
| A04 详情返回深位置 | 未验收；仅快照字段 |
| A05 重启、回收、强停恢复 | 未验收 |
| A06 不同集独立进度 | 未验收；仅作品/剧集 ID |
| A07 快退保存 | 未验收；R16 在候选路径验证后退 seek 与显式归零如实提交（不并合最大位置），生产行为未验收 |
| A08 零位置、旧回调 | 未验收；仅零位置及非负序号约束 |
| A09 最新集与预加载 | 未验收 |
| A10 收藏持久化、失败 | 未验收；仅提交结果及幂等接口 |
| A11 删除记录与清缓存 | 未验收 |
| A12 老年份与区间 | 未验收；仅包含边界及非法范围拒绝 |
| A13 语言、国家、音轨 | 未验收；仅独立证据模型 |
| A14 空页与后续页 | 未验收；仅 partial/complete 值区分 |
| A15 来源失败、重复、忽略参数 | 未验收；仅失败来源字段 |
| A16 身份关联与冲突 | 未验收；仅不透明 ID 和冲突值 |
| A17 目录空与刷新失败 | 未验收；仅订阅状态区分 |
| A18 来源换域、停用 | 未验收 |
| A19 刷新、面板与焦点 | 未验收 |
| A20 迁移中断与重复执行 | 未验收；有候选与合成／故障原生测试编译证据，原生执行仍阻塞 |
| A21 播放、后台与低资源 | 未验收 |
| A22 长字、缺图等界面状态 | 未验收；R16 覆盖播放器层长中文线路/剧集标签、禁用/错误/缓冲状态的真实截图 |
| A23 生产包安全与清理 | 未验收；仅当前 Git index 安全检查 |
| A24 性能与耐久 | 未验收 |
