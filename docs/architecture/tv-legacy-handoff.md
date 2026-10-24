# 隔离旧记录交接

本轮仅实现测试自有合成 profile 的交接演练。普通 Debug／Release、生产入口与 Koin 仍使用旧存储；不构成真实用户升级或 T03／T04 整体验收。

## 实际 ownership 与限定接口

| 单例文件 | 读写者 | 本轮边界 |
| --- | --- | --- |
| cinema_progress | PlaybackProgress；PlayerViewModel 的暂停、心跳、切集／结束、onCleared；HomeViewModel 只读 | 既有单例捕获，所有 save／clear 等待同一写屏障 |
| cinema_browse | DataStoreBrowseStore；HomeViewModel 的防抖 save | 同一写屏障，保留原始 ID 和 index |
| cinema_filters | DataStoreFilterStore；HomeViewModel 的筛选 save | 同一写屏障，空串与 absent 不合并 |
| cinema_settings | CinemaSettings；SettingsViewModel、SourceCatalog、PlayerViewModel 步长 | 保存来源／类型原值；测试交接期间冻结配置写入 |
| Room v2 | 候选 store、Round 06 测试探针 | 无生产 DI；复用既有 Media3 bridge 和 generation／sequence |

R07 交接不启动 Home／Settings 页面。已保存 browse／filter／来源仍可从完整保留的旧 API 读取，并原样 staging；激活后这些旧 API 的修改显式拒绝，不假装已提供新的生产配置 UI。测试播放器的进度读写只选 Room。

`HandoffAuthorization.allows()` 是独立、默认关闭的授权判断：Probe 编译属性、Debug、同签名测试 APK、独立私有迁移同意、预先声明的自有 profile、专用 API30 ARM64 emulator 全部成立才允许；不改变 Round 06 对未知旧文件的拒绝。

限定内部接口：`LegacyHandoffController.prepare(manifest)` 返回保存和审计结果；`activate(manifest, digest)` 在同一旧 writer 屏障内重新捕获、核验 staging 并提交；`writer()` 从真实 Room marker 选择 Legacy／Room／Blocked；`room(action)` 每次检查授权与 ACTIVE 后执行候选读写。类型沿用原 PlaybackTarget／PlaybackSnapshot／CommitResult，不增加第二套进度模型。

旧 source-qualified 进度键实际是 `wp_来源#movieId`，不是附件示例中的任意分隔格式。只接受显式完整目录映射，不使用 bare ID、标题、URL、索引或时间猜 canonical ID；索引仅在已声明的精确 fixture 目录内校验。任何用户可见旧进度不能关联时，保留 staging 并拒绝激活。

schema v2 没有持久交接 authority，需新增单独状态表与显式 2→3 migration；原 1／2 schema 不改。最终审计、事务／取消及原生证据见本轮验证报告。


## 持久状态与原子边界

真实表 `legacy_handoff_state` 的单个 profile 行包含阶段、importVersion、contentDigest、mappingDigest 和计数，并用外键指向原始保留批次。`prepare` 在写屏障内捕获四个既有单例、保存完整 typed staging，再持久验证结果；staged／verified 都不转移 writer。激活前在同一屏障内重新捕获并比摘要、重读每条 staging 与 accounting；旧内容或目录变化均拒绝。每个旧进度必须有精确映射，未解析／损坏记录完整保留并阻止激活，不从一条旧坐标生成逐集历史。

激活先持久写入 app-private AtomicFile 意图，再在同一 Room 事务内写入关联后的初始检查点、会话／续播指针和 `room_active` 行。仅事务真实返回后才承诺提交；提交后的确认丢失不会撤销已提交事实，重试只确认 ACTIVE，绝不重放旧点覆盖较新的 Room 位置。事务内取消／SQL-full 异常回滚全部候选进度和 activation；staging 仍保留。时间戳／最大位置不参与新检查点裁决，沿用原 generation／sequence。

两个存储引擎之间没有共享事务。意图只表示 Room **可能已提交**，不是成功回执。数据库可读且 staged／verified 时仍选 Legacy；ACTIVE 时只选 Room。意图存在但数据库缺失、ACTIVE 行与意图不一致、staging 摘要损坏、授权缺失均选 Blocked，不创建空库或退回旧写入。若意图写入后、激活前数据库丢失，也保守阻止写入，因为无法核验实际提交结果；不宣称这个窗口仍可无条件回退。

测试 profile 的所有旧 `.edit` 等待同一互斥边界内的实际 DataStore IO。DataStore 1.2.1 的 actor 独立于调用方，取消 `updateData` 的等待不等于实际写盘已结束。因此已取得写权限的 edit 在 `NonCancellable` 中等待真实 acknowledgement，完成后检查原调用方上下文并传播取消；等待取得锁时仍可取消，不启动写入。捕获和激活持有同一边界，迟到的旧写入在锁内重新检查持久 authority。普通 profile 没有迁移标记时直接执行原 edit，不改变普通写入调度或取消语义。只读 source 配置仍来自完整保留的旧文件，本轮不提供新的 browse／filter／来源配置 writer。依据已核对的 [DataStore 1.2.1 源码](https://dl.google.com/dl/android/maven2/androidx/datastore/datastore-core/1.2.1/datastore-core-1.2.1-sources.jar)，原生测试在实际 Preferences `writeScope` 内阻塞 IO 后取消调用方，验证捕获必须继续等待。

## 运行时门禁矩阵

| 场景 | 实际选择 |
| --- | --- |
| 普通 Debug／Release，Probe OFF，无本轮 profile／意图 | 原 Legacy；不开 Room、不迁移 |
| Probe ON，未知旧文件或没有独立迁移同意 | 原 Round 06 拒绝规则；不放宽 |
| 自有合成 profile、同签名测试 APK、独立同意与专用 API30 ARM64 环境 | 可以捕获／staging／审计，仍为 Legacy |
| 旧内容变化、未解析身份、损坏条目或目录／时间轴不一致 | 保留 raw staging，拒绝激活 |
| 审计通过、重新捕获一致、真实激活事务成功 | 仅 Room 进度读写；拒绝所有旧 writer |
| 激活后 Probe OFF、授权撤销、缺库／缺意图或数据损坏 | Blocked；不退回旧记录 |
| 已激活的同批次重试 | 核实已提交状态，不重复导入或覆盖较新的 Room 点 |

AVD 的唯一所有权、serial、PID／启动命令、boot ID、qemu、API／ABI 和测试包清单由宿主逐安装／执行前独立核对；应用内的镜像／签名／私有 profile token 检查不能代替宿主门禁。

测试仓库显式提供 `HandoffPlaybackProbeCatalog`。目录指纹把原 MovieDetail、完整显式 target／坐标及实际 app-private WAV 字节绑定到审计元数据；同 URI／episode／variant 的媒体内容变化也拒绝。`HandoffManifest` 另接收可信 fixture 的可重读证明函数，缺少该函数不能获得 verified readiness；prepare 与激活事务内、写入任何初始检查点／会话前，均重新读取实际目录和媒体，而不是仅比较调用方保存的 revision 字符串。文件系统和 Room 没有共享事务，本轮证明受控私有 fixture 的边界检查，不宣称可抵御同时恶意改写文件。

播放器 start、切集、retry 均重验完整目录；切集／retry 在原串行队列内、旧 checkpoint／新会话／媒体重开副作用前核验。指纹只验证声明的 fixture，不从标题、索引、路径或媒体推导 canonical ID。Media3 仍通过原 Round 06 listener／queue／bridge 和原 store 提交；运行中撤销授权返回真实 Failed 与原有“未保存”提示，不启动旧 writer。同步 ViewModel 构造只在明确的测试 profile 内执行有五秒上限的 IO authority 判断，普通路径不进入该判断。
