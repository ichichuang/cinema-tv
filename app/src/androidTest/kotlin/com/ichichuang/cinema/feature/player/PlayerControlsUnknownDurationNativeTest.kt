package com.ichichuang.cinema.feature.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * R17 ST3 goal B — U05 未知时长（loopback 直播式流）。
 *
 * 用一个只回环、只提供仓库已有 `live.aac` fixture 字节、有界资源且确定性关闭的
 * 测试专用 HTTP server，构造一个指向 `http://127.0.0.1:<port>/live` 的 ExoPlayer，
 * 断言真实未知时长行为：
 *   - 播放器状态到达 [Player.STATE_READY]（有界等待，超时失败并附当前状态）；
 *   - [Player.playerError] 为 null（不是靠错误/失败态冒充“未知时长”）；
 *   - [Player.duration] 为真实 [C.TIME_UNSET]（无 Content-Length 的常率流不得推断时长）；
 *   - 可用命令里不含绝对 seek（[Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM]）；
 *   - 控制器对未知时长的如实投影：时间轴禁用、无法建立预览、OK 不产生 seek 命令。
 *
 * 线程边界（关键）：Media3 [ExoPlayer] 的创建、`setMediaItem/prepare/release` 以及读取
 * `playbackState/playerError/duration/isCommandAvailable` 全部必须在应用主线程进行，否则
 * Media3 会抛 `IllegalStateException("Player is accessed on the wrong thread")`。本测试用
 * `InstrumentationRegistry.getInstrumentation().runOnMainSync { ... }` 在主线程执行，读取值
 * 经 AtomicReference/AtomicInteger/AtomicLong 带出；有界等待 READY 超时必须在主线程再次
 * 取样当前 state/playerError 并失败，绝不“没抛异常”当证据。
 *
 * 架构边界（如实披露）：生产播放管线经 `ProbeMediaFile.requirePrivate` 只接受私有目录
 * 内的 `file://` 媒体，loopback `http://` URL 无法通过该门禁，因此本测试**直接构造**
 * ExoPlayer（不经 `probe.select`），它绝不弱化 `requirePrivate`、不新增生产网络例外。
 * 相应的，「真实 Compose 语义节点上的中文“时长未知”文案」与「真实 `player.seekTo` 零
 * 调用」只能由经完整 Activity/Runtime 管线的路径验证；本测试在 Media3 + 控制器纯逻辑层
 * 验证其上游事实（duration 为 TIME_UNSET、命令缺失、控制器禁用时间轴/无预览/无 SeekTo
 * 命令），中文文案渲染分支由 `TimelineRow` 的 `duration==null -> "时长未知"` 覆盖
 * （见 TvPlayerControlsScreen.kt），故中文文案环节如实标 UNVERIFIED。
 *
 * box 无模拟器 → 本测试仅编译（UNVERIFIED）；矩阵状态如实标 UNVERIFIED（见验证报告）。
 */
class PlayerControlsUnknownDurationNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()

    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private val stopped = AtomicBoolean(false)
    private val servedRequests = AtomicInteger(0)
    private var player: ExoPlayer? = null

    @Before fun startLoopbackServer() {
        // 从仪器上下文一次性读入仓库已有 live.aac fixture 字节（有界，不在测试里重新合成媒体）。
        val aacBytes = instrumentation.context.assets.open("round16/live.aac").use { it.readBytes() }
        require(aacBytes.size > 50_000) { "live.aac fixture 缺失或过小，无法验证未知时长" }
        // 只绑定回环地址；每个测试用例一个随机端口，随测试确定性关闭。
        serverSocket = ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        serverThread = Thread({ acceptLoop(serverSocket!!, aacBytes) }, "cinema-u05-loopback").apply {
            isDaemon = true; start()
        }
    }

    @After fun stopAll() {
        stopped.set(true)
        // 确定性关闭：先在主线程 release 播放器，再关闭 socket 并 join 服务线程（有界超时），
        // 避免遗留 socket/线程资源。release 必须在主线程（与创建同线程）。
        val p = player
        if (p != null) {
            instrumentation.runOnMainSync { p.release() }
            player = null
        }
        runCatching { serverSocket?.close() }
        serverThread?.let { t ->
            try { t.join(2_000) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
        serverThread = null; serverSocket = null
    }

    private fun url(): String {
        val port = serverSocket!!.localPort
        return "http://127.0.0.1:$port/live"
    }

    private fun acceptLoop(server: ServerSocket, aacBytes: ByteArray) {
        while (!server.isClosed && !stopped.get()) {
            val client = runCatching { server.accept() }.getOrNull() ?: break
            servedRequests.incrementAndGet()
            runCatching { serve(client, aacBytes) }
            runCatching { client.close() }
        }
    }

    /**
     * 有界、无时长标记的直播式响应：`HTTP/1.1 200 OK` + `audio/aac` + `Connection: close`，
     * 不写 Content-Length、也不写 Transfer-Encoding，响应体是仓库已有 `live.aac` fixture 的
     * 原始字节（复用，不新增媒体文件）。连接关闭式结束，Media3 无法从响应头推断时长。
     *
     * 关键：不得写 `Transfer-Encoding: chunked` —— 响应体是原始 ADTS 字节而非分块帧，写了
     * 该头客户端会因 `Bogus chunk size` 读失败，导致 playerError 非空、到不了 READY。
     */
    private fun serve(client: Socket, aacBytes: ByteArray) {
        client.soTimeout = 8000
        val input = client.getInputStream()
        // 读请求头直到 \r\n\r\n（有界，避免无界阻塞）。
        val header = java.io.ByteArrayOutputStream()
        var tail = 0
        while (header.size() < 8192) {
            val b = input.read()
            if (b < 0) return
            header.write(b)
            tail = ((tail shl 8) or b) and 0xFFFFFFFF.toInt()
            if (tail == 0x0D0A0D0A.toInt()) break // \r\n\r\n
        }
        val out = BufferedOutputStream(client.getOutputStream())
        // 无 Content-Length、无 Transfer-Encoding：连接关闭式传输，Media3 无法从响应头推断时长。
        out.write(("HTTP/1.1 200 OK\r\n" +
            "Content-Type: audio/aac\r\n" +
            "Connection: close\r\n\r\n").toByteArray(Charsets.ISO_8859_1))
        out.flush()
        var sent = 0
        while (sent < MAX_BYTES && !stopped.get()) {
            out.write(aacBytes)
            out.flush()
            sent += aacBytes.size
            Thread.sleep(10)
        }
    }

    @Test fun loopback_live_stream_reports_unknown_duration_and_no_seek() {
        // 直接构造指向回环流、关闭常率 seek 推断的 ExoPlayer（与生产 DefaultExtractorsFactory 一致）。
        // 创建与后续 setMediaItem/prepare/release 都必须在主线程，否则 Media3 抛 wrong-thread。
        val p = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context,
                DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(false)))
            .build()
        player = p
        instrumentation.runOnMainSync {
            p.setMediaItem(MediaItem.fromUri(url()))
            p.prepare()
        }

        // 断言 1：有界等待播放器真正到达 READY；超时必须在主线程取样当前 state/playerError 并失败。
        val state = AtomicInteger(Player.STATE_IDLE)
        val error = AtomicReference<PlaybackException?>(null)
        val duration = AtomicLong(C.TIME_UNSET)
        val seekAvailable = AtomicBoolean(true)
        val end = android.os.SystemClock.uptimeMillis() + 15_000
        var reachedReady = false
        while (android.os.SystemClock.uptimeMillis() < end && !reachedReady) {
            instrumentation.runOnMainSync {
                state.set(p.playbackState)
                error.set(p.playerError)
                reachedReady = p.playbackState == Player.STATE_READY ||
                    p.playbackState == Player.STATE_ENDED
            }
            if (!reachedReady) Thread.sleep(50)
        }
        assertTrue(
            "回环流必须到达 READY（实际 state=${state.get()} error=${error.get()} " +
                "requests=${servedRequests.get()}）", state.get() == Player.STATE_READY)

        // 断言 2：不是靠错误/失败态冒充“未知时长”。
        assertNull("未知时长不是播放失败", error.get())

        // 断言 3/4：duration 与命令可用性必须在主线程读取，用真实播放器值驱动后续控制器投影。
        instrumentation.runOnMainSync {
            duration.set(p.duration)
            seekAvailable.set(p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        }
        assertEquals("无 Content-Length 常率流必须如实 C.TIME_UNSET", C.TIME_UNSET, duration.get())
        assertFalse("未知时长不得宣称可拖动", seekAvailable.get())

        // 断言 5–7：真实控制器投影，duration/seek 用断言 3/4 得到的真实播放器值（非测试自写常量）。
        // 若时长被错误推断为已知，`durationMs` 非空 → controlEnabled("时间轴") 变 true、
        // beginPreview 返回 true、confirm 返回 SeekTo，本测试即失败；反之这些恒为禁用/无预览/None。
        val controller = PlayerControlsController()
        controller.install(
            durationMs = duration.get().takeIf { it != C.TIME_UNSET },
            seekAvailable = seekAvailable.get(),
            lines = emptyList(), episodes = emptyList(),
            currentVariantId = "", currentEpisodeId = "", positionMs = 0L)
        controller.revealByUser()
        assertFalse("未知时长禁用时间轴", controller.controlEnabled("时间轴"))
        assertFalse("未知时长不可建立预览", controller.beginPreview(0L))
        controller.overrideFocus("时间轴") // 直接请求聚焦禁用项（模拟漂移/迟到恢复）
        assertEquals("禁用时间轴 OK 不产生 seek 命令", PlayerControlCommand.None, controller.confirm())
    }

    private companion object {
        const val MAX_BYTES = 256 * 1024
    }
}
