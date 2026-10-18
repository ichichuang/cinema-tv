package com.ichichuang.cinema.feature.settings

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

/** 手机点「保存」之后的结果：电视那边测通了没有 + 给手机看的人话 */
data class PhoneSaveResult(val ok: Boolean, val message: String)

/**
 * 手机扫码配置用的迷你 HTTP 服务。
 *
 * 为什么要有它：**电视上打字是灾难**（遥控器一格一格挪，输入框一聚焦就弹键盘）。
 * 所以 TV 端的设置页不再有任何输入框 —— 点「扫码配置」后电视在局域网上起一个
 * 小网页，手机上填好点保存，配置直接推到电视里。
 *
 * 设计上的几条克制：
 * - **只在配置浮层打开时运行**，关掉即停（平时不监听任何端口）
 * - 只接受**局域网 / 本机**的请求（公网地址一律 403）
 * - 只服务三个路径：`/`（H5 页面）、`/api/config`（读/写配置）、favicon（204 打发）
 * - 手写极简 HTTP/1.1：够用就行，不引任何服务端框架（电视内存紧张，也少一个依赖）
 *
 * 页面与接口同源 —— 手机上不存在跨域问题，不用 CORS 那套东西。
 */
class ConfigServer(
    private val onSave: suspend (apiBase: String, updateUrl: String) -> PhoneSaveResult,
    private val port: Int = PORT_START,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private var socket: ServerSocket? = null
    private var thread: Thread? = null

    @Volatile
    private var currentUrl: String? = null

    /** 起服务；成功返回给用户看的网址（含局域网 IP），失败返回 null */
    fun start(): String? {
        if (thread != null) return currentUrl

        val bound = (port until port + PORT_TRIES).firstNotNullOfOrNull { candidate ->
            runCatching { ServerSocket(candidate) }.getOrNull()
        } ?: return null

        socket = bound
        currentUrl = "http://${lanIpv4() ?: "127.0.0.1"}:${bound.localPort}/"
        thread = Thread({ acceptLoop(bound) }, "cinema-config-server").apply {
            isDaemon = true
            start()
        }
        return currentUrl
    }

    fun stop() {
        val closing = socket
        socket = null
        thread = null
        currentUrl = null
        runCatching { closing?.close() }
    }

    // ── 内部 ───────────────────────────────────────────────────

    private fun acceptLoop(serverSocket: ServerSocket) {
        while (!serverSocket.isClosed) {
            val client = runCatching { serverSocket.accept() }.getOrNull() ?: break
            Thread { handleQuietly(client) }.apply { isDaemon = true }.start()
        }
    }

    private fun handleQuietly(client: Socket) {
        runCatching { handle(client) }
        runCatching { client.close() }
    }

    private fun handle(client: Socket) {
        client.soTimeout = 8000
        val input = BufferedInputStream(client.getInputStream(), 4096)

        val head = readHead(input) ?: return
        val requestLine = head.first
        val headers = head.second

        val parts = requestLine.split(" ")
        val method = parts.getOrNull(0).orEmpty()
        val path = parts.getOrNull(1).orEmpty().substringBefore('?').ifEmpty { "/" }

        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (length in 1..MAX_BODY) readExact(input, length) else ByteArray(0)

        // 只认局域网（电视和手机在同一个 WiFi 下）；公网/奇怪来源一律挡掉
        if (!isLocal(client.inetAddress)) {
            respond(client, 403, TYPE_TEXT, "仅限局域网访问".toByteArray(Charsets.UTF_8))
            return
        }

        when {
            method == "GET" && (path == "/" || path == "/index.html") ->
                respond(client, 200, TYPE_HTML, htmlPage().toByteArray(Charsets.UTF_8))

            method == "GET" && path == "/api/config" -> {
                val payload = buildString {
                    append("{\"ok\":true")
                    append(",\"apiUrl\":"); append(jsonString(currentApiBase))
                    append(",\"updateUrl\":"); append(jsonString(currentUpdateUrl))
                    append('}')
                }
                respond(client, 200, TYPE_JSON, payload.toByteArray(Charsets.UTF_8))
            }

            method == "POST" && path == "/api/config" -> handleSave(client, body)

            method == "GET" && path == "/favicon.ico" ->
                respond(client, 204, TYPE_TEXT, ByteArray(0))

            else -> respond(client, 404, TYPE_TEXT, "not found".toByteArray(Charsets.UTF_8))
        }
    }

    private fun handleSave(client: Socket, body: ByteArray) {
        val text = String(body, Charsets.UTF_8)
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        if (root == null) {
            respondJson(client, PhoneSaveResult(false, "手机发来的数据看不懂，刷新页面再试一次"))
            return
        }
        val api = runCatching { root["apiUrl"]?.jsonPrimitive?.contentOrNull }
            .getOrNull().orEmpty().trim()
        val update = runCatching { root["updateUrl"]?.jsonPrimitive?.contentOrNull }
            .getOrNull().orEmpty().trim()

        if (api.isEmpty()) {
            respondJson(client, PhoneSaveResult(false, "接口地址不能为空"))
            return
        }
        if (!api.startsWith("http://") && !api.startsWith("https://") && api != "demo") {
            respondJson(client, PhoneSaveResult(false, "接口地址要以 http:// 或 https:// 开头（想先看界面就填 demo）"))
            return
        }
        if (update.isNotEmpty() && !update.startsWith("http://") && !update.startsWith("https://")) {
            respondJson(client, PhoneSaveResult(false, "更新地址也要以 http:// 开头，或者留空用默认"))
            return
        }

        // 保存 + 自动测试都在这里同步做完，手机那边能立刻看到结果
        val result = runBlocking {
            runCatching { onSave(api, update) }
                .getOrElse { PhoneSaveResult(false, "保存失败：" + (it.message ?: "未知原因")) }
        }
        respondJson(client, result)
    }

    private fun respondJson(client: Socket, result: PhoneSaveResult) {
        val payload = buildString {
            append("{\"ok\":").append(if (result.ok) "true" else "false")
            append(",\"message\":").append(jsonString(result.message))
            append('}')
        }
        respond(client, 200, TYPE_JSON, payload.toByteArray(Charsets.UTF_8))
    }

    /** 页面里预填当前配置 —— 手机打开就知道电视现在用的是什么 */
    @Volatile
    private var currentApiBase: String = ""

    @Volatile
    private var currentUpdateUrl: String = ""

    fun setCurrentValues(apiBase: String, updateUrl: String) {
        currentApiBase = apiBase
        currentUpdateUrl = updateUrl
    }

    // ── HTTP 读写 ──────────────────────────────────────────────

    private fun readHead(input: InputStream): Pair<String, Map<String, String>>? {
        val buffer = ByteArrayOutputStream()
        var tail = 0L
        while (buffer.size() < MAX_HEADER) {
            val b = input.read()
            if (b < 0) return null
            buffer.write(b)
            tail = ((tail shl 8) or b.toLong()) and 0xFFFFFFFFL
            if (tail == 0x0D0A0D0AL) break   // \r\n\r\n
        }
        val text = buffer.toString("ISO-8859-1")
        val lines = text.split("\r\n").filter { it.isNotEmpty() }
        val requestLine = lines.firstOrNull() ?: return null
        val headers = lines.drop(1).mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) null else line.take(colon).trim().lowercase() to line.substring(colon + 1).trim()
        }.toMap()
        return requestLine to headers
    }

    private fun readExact(input: InputStream, length: Int): ByteArray {
        val out = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(out, read, length - read)
            if (n < 0) break
            read += n
        }
        return out
    }

    private fun respond(client: Socket, code: Int, contentType: String, body: ByteArray) {
        val status = when (code) {
            200 -> "OK"
            204 -> "No Content"
            400 -> "Bad Request"
            403 -> "Forbidden"
            else -> "Not Found"
        }
        val header = buildString {
            append("HTTP/1.1 ").append(code).append(' ').append(status).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        val stream = client.getOutputStream()
        stream.write(header.toByteArray(Charsets.ISO_8859_1))
        if (body.isNotEmpty()) stream.write(body)
        stream.flush()
    }

    private fun isLocal(address: InetAddress): Boolean =
        address.isLoopbackAddress || address.isSiteLocalAddress

    /** 电视在局域网的 IPv4（wlan0 / eth0 都认，不靠网卡名字猜） */
    private fun lanIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    // ── H5 页面 ────────────────────────────────────────────────

    /**
     * 手机 / 电脑通用的配置页。
     *
     * 兼容性上做的几件事：手机上字号 16px 起（低于 16px 的输入框 iOS 会自动放大页面）、
     * `autocapitalize=off`（地址里的字母别被自动大写）、`inputmode=url`（弹出带 . / 的键盘）、
     * 电脑上居中限宽 560px（一行读得完，不会拉成大宽条）。
     */
    private fun htmlPage(): String = HTML_PAGE
        .replace("__API__", htmlEscape(currentApiBase))
        .replace("__UPD__", htmlEscape(currentUpdateUrl))

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
    }

    private fun htmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private companion object {
        const val PORT_START = 8899
        const val PORT_TRIES = 12
        const val MAX_HEADER = 16 * 1024
        const val MAX_BODY = 64 * 1024
        const val TYPE_HTML = "text/html; charset=utf-8"
        const val TYPE_JSON = "application/json; charset=utf-8"
        const val TYPE_TEXT = "text/plain; charset=utf-8"

        /**
         * 内嵌的配置页。
         *
         * ⚠️ 这是 Kotlin 原始字符串：里面**不能出现美元符号**（会被当成模板变量），
         *    所以 JS 一律用字符串拼接，不用模板字符串。改动前先搜一遍 "$"。
         */
        val HTML_PAGE = """
<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="color-scheme" content="dark">
<title>极简影院 · 配置</title>
<style>
  :root{--ink:#0a0a0c;--panel:#16161a;--panelhi:#22222a;--gold:#d4a056;--tmain:#f5f5f7;--tsub:#8e8e93}
  *{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
  html,body{margin:0;padding:0;background:var(--ink);color:var(--tmain);
    font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"PingFang SC","Microsoft YaHei",sans-serif}
  .wrap{max-width:560px;margin:0 auto;padding:30px 20px 64px}
  h1{font-size:24px;margin:0 0 8px;letter-spacing:.5px}
  .sub{color:var(--tsub);font-size:14px;line-height:1.7;margin:0 0 20px}
  label{display:block;font-size:14px;color:var(--tsub);margin:20px 0 8px}
  input{width:100%;padding:15px 16px;font-size:16px;color:var(--tmain);background:var(--panel);
    border:1px solid #2c2c34;border-radius:12px;outline:none;-webkit-appearance:none}
  input:focus{border-color:var(--gold)}
  .hint{color:#6f6f76;font-size:13px;line-height:1.7;margin:8px 0 0}
  code{background:var(--panelhi);padding:2px 6px;border-radius:6px;font-size:12px}
  button{width:100%;margin-top:26px;padding:17px;font-size:17px;font-weight:700;color:#0a0a0c;
    background:var(--gold);border:0;border-radius:14px;cursor:pointer}
  button:disabled{opacity:.55}
  .result{margin-top:18px;padding:15px 16px;border-radius:12px;font-size:15px;line-height:1.7;display:none;background:var(--panel)}
  .result.show{display:block}
  .result.ok{color:#8ce0a8}
  .result.bad{color:#e0a08c}
  summary{color:var(--tsub);font-size:14px;margin-top:24px;cursor:pointer}
  .foot{color:#5c5c63;font-size:12px;line-height:1.8;margin-top:28px}
</style>
</head>
<body>
<div class="wrap">
  <h1>极简影院 · 配置</h1>
  <p class="sub">在这里填好点保存 → 电视立即生效，并自动测一次连通性。电视上不用打一个字。</p>

  <label for="api">影视接口地址（必填）</label>
  <input id="api" value="__API__" inputmode="url" autocapitalize="off" autocorrect="off" spellcheck="false" placeholder="http://你的站点/api.php/provide/vod/">
  <p class="hint">苹果CMS V10 接口地址，形如 <code>http://站点/api.php/provide/vod/</code>。应用不内置任何片源。想先看界面长什么样，填 <b>demo</b> 即可。</p>

  <details>
    <summary>高级：更新地址（一般不用改）</summary>
    <label for="upd">在线更新清单地址（version.json）</label>
    <input id="upd" value="__UPD__" inputmode="url" autocapitalize="off" autocorrect="off" spellcheck="false" placeholder="留空 = 用默认的 GitHub 发布地址">
    <p class="hint">留空就用默认地址。只有电视连不上 GitHub 时，才需要换成镜像。</p>
  </details>

  <button id="save">保存并让电视测试</button>
  <div id="result" class="result"></div>

  <p class="foot">保存后电视会自动测试：能取到影片才算配置成功。<br>测完还能再改；电视上关掉「扫码配置」页面后，这个网址就会失效。</p>
</div>
<script>
(function () {
  var apiEl = document.getElementById('api');
  var updEl = document.getElementById('upd');
  var btn = document.getElementById('save');
  var box = document.getElementById('result');

  function show(ok, msg) {
    box.className = 'result show ' + (ok ? 'ok' : 'bad');
    box.textContent = msg;
  }

  btn.addEventListener('click', function () {
    var api = apiEl.value.trim();
    var upd = updEl.value.trim();
    if (!api) { show(false, '请先填接口地址'); return; }
    if (api.indexOf('http') !== 0 && api !== 'demo') {
      show(false, '地址要以 http:// 或 https:// 开头（想先看界面就填 demo）');
      return;
    }
    btn.disabled = true;
    btn.textContent = '正在让电视测试…';
    fetch('/api/config', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ apiUrl: api, updateUrl: upd })
    })
      .then(function (r) { return r.json(); })
      .then(function (d) {
        btn.disabled = false;
        btn.textContent = '保存并让电视测试';
        show(!!d.ok, d.message || '完成');
      })
      .catch(function () {
        btn.disabled = false;
        btn.textContent = '保存并让电视测试';
        show(false, '手机和电视没连上：确认在同一个 WiFi，且电视上的「扫码配置」页面还开着');
      });
  });
})();
</script>
</body>
</html>
"""
    }
}
