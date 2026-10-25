# 候选个人记录：收藏、历史与继续观看

Round 12 仅接入自有合成 profile 的测试授权原生路径。普通 Debug、Release、旧 HomeViewModel／DataStore 和正式导航保持原有入口；不表示完整 T09、M04、电视或真实用户验收。执行与逐项证据见 `docs/verification/2026-10-25-round-12-personal-library.md`。

## 入口与写入权限

`cinema.personalLibraryProbe` 默认 false；ON 仅允许完整 `:app:assembleDebug :app:assembleDebugAndroidTest` 根任务，并要求 `cinema.browseRestoreProbe`、`cinema.playbackRoomProbe`、`cinema.storageTestArm64` 同时启用。Release、bundle、publish、install、签名和不完整配对在执行前拒绝。Release 的 BuildConfig 常量为 false。

MainActivity 在打开候选数据库前核对私有 R12 授权标记，随后沿用 R11 `DebugBrowseProbeGate.withOwnedProfile` 和 R07 同签名 app/test、API30 ranchu ARM64、明确同意、自有 profile、旧记录与交接状态检查。整个候选数据库生命周期持有已有排他 writer lease；不启用旧数据迁移或第二个 DataStore writer。普通 OFF 不创建个人运行时、媒体或数据库。

## 实际存储与投影

既有 Room schema 1–5 保留原字节，显式 5→6 迁移只新增三表，工厂保留全部旧迁移，无 destructive fallback。

| 新表 | 持久事实 |
| --- | --- |
| personal_library_state | 每个原 opaque workId 的隐藏／删除 generation 水位、收藏动作顺序、收藏修订和已撤销修订 |
| confirmed_plays | 真实播放确认；session generation 唯一，SQLite 自增 eventOrder 排序；不依赖系统时间 |
| personal_library_cards | 非缓存的最小作品标题与原来源标签；与原 work reference 绑定，缓存淘汰不删除 |

`RoomPersonalLibraryRepository` 在真实 Room 事务中读取一致的列表，使用 Room invalidation Flow 更新界面。收藏由既有 `RoomFavoritesStore.setFavorite` 唯一写入；仅实际关系变化分配动作修订，重复 set 不改变排序。没有基于标题、年份或位置分配身份，也不重映射来源关联／拆分后的用户引用。

历史只由既有 Media3 桥的 `READY && isPlaying` 确认产生，prepare、预加载、浏览和详情不算观看。确认独立于 R05 的 5000ms 续播提升：短暂但真实的播放可以进入历史，历史卡独立携带 session、episode、variant，即使尚无新检查点。最近历史按持久 eventOrder 排序，相同 session 重试不重复、不重排。

继续观看使用已有已确认的逐作品 resume，而非历史列表或最大媒体位置。R05 新会话普通提升仍需累计实际 playing 5000ms，真实短片 ended 的既有例外不变；新集误触不取代旧指针，初始化零值／未知时长／重试沿用已有桥和仲裁。已存在的 v5 合格检查点可作为续播依据，不凭空回填真实观看历史；显式隐藏或删除后的重新出现还要求更新的真实播放确认与合格检查点。完成状态仅表示本集，不推定全剧终结。

## 三个用户动作

| 动作 | 改变 | 保留 | 重试边界 |
| --- | --- | --- | --- |
| 移出继续观看 | 持久隐藏水位，投影移除续播卡 | 历史、收藏、每集每版本检查点、目录／暂存 | 请求先捕获 generation 范围；迟到旧回调与同一请求重试不能重显，也不能隐藏后来合法新播放 |
| 删除观看记录 | 确认范围内该作品历史、resume、各集／版本检查点及删除抑制水位 | 收藏、其他作品、原身份／来源、browse、session 仲裁证据、legacy staging | 固定请求 generation；同一请求重试不扩大范围，新会话不被旧请求删除 |
| 取消收藏 | 仅删除收藏关系并分配收藏动作修订 | 历史、进度、resume、来源与暂存 | 准备动作绑定原 favoriteOrder；回执丢失可核实同次提交，不能借后来的用户动作取得撤销权 |

删除确认的 Back 只取消对话框。删除通过 generation 水位在 `RoomPlaybackProgressStore` 的幂等快路径前拒绝旧 session；session／record-order 留作仲裁证据。旧 importer 仍只保留 staging，已激活 handoff 重试不重放被删除的 checkpoint。新实际播放需要新的合法 session，不绕过旧回调拒绝规则。

撤销取消收藏是一个真实逆事务，绑定持久收藏修订。界面给出 10 秒单调时间窗口；更晚的明确动作使旧撤销失效，同一已提交撤销重试幂等。SQL／IO 失败不显示已提交成功，取消向上传播；事务后确认丢失表示结果尚未确认，不能宣称数据回滚。重试保留原动作目标与范围，读取实际存储核实。

## 原生导航与媒体

复用原 R11 CinemaNavHost／BrowseProbeRuntime，增加独立 CONTINUE 顶层 context，DETAIL／PLAYER 仍使用原有有界返回链。收藏／历史／继续观看由真实 Room 列表驱动；仅片库保留原测试目录查询。列表按稳定 workId 定位，动作后失去当前项时选择实际合法邻居；用户按键取消迟到恢复。相同个人投影的 invalidation 不制造新布局代次，实际投影变化只推进已有有效恢复请求，不能复活被用户撤销的请求。

详情／播放器动作使用中文且可通过方向键、确认、返回访问；滚动容器配合完整动作行的 BringIntoView 请求，在焦点／错误文字／实际布局变化后保持撤销等末尾动作完整可见，不依靠裁切后的 bounds 证明。页面恢复与播放器准备保持暂停，实际媒体目标与已确认 episode／variant／position 对齐；重建 PLAYER 时请求实际存在的播放按钮焦点。未知来源仍显示本地身份及不可播说明，不声称已下载或网络成功。

未知版本从详情、继续观看或保存的 PLAYER 进入时，都转到既有详情的明确选择动作，保留原顶层、查询、作品和返回来源。明确选择优先匹配已确认的相同 opaque episode，只将尚未核实的版本时间轴位置置 0 并暂停；没有该集时动作文字明确指出实际回退集，不自动映射旧毫秒值。

同进程删除当前作品的观看记录成功后，先取消并等待旧 probe 队列退出，清除旧 media item，再在相同数据库与 player 上建立原 probe 的新会话协调器；旧 session 仍被拒绝，新实际播放可以成立，不通过绕过保存屏障恢复。

媒体只读取 instrumentation 写入私有文件的完整身份映射，沿用 `ProbeMediaFile` 路径校验和既有 RoomPlaybackProbe → PlaybackRuntimeBridge → R05 coordinator。共享 browse 数据库的 probe 不关闭 lease 所有的数据库；不会建立第二套进度仲裁器。真实来源、跨版本时间轴等价、物理遥控器、API24／ARMv7、真实用户升级和任意断电仍需独立验收。

API 语义参考：[Media3 播放事件](https://developer.android.com/media/media3/exoplayer/listening-to-player-events)、[Room 数据库迁移](https://developer.android.com/training/data-storage/room/migrating-db-versions)。本轮仍使用仓库锁定版本，实际行为与迁移结果以保存的原生执行证据为准，没有依据外部页面升级依赖。
