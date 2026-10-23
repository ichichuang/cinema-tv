package com.ichichuang.cinema.data.update

import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTransportPolicyTest {
    @Test fun httpsDowngradeIsRejectedAndResponseIsClosed() {
        val closed = AtomicBoolean(false)
        val source = object : ForwardingSource(Buffer()) {
            override fun close() { closed.set(true); super.close() }
        }.buffer()
        val body = object : ResponseBody() {
            override fun contentType() = null
            override fun contentLength() = 0L
            override fun source() = source
        }
        val origin = "https://example.invalid/manifest".toHttpUrl()
        val response = Response.Builder()
            .request(Request.Builder().url(origin).build())
            .protocol(Protocol.HTTP_1_1).code(302).message("fixture")
            .header("Location", "http://example.invalid/manifest")
            .body(body).build()
        assertThrows(IOException::class.java) { UpdateTransportPolicy.verifyRedirect(origin, response) }
        assertTrue(closed.get())
    }

    @Test fun safeRedirectsAndNonredirectResponsesRemainCompatible() {
        for ((originText, destination, code) in listOf(
            Triple("http://example.invalid/old", "https://example.invalid/new", 302),
            Triple("http://example.invalid/old", "/new", 302),
            Triple("https://example.invalid/old", "/new", 302),
            Triple("https://example.invalid/old", "http://example.invalid/information", 200),
        )) {
            val origin = originText.toHttpUrl()
            val response = Response.Builder().request(Request.Builder().url(origin).build())
                .protocol(Protocol.HTTP_1_1).code(code).message("fixture")
                .header("Location", destination).body("".toResponseBody()).build()
            response.use { assertSame(response, UpdateTransportPolicy.verifyRedirect(origin, response)) }
        }
    }

    @Test fun legacyHttpEndpointCanRedirectToHttps() {
        // Observe the TLS attempt without a test certificate or private key.
        val tlsAttempted = AtomicBoolean(false)
        val server = ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 3000
        val worker = thread(isDaemon = true) {
            runCatching {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { /* consume the HTTP headers */ }
                    socket.getOutputStream().write(
                        ("HTTP/1.1 302 Found\r\nLocation: https://127.0.0.1:${server.localPort}/secure\r\n" +
                            "Content-Length: 0\r\nConnection: close\r\n\r\n").toByteArray(),
                    )
                }
                // Closing the next connection deliberately stops the TLS handshake.
                server.accept().close()
            }
        }
        val base = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .retryOnConnectionFailure(false)
            .callTimeout(3, TimeUnit.SECONDS)
            .eventListener(object : EventListener() {
                override fun secureConnectStart(call: Call) { tlsAttempted.set(true) }
            })
            .build()
        try {
            val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/legacy").build()
            try { UpdateTransportPolicy.client(base).newCall(request).execute().close() } catch (_: IOException) { }
            assertTrue("A legitimate HTTP-to-HTTPS upgrade must still be attempted", tlsAttempted.get())
        } finally {
            server.close()
            worker.join(4000)
        }
    }
}
