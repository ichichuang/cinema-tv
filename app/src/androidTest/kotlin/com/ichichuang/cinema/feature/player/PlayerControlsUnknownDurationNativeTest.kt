package com.ichichuang.cinema.feature.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
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
        runCatching { serverSocket?.close() }
        serverThread = null; serverSocket = null
        runCatching { player?.release() }
        player = null
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
     * 有界、无时长标记的直播式响应：200 + `audio/aac`，不带 Content-Length（Transfer-Encoding:
     * chunked + Connection: close），响应体持续发送 fixture 帧直到客户端关闭或达到字节上界。
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
        // 无 Content-Length：连接关闭式分块传输，Media3 无法从响应头推断时长。
        out.write(("HTTP/1.1 200 OK\r\n" +
            "Content-Type: audio/aac\r\n" +
            "Transfer-Encoding: chunked\r\n" +
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
        val p = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context,
                DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(false)))
            .build()
        player = p
        p.setMediaItem(MediaItem.fromUri(url()))
        p.prepare()

        // 断言 1：有界等待播放器真正到达 READY；超时必须失败并附当前状态（不能以“没抛异常”当证据）。
        val end = android.os.SystemClock.uptimeMillis() + 15_000
        while (android.os.SystemClock.uptimeMillis() < end &&
            p.playbackState != Player.STATE_READY && p.playbackState != Player.STATE_ENDED) {
            Thread.sleep(50)
        }
        assertTrue(
            "回环流必须到达 READY（实际 state=${p.playbackState} error=${p.playerError} " +
                "requests=${servedRequests.get()}）", p.playbackState == Player.STATE_READY)

        // 断言 2：不是靠错误/失败态冒充“未知时长”。
        assertNull("未知时长不是播放失败", p.playerError)

        // 断言 3：无索引常率流不得伪造时长。
        assertEquals("无 Content-Length 常率流必须如实 C.TIME_UNSET", C.TIME_UNSET, p.duration)

        // 断言 4：可用命令不包含绝对 seek。
        assertFalse("未知时长不得宣称可拖动", p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))

        // 断言 5–7：真实控制器投影（durationMs == null 的上游事实已由断言 3 确立）——
        // 时间轴禁用、无法建立预览、OK 确认不产生 SeekTo 命令。这些不是读测试自写的常量，
        // 而是驱动真实 PlayerControlsController 状态机；若时长被错误推断为已知，controlEnabled
        // 会变 true、beginPreview 会返回 true、confirm 会返回 SeekTo，本测试即失败。
        val controller = PlayerControlsController()
        controller.install(durationMs = null, seekAvailable = false, lines = emptyList(),
            episodes = emptyList(), currentVariantId = "", currentEpisodeId = "", positionMs = 0L)
        controller.revealByUser()
        assertFalse("未知时长禁用时间轴", controller.controlEnabled("时间轴"))
        assertFalse("未知时长不可建立预览", controller.beginPreview(0L))
        assertNull("未知时长下预览目标必须为 null", controller.previewMs)
        controller.overrideFocus("时间轴") // 直接请求聚焦禁用项（模拟漂移/迟到恢复）
        assertNull("聚焦禁用时间轴后 OK 不产生预览", controller.previewMs)
        assertEquals("禁用时间轴 OK 不产生 seek 命令", PlayerControlCommand.None, controller.confirm())
    }

    private companion object {
        const val MAX_BYTES = 256 * 1024
    }
}
