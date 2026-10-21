package com.ichichuang.cinema.feature.settings

import android.os.SystemClock
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
import java.util.UUID

/** 手机点「保存」之后的结果：电视那边测通了没有 + 给手机看的人话 */
data class PhoneSaveResult(val ok: Boolean, val message: String)

/**
 * 手机扫码配置用的迷你 HTTP 服务。
 *
 * 为什么要有它：**电视上打字是灾难**（遥控器一格一格挪，输入框一聚焦就弹键盘）。
 * 所以 TV 端的设置页不再有任何输入框 —— 点「扫码配置」后电视在局域网上起一个
 * 小网页，手机上就能改：
 *  - **推荐源 / 已保存的源**：点一下 → 勾选/取消（可多选，首页聚合），电视自动测试
 *  - **导入 TVBox 配置**：粘贴单仓/多仓 JSON 或订阅地址（v0.9.0 新增）
 *  - **自定义地址**：留给用户自己的苹果CMS接口（只启用这一个）
 *
 * 设计上的几条克制：
 * - **只在配置浮层打开时运行**，关掉即停（平时不监听任何端口）
 * - 只接受**局域网 / 本机**的请求（公网地址一律 403）
 * - 手写极简 HTTP/1.1：够用就行，不引任何服务端框架（电视内存紧张，也少一个依赖）
 * - 页面与接口同源 —— 手机上不存在跨域问题，不用 CORS 那套东西
 */
class ConfigServer(
    private val onSave: suspend (apiBase: String, updateUrl: String) -> PhoneSaveResult,
    private val onToggle: suspend (url: String) -> PhoneSaveResult,
    private val onImport: suspend (text: String) -> PhoneSaveResult,
    private val onSourcesPayload: suspend () -> String,
    private val onRefreshCatalog: suspend () -> PhoneSaveResult,
    private val port: Int = PORT_START,
    private val diagnosticReport: ByteArray? = null,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private var socket: ServerSocket? = null
    private var thread: Thread? = null

    @Volatile
    private var currentUrl: String? = null

    private val diagnosticPath = diagnosticReport?.let {
        "/diagnostics/${UUID.randomUUID().toString().replace("-", "")}/device-report.json"
    }
    @Volatile
    private var reportActive = false
    private var reportStartedAt = 0L

    /** 起服务；成功返回给用户看的网址（含局域网 IP），失败返回 null */
    @Synchronized
    fun start(): String? {
        if (thread != null) return currentUrl

        // 报告只提供局域网下载；没有可用地址时不展示无法从 Mac 访问的回环网址。
        val reportAddress = if (diagnosticReport != null) lanIpv4() ?: return null else null

        val bound = (port until port + PORT_TRIES).firstNotNullOfOrNull { candidate ->
            runCatching { ServerSocket(candidate) }.getOrNull()
        } ?: return null

        socket = bound
        currentUrl = "http://${reportAddress ?: lanIpv4() ?: "127.0.0.1"}:${bound.localPort}${diagnosticPath ?: "/"}"
        reportStartedAt = SystemClock.elapsedRealtime()
        reportActive = diagnosticReport != null
        thread = Thread({ acceptLoop(bound) }, "cinema-config-server").apply {
            isDaemon = true
            start()
        }
        return currentUrl
    }

    @Synchronized
    fun stop() {
        reportActive = false
        val closing = socket
        socket = null
        thread = null
        currentUrl = null
        runCatching { closing?.close() }
    }

    /** 包含电视待机时长，唤醒后不会给旧链接额外五分钟。 */
    fun reportRemainingMillis(): Long =
        if (reportActive) (REPORT_LIFETIME_MILLIS - (SystemClock.elapsedRealtime() - reportStartedAt)).coerceAtLeast(0L)
        else 0L

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

        // 只认局域网（电视和手机在同一个 WiFi 下）；公网/奇怪来源一律挡掉
        if (!isLocal(client.inetAddress)) {
            respond(client, 403, TYPE_TEXT, "仅限局域网访问".toByteArray(Charsets.UTF_8))
            return
        }

        // 独立报告会话仅允许读这份快照，不暴露配置页、片源接口或任何写接口。
        if (diagnosticReport != null) {
            val valid = reportRemainingMillis() > 0L
            if (valid && method == "GET" && path == diagnosticPath) {
                respond(client, 200, TYPE_JSON, diagnosticReport, download = true)
            } else {
                respond(client, 404, TYPE_TEXT, "下载链接已关闭、过期或不存在".toByteArray(Charsets.UTF_8))
            }
            return
        }

        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (length in 1..MAX_BODY) readExact(input, length) else ByteArray(0)

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

            // 已保存的源 + 推荐源清单（手机页渲染列表用）
            method == "GET" && path == "/api/sources" -> {
                val payload = runBlocking {
                    runCatching { onSourcesPayload() }.getOrElse { "{\"ok\":false}" }
                }
                respond(client, 200, TYPE_JSON, payload.toByteArray(Charsets.UTF_8))
            }

            // 手机上点一条源 → 电视勾选/取消（多选聚合），同步返回测试结果
            method == "POST" && path == "/api/toggle" -> handleToggle(client, body)

            // 手机上「导入 TVBox 配置」→ 粘贴 JSON 或订阅地址，电视解析并入库
            method == "POST" && path == "/api/import" -> handleImport(client, body)

            // 手机上点「获取推荐源」→ 电视去拉清单（拉完两边都能看到）
            method == "POST" && path == "/api/catalog/refresh" -> {
                val result = runBlocking {
                    runCatching { onRefreshCatalog() }
                        .getOrElse { PhoneSaveResult(false, "刷新失败：" + (it.message ?: "未知原因")) }
                }
                respondJson(client, result)
            }

            method == "GET" && path == "/favicon.ico" ->
                respond(client, 204, TYPE_TEXT, ByteArray(0))

            else -> respond(client, 404, TYPE_TEXT, "not found".toByteArray(Charsets.UTF_8))
        }
    }

    private fun handleSave(client: Socket, body: ByteArray) {
        val api = readStringField(body, "apiUrl").orEmpty()
        val update = readStringField(body, "updateUrl").orEmpty()

        if (api.isEmpty()) {
            respondJson(client, PhoneSaveResult(false, "地址不能为空"))
            return
        }
        if (!api.startsWith("http://") && !api.startsWith("https://") && api != "demo") {
            respondJson(client, PhoneSaveResult(false, "地址要以 http:// 或 https:// 开头（想先看界面就填 demo）"))
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

    private fun handleToggle(client: Socket, body: ByteArray) {
        val url = readStringField(body, "url").orEmpty()
        if (url.isEmpty()) {
            respondJson(client, PhoneSaveResult(false, "没收到要启用的地址"))
            return
        }
        val result = runBlocking {
            runCatching { onToggle(url) }
                .getOrElse { PhoneSaveResult(false, "切换失败：" + (it.message ?: "未知原因")) }
        }
        respondJson(client, result)
    }

    private fun handleImport(client: Socket, body: ByteArray) {
        val text = readStringField(body, "text").orEmpty()
        if (text.isEmpty()) {
            respondJson(client, PhoneSaveResult(false, "没收到要导入的内容（粘贴 JSON 或订阅地址）"))
            return
        }
        val result = runBlocking {
            runCatching { onImport(text) }
                .getOrElse { PhoneSaveResult(false, "导入失败：" + (it.message ?: "未知原因")) }
        }
        respondJson(client, result)
    }

    /** 从 JSON 请求体里取一个字符串字段（取不到 / 格式不对一律返回 null，不抛） */
    private fun readStringField(body: ByteArray, key: String): String? {
        if (body.isEmpty()) return null
        val text = String(body, Charsets.UTF_8)
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        return runCatching { root[key]?.jsonPrimitive?.contentOrNull }.getOrNull()?.trim()
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

    private fun respond(client: Socket, code: Int, contentType: String, body: ByteArray, download: Boolean = false) {
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
            if (download) {
                append("Content-Disposition: attachment; filename=\"cinema-tv-device-diagnostics.json\"\r\n")
                append("X-Content-Type-Options: nosniff\r\n")
            }
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

    companion object {
        const val REPORT_LIFETIME_MILLIS = 5 * 60 * 1000L

        /** 复用 HTTP 和局域网能力；此实例在路由层完全隔离配置读写。 */
        fun forDiagnosticReport(report: String): ConfigServer = ConfigServer(
            onSave = { _, _ -> PhoneSaveResult(false, "只读报告") },
            onToggle = { PhoneSaveResult(false, "只读报告") },
            onImport = { PhoneSaveResult(false, "只读报告") },
            onSourcesPayload = { "{}" },
            onRefreshCatalog = { PhoneSaveResult(false, "只读报告") },
            diagnosticReport = report.toByteArray(Charsets.UTF_8),
        )

        private const val PORT_START = 8899
        private const val PORT_TRIES = 12
        private const val MAX_HEADER = 16 * 1024
        /** 导入 TVBox 配置时请求体可能是几十上百 KB 的 JSON，留够（电视内存一次 0.5MB 无压力） */
        private const val MAX_BODY = 512 * 1024
        private const val TYPE_HTML = "text/html; charset=utf-8"
        private const val TYPE_JSON = "application/json; charset=utf-8"
        private const val TYPE_TEXT = "text/plain; charset=utf-8"

        /**
         * 内嵌的配置页。
         *
         * ⚠️ 这是 Kotlin 原始字符串：里面**不能出现美元符号**（会被当成模板变量），
         *    所以 JS 一律用字符串拼接，不用模板字符串。改动前先搜一遍 "$"。
         */
        private val HTML_PAGE = """
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
  label{display:block;font-size:14px;color:var(--tsub);margin:24px 0 8px}
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
  .list{margin-top:4px}
  .item{display:flex;align-items:center;gap:10px;text-align:left;margin:8px 0;padding:14px 16px;
    background:var(--panel);border:1px solid #2c2c34;border-radius:12px;color:var(--tmain);
    font-size:15px;font-weight:400}
  .item .n{font-weight:700;flex:0 0 auto;white-space:nowrap}
  .item .i{color:#6f6f76;font-size:12px;flex:1 1 auto;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
  .item .m{flex:0 0 auto;color:var(--gold);font-size:13px;font-weight:600}
  .item.active{border-color:var(--gold);background:#1d1a14}
  .item.on{border-color:var(--gold);background:#1d1a14}
  .item:disabled{opacity:1;cursor:default}
  .empty{color:#5c5c63;font-size:13px;padding:10px 2px}
  .sum{color:var(--gold);font-size:14px;line-height:1.7;margin:0 0 18px;padding:12px 14px;
    background:var(--panel);border:1px solid #2c2c34;border-radius:12px}
  textarea{width:100%;min-height:110px;padding:15px 16px;font-size:15px;color:var(--tmain);
    background:var(--panel);border:1px solid #2c2c34;border-radius:12px;outline:none;
    -webkit-appearance:none;font-family:inherit;resize:vertical}
  textarea:focus{border-color:var(--gold)}
  button.ghost{background:var(--panel);color:var(--tmain);border:1px solid #2c2c34;margin-top:12px;
    font-size:15px;font-weight:600;padding:14px}
</style>
</head>
<body>
<div class="wrap">
  <h1>极简影院 · 配置</h1>
  <p class="sub">点一下就启用（再点一下取消），电视马上生效并自动测试。可以同时启用多个，首页会把这些电影合在一起。</p>
  <div id="sum" class="sum">正在读取电视状态…</div>

  <label>推荐（点一下启用 / 取消）</label>
  <div id="catalog" class="list"><div class="empty">正在获取推荐…</div></div>
  <button id="fetchBtn" class="ghost" style="display:none">获取推荐</button>

  <label>已添加（点一下启用 / 取消）</label>
  <div id="saved" class="list"><div class="empty">还没有添加过别的</div></div>

  <details>
    <summary>高级：批量导入（TVBox 订阅 / 单仓多仓 JSON）</summary>
    <label for="imp">整份 JSON 或订阅地址</label>
    <textarea id="imp" autocapitalize="off" autocorrect="off" spellcheck="false" placeholder="{ &quot;sites&quot;: [ ... ] }  或  https://…/推荐的.json"></textarea>
    <p class="hint">整份 JSON 贴进来，或直接贴订阅地址。相对路径的引用（如 <code>./xxx.json</code>）抓不到会跳过。</p>
    <button id="impBtn" class="ghost">导入</button>
  </details>

  <label for="api">自己填一个地址（只启用这一个）</label>
  <input id="api" value="__API__" inputmode="url" autocapitalize="off" autocorrect="off" spellcheck="false" placeholder="http://你的站点/api.php/provide/vod/">
  <p class="hint">形如 <code>http://站点/api.php/provide/vod/</code>。应用本身不带内容；想先看界面长什么样，填 <b>demo</b> 即可。</p>

  <details>
    <summary>高级：改更新地址（一般不用动）</summary>
    <label for="upd">更新清单地址（version.json）</label>
    <input id="upd" value="__UPD__" inputmode="url" autocapitalize="off" autocorrect="off" spellcheck="false" placeholder="留空 = 用默认地址">
    <p class="hint">留空就用默认的。连不上更新的时候，可以换一个能用的地址。</p>
  </details>

  <button id="save">保存并测试（只启用这一个）</button>
  <div id="result" class="result"></div>

  <p class="foot">保存后电视会自动测一次，能取到影片才算成功。<br>关掉电视上的「手机配置」页面后，这个网址就会失效。</p>
</div>
<script>
(function () {
  var apiEl = document.getElementById('api');
  var updEl = document.getElementById('upd');
  var btn = document.getElementById('save');
  var box = document.getElementById('result');
  var catEl = document.getElementById('catalog');
  var savedEl = document.getElementById('saved');
  var fetchBtn = document.getElementById('fetchBtn');
  var impEl = document.getElementById('imp');
  var impBtn = document.getElementById('impBtn');
  var sumEl = document.getElementById('sum');
  var busy = false;
  var enabled = [];

  // 地址归一化（只用于"是否已启用"的判断）："…/vod" 与 "…/vod/" 是同一个源
  function normUrl(u) { return (u || '').replace(/\/+$/, ''); }
  function isOn(u) { return enabled.indexOf(normUrl(u)) >= 0; }
  // 清单里的 "3023ms" 是排序用的，手机上不显示
  function cleanInfo(s) {
    return (s || '').split('·').map(function (x) { return x.trim(); })
      .filter(function (x) { return x.slice(-2) !== 'ms'; }).join(' · ');
  }

  function show(ok, msg) {
    box.className = 'result show ' + (ok ? 'ok' : 'bad');
    box.textContent = msg;
  }

  function makeRow(name, info, on, url) {
    var row = document.createElement('button');
    row.type = 'button';
    row.className = 'item' + (on ? ' on' : '');
    var n = document.createElement('span'); n.className = 'n'; n.textContent = name;
    var i = document.createElement('span'); i.className = 'i'; i.textContent = info || '';
    var m = document.createElement('span'); m.className = 'm'; m.textContent = on ? '已启用' : '启用';
    row.appendChild(n); row.appendChild(i); row.appendChild(m);
    row.addEventListener('click', function () { toggleSource(url); });
    return row;
  }

  function paint(el, rows, emptyText) {
    el.textContent = '';
    if (!rows.length) {
      var d = document.createElement('div'); d.className = 'empty'; d.textContent = emptyText;
      el.appendChild(d);
      return;
    }
    rows.forEach(function (r) { el.appendChild(r); });
  }

  function load() {
    fetch('/api/sources').then(function (r) { return r.json(); }).then(function (d) {
      if (!d || d.ok !== true) { show(false, '电视那边状态不对，重开一下配置页'); return; }
      enabled = (d.enabled || '').split('\n')
        .filter(function (s) { return s.trim().length > 0; })
        .map(function (s) { return normUrl(s.trim()); });
      var maxN = d.maxEnabled || 8;
      sumEl.textContent = enabled.length
        ? ('已启用 ' + enabled.length + ' / ' + maxN + ' 个：首页会把电影合在一起')
        : '还没有启用任何资源：从下面挑一个吧';
      var saved = (d.saved || []).map(function (s) {
        return makeRow(s.name, s.url, isOn(s.url), s.url);
      });
      paint(savedEl, saved, '还没有添加过别的');
      var cat = (d.catalog || []).map(function (s) {
        return makeRow(s.name, cleanInfo(s.info) || s.url, isOn(s.url), s.url);
      });
      var hasCat = !!(d.catalog && d.catalog.length);
      paint(catEl, cat, '还没获取过推荐，点下面的按钮去获取');
      fetchBtn.style.display = 'block';
      fetchBtn.textContent = hasCat ? '刷新推荐' : '获取推荐';
    }).catch(function () {
      show(false, '打不开电视上的配置服务：确认手机和电视在同一个 WiFi');
    });
  }

  function toggleSource(url) {
    if (busy) return;
    busy = true;
    show(true, '正在切换并测试，稍等几秒…');
    fetch('/api/toggle', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url: url })
    })
      .then(function (r) { return r.json(); })
      .then(function (d) { busy = false; show(!!d.ok, d.message || '完成'); load(); })
      .catch(function () { busy = false; show(false, '电视没响应：确认配置页还开着，然后重试'); load(); });
  }

  fetchBtn.addEventListener('click', function () {
    if (busy) return;
    busy = true;
    fetchBtn.disabled = true;
    fetchBtn.textContent = '正在获取…';
    fetch('/api/catalog/refresh', { method: 'POST' })
      .then(function (r) { return r.json(); })
      .then(function (d) {
        busy = false; fetchBtn.disabled = false;
        show(!!d.ok, d.message || '完成');
        load();
      })
      .catch(function () {
        busy = false; fetchBtn.disabled = false;
        show(false, '获取失败：电视那边连不上清单，稍后再试');
        load();
      });
  });

  impBtn.addEventListener('click', function () {
    var text = impEl.value.trim();
    if (!text) { show(false, '先粘贴 JSON 内容或订阅地址'); return; }
    if (busy) return;
    busy = true;
    impBtn.disabled = true;
    impBtn.textContent = '正在导入…';
    show(true, '正在导入，订阅地址多的话要等几秒…');
    fetch('/api/import', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text: text })
    })
      .then(function (r) { return r.json(); })
      .then(function (d) {
        busy = false; impBtn.disabled = false; impBtn.textContent = '导入';
        show(!!d.ok, d.message || '完成');
        if (d.ok) { impEl.value = ''; }
        load();
      })
      .catch(function () {
        busy = false; impBtn.disabled = false; impBtn.textContent = '导入';
        show(false, '电视没响应：确认配置页还开着，然后重试');
        load();
      });
  });

  btn.addEventListener('click', function () {
    var api = apiEl.value.trim();
    var upd = updEl.value.trim();
    if (!api) { show(false, '请先填地址'); return; }
    if (api.indexOf('http') !== 0 && api !== 'demo') {
      show(false, '地址要以 http:// 或 https:// 开头（想先看界面就填 demo）');
      return;
    }
    btn.disabled = true;
    btn.textContent = '正在测试…';
    fetch('/api/config', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ apiUrl: api, updateUrl: upd })
    })
      .then(function (r) { return r.json(); })
      .then(function (d) {
        btn.disabled = false;
        btn.textContent = '保存并测试（只启用这一个）';
        show(!!d.ok, d.message || '完成');
        load();
      })
      .catch(function () {
        btn.disabled = false;
        btn.textContent = '保存并测试（只启用这一个）';
        show(false, '手机和电视没连上：确认在同一个 WiFi，且电视上的「手机配置」页面还开着');
      });
  });

  load();
})();
</script>
</body>
</html>
"""
    }
}
