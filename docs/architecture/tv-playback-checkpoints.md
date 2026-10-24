# 候选播放检查点

Round 05 仅实现 T04 的候选切片，不接入 Koin、Application、导航、PlayerViewModel 或旧 PlaybackProgress。现有播放器的问题在运行时仍待修复，本文不构成产品验收。

| 实际契约 | 候选职责 |
| --- | --- |
| domain/model/userdata/PlaybackSnapshot.kt | 沿用 workId、可空 episodeId/variantId、sessionId、operationSequence、positionMs、可空 durationMs、watchState、capturedAt，不复制公共模型 |
| domain/repository/PlaybackProgressStore.kt | 原 suspend commit(snapshot): CommitResult，由 Room 候选实现 |
| data/local/entity/StorageEntities.kt | 原逐集、逐版本 payload 与七张旧表保持；新增会话、逐记录仲裁和作品续播表 |
| data/userdata/PlaybackCheckpointCoordinator.kt | 串行处理显式选集、有效观察、playing、pulse、seek、关键边界；注入单调时钟，不启动脱离调用方的协程 |
| data/userdata/RoomPlaybackProgressStore.kt | 真实事务分配代次、检查序号、保存逐集记录及独立续播指针 |

原 schema 1 没有持久会话代次与独立续播指针，需要真实 1→2 迁移。原七表和 schema 1 原字节保留，新增结构不推测旧记录的会话顺序或续播目标。

## 调度与观看政策

调用方在其拥有的生命周期协程内提供观察和 pulse，默认可沿用已有 500ms 观察频率；候选协调器以注入的 `PlaybackClock.elapsedRealtimeMs()` 累积实际 playing 时间，达到 5,000ms 才尝试周期保存。不用媒体位置差作计时，不保存单调钟值，也不启动后台游离任务。缓冲、暂停和 retry/loading 停止累计；存储失败不重置待保存时间。墙上时间仅写入 capturedAt，允许负值。

本轮明确的初始政策：新选中的集需要累计实际播放至少 5,000ms 才由普通检查点／离开事件提升为 IN_PROGRESS，并取代作品续播目标；4,999ms 即退出不提升。真实短片在有大于零的已播放时间且收到有效 ended 时可直接完成该集。该阈值是项目选择，仍需未来产品／实机评估；不是 Media3 保证。恢复已有记录本身不证明新一次播放，恢复协调器默认暂停。

初始化、loading、缓冲的 0 不覆盖最后有效位置；只有显式 `seek` 接受有意归零，保留合法快退及超过陈旧 duration 的非负位置。未知 duration 输入 null 时保留已有已知时长，首次未知继续为 null；上层须先将 Media3 TIME_UNSET 转成 null。显式未观看的 seek 可保存 NOT_STARTED，但不提升续播；也不能把已有实际观看记录降为 NOT_STARTED。完成仅改变当前集，不归零其他集，不推定全剧完结。已完成记录的有意重看 seek，或 ready 后的真实 playing，恢复进行中状态；仅恢复／ready 不改变完成状态，正常 ended 后离开仍保持 COMPLETED。

所有变更入口在首次挂起、等待 Mutex 前捕获事件的单调时间，再在同一 Mutex 内结算；时间基线只前进，不把慢存储造成的锁等待算成实际播放。正常持续播放跨越存储耗时仍累计。所有入口持有同一 Mutex，包含 suspend 提交和选集屏障。选集先保存旧集不可变快照并等待真实结果；失败或 RejectedStale 时返回该结果，保留旧上下文。显式 seek／短片完成等待确认状态独立于五秒门槛保留，只有 Committed 才清除；失败和取消后，离开／切集仍须重试，不能越过保存屏障。之后才分配新 session，身份／版本不完全相符的恢复快照显式拒绝。预加载不调用 select；带未选中 session 的 ready、playing、pulse、boundary 均被忽略。调用方必须为每次新的选中会话提供唯一 opaque sessionId，不能用片名、URL 或列表下标充当稳定身份。取消不被吞掉。

## 持久顺序与提交

| schema 2 新表 | 职责 |
| --- | --- |
| playback_sessions | INTEGER PRIMARY KEY AUTOINCREMENT 在事务内分配持久 generation；BLOB sessionId 唯一并绑定作品／集／版本，保存最后已接受 sequence 和完整 payload |
| playback_record_order | 每个 work/episode/variant 当前已选会话的 generation 下限；选中即更新，旧会话不能在新会话首个检查点前抢写同一集 |
| playback_resume | 每个作品一个合格续播快照及 generation，与逐集进度独立 |

beginSession 原子保存 reference、session 和记录下限。commit 使用同一个真实 Room withTransaction 检查绑定及 generation，再比较该会话 operationSequence。较低序号拒绝；同序号且完整 payload 与当前逐集行均相同，确认既有持久提交，返回 Committed；同序号不同字段拒绝。更高序号保存逐集 payload、会话序号及符合条件的续播指针，事务完成后才返回 Committed。负 capturedAt、opaque sessionId 字典序和位置大小都不参加裁决。

作品续播仅允许该作品最近一次显式选中会话提升；旧集的最终状态仍可保存在其独立行，但不能夺走较新会话的作品指针。新集未达到观看阈值时保留原续播快照。重复回执也不会重写续播。SQLite／I/O 异常映射为脱敏 storage_sql／storage_io，损坏记录为 storage_read；CancellationException 直接传播。测试注入点位于真实事务内及事务后，用于证明回滚和确认丢失，不是替代存储。提交后失去回执时，失败／取消不意味着数据未写入，使用同一快照幂等重试核实。

1→2 迁移只新增三表和索引；原七表、BLOB UTF-16 键及 payload 不改。`1.json` 原 SHA-256 为 `14ff4e2507925df8ef1cd34085868de900520f162dca1dd9cb3d57385f048959`；`2.json` 为真实 Room 导出。迁移测试从打包的原 `1.json` 创建全部旧表、identity 和 version，给七表各写合成记录，迁移前后逐字段、逐类型比较。旧进度没有可信持久代次，因此不虚构会话或续播指针。没有 destructive fallback。

## 未来 Media3 接入映射

| 实际旧代码／事件 | 候选接入要求 |
| --- | --- |
| PlayerViewModel 心跳以 pos-lastSavedMs > 10000 保存 | 独立 elapsed-time + pulse；20min 快退到 10min 后五秒仍触发，不等位置追上旧值 |
| onIsPlayingChanged(false) 立即调用旧 persistProgress | 先停止累计；根据 buffering、error、ended 或真实用户暂停分别处理，不能统一保存原始 0 |
| STATE_IDLE / BUFFERING、prepare、retry | loading；保留有效位置／时长，不把准备当观看 |
| STATE_READY 与真实 isPlaying=true | ready 接受有效观察，playing 开始实际计时；恢复仍需用户主动播放 |
| seekTo / onPositionDiscontinuity(SEEK) | 由用户意图及匹配 session 确认跳转后显式 seek，支持 0，不将所有 discontinuity 都当用户 seek |
| selectEpisode / selectLine | 等待旧集保存屏障，再分配新会话；不同版本未经时间轴核对不能直接沿用位置 |
| STATE_ENDED | 仅当前实际观看集完成；不归零，也不将来源当前最后一集当全剧完结 |
| pauseAndSave / 后台 / 离开 | 调用方仍存活时 await boundary；不能指望 onCleared 后再发已被取消的任务刷盘 |

依据 [Media3 事件说明](https://developer.android.com/media/media3/exoplayer/listening-to-player-events)、[Player 时长契约](https://developer.android.com/reference/androidx/media3/common/Player)、[Room 迁移说明](https://developer.android.com/training/data-storage/room/migrating-db-versions)及[协程取消指南](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)，本轮在线核查。没有构造或运行真实 Media3 播放器；确定性事件测试只证明上述候选边界。

T03 仍须真实旧 DataStore 保留与身份关联迁移、升级／回滚及无双写 cutover；T05 须提供可信稳定身份和版本时间轴关系；T09 须接入实际播放器、生命周期、遥控与页面反馈并做 TV 验证。本轮不满足这些生产前置条件。
