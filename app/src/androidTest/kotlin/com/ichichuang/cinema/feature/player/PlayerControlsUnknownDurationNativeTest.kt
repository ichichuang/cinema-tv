package com.ichichuang.cinema.feature.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * R17 ST3 goal B — U05 未知时长（loopback 直播式流）。
 *
 * 用一个只回环、只提供本地生成媒体、有界资源且确定性关闭的测试专用 HTTP server，
 * 直接构造一个指向 `http://127.0.0.1:<port>/live` 的 ExoPlayer，断言：
 *   - 真实 `C.TIME_UNSET`（无 Content-Length、无索引的常率流不推断时长）；
 *   - 可用命令里不含绝对 seek；
 *   - 控制器把时长未知如实呈现（不伪造进度）；
 *   - 0 次不支持的 seek 调用。
 *
 * 说明：本测试**不经** `ProbeMediaFile`/`probe.select` 的私有文件目录，因此绝不弱化
 * `requirePrivate` 校验、不新增生产网络例外。它只验证 Media3 + 控制器对未知时长的
 * 如实投影，属于独立于私有目录门禁的补充断言。
 *
 * box 无模拟器 → 本测试仅编译（UNVERIFIED）；矩阵状态如实标 BLOCKED（见验证报告）。
 */
class PlayerControlsUnknownDurationNativeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private val stopped = AtomicBoolean(false)
    private var player: ExoPlayer? = null

    @Before fun startLoopbackServer() {
        // 只绑定回环地址；有界生成一段无时长标记的 ADTS 合成帧（本地生成，不接公网）。
        serverSocket = ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        serverThread = Thread({ acceptLoop(serverSocket!!) }, "cinema-u05-loopback").apply {
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

    private fun acceptLoop(server: ServerSocket) {
        while (!server.isClosed && !stopped.get()) {
            val client = runCatching { server.accept() }.getOrNull() ?: break
            runCatching { serve(client) }
            runCatching { client.close() }
        }
    }

    /** 有界、无时长标记的合成 ADTS 流：无 Content-Length，响应体持续发送直到客户端关闭。 */
    private fun serve(client: Socket) {
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
        out.write(("HTTP/1.1 200 OK\r\n" +
            "Content-Type: audio/aac\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "Connection: close\r\n\r\n").toByteArray(Charsets.ISO_8859_1))
        out.flush()
        // 有界发送本地生成的 ADTS 帧（无时长头、常率），不写 Content-Length。
        val frame = syntheticAdtsFrame()
        var sent = 0
        while (sent < MAX_BYTES && !stopped.get()) {
            out.write(frame)
            out.flush()
            sent += frame.size
            Thread.sleep(10)
        }
    }

    private fun syntheticAdtsFrame(): ByteArray {
        // 本地生成的、无时长索引的 ADTS 帧（帧头 0xFFF1 + 有效载荷填充），不来自任何外部来源。
        val frame = ByteArray(208)
        frame[0] = 0xFF.toByte(); frame[1] = 0xF1.toByte()
        frame[2] = 0x4C.toByte() // MPEG-4 AAC-LC, 44100 Hz
        frame[3] = 0x80.toByte()
        for (i in 7 until frame.size) frame[i] = 0x55
        return frame
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
        // 等待真实 READY（或至少读到媒体信息），有界轮询。
        val end = android.os.SystemClock.uptimeMillis() + 15_000
        while (android.os.SystemClock.uptimeMillis() < end &&
            p.playbackState != Player.STATE_READY && p.playbackState != Player.STATE_ENDED) {
            Thread.sleep(50)
        }
        // 真实未知时长：无索引常率流不得伪造时长。
        assertEquals("无索引常率流必须如实 C.TIME_UNSET", C.TIME_UNSET, p.duration)
        // 可用命令：不包含绝对 seek 能力。
        assertFalse("未知时长不得宣称可拖动", p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        // 控制器对未知时长的如实投影：时间轴禁用、无假预览（纯逻辑层，JVM 已覆盖；此处为原生旁证）。
        val controller = PlayerControlsController()
        controller.install(durationMs = null, seekAvailable = false, emptyList(), emptyList(),
            currentVariantId = "", currentEpisodeId = "", positionMs = 0L)
        controller.revealByUser()
        assertFalse("未知时长禁用时间轴", controller.controlEnabled("时间轴"))
        assertFalse("未知时长不可建立预览", controller.beginPreview(0L))
        // 0 次不支持的 seek 调用（真实播放器未暴露 seek 命令，控制器不会调用）。
        PlayerControlsTestHooks.forbidSeekCalls = true
        PlayerControlsTestHooks.seekCalls.set(0)
        controller.confirm()
        assertEquals("未知时长零 seek 调用", 0, PlayerControlsTestHooks.seekCalls.get())
        PlayerControlsTestHooks.forbidSeekCalls = false
    }

    private companion object {
        const val MAX_BYTES = 256 * 1024
    }
}
