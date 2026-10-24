package com.ichichuang.cinema.data.subscription

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun interface DirectoryResolver { suspend fun resolve(host: String): List<InetAddress> }
internal data class DirectoryResponse(val status: Int, val body: ByteArray)
/** Injection is for owned synthetic tests; no injected transport is registered in the application. */
internal fun interface DirectoryExchange {
    suspend fun fetch(url: HttpUrl, pinned: List<InetAddress>, maxBytes: Int): DirectoryResponse
}
internal object PublicDirectoryPolicy {
    fun url(raw: String): HttpUrl {
        val url = raw.toHttpUrlOrNull() ?: reject(SubscriptionFailure.DESTINATION)
        if (url.scheme != "https" || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.query != null || url.fragment != null || raw.substringAfter("://").substringBefore('/').contains('@') ||
            raw.contains('\\') || raw.any { it.code <= 32 }) reject(SubscriptionFailure.DESTINATION)
        val host = url.host
        // OkHttp normalizes mapped literals into IPv4; reject the original IPv6 mapped spelling too.
        val authority = raw.substringAfter("://").substringBefore('/')
        if (authority.startsWith('[')) {
            val literal = try { InetAddress.getByName(authority.substringAfter('[').substringBefore(']')) }
                catch (_: Exception) { reject(SubscriptionFailure.DESTINATION) }
            if (literal.address.size != 16) reject(SubscriptionFailure.DESTINATION)
        }
        if (!host.contains('.') && !host.contains(':') || host.endsWith('.') ||
            listOf("localhost", ".localhost", ".local", ".internal", ".home", ".lan").any { host == it || host.endsWith(it) })
            reject(SubscriptionFailure.DESTINATION)
        if (host.contains(':') || host.all { it.isDigit() || it == '.' }) {
            val address = try { InetAddress.getByName(host) } catch (_: Exception) { reject(SubscriptionFailure.DESTINATION) }
            if (host.contains(':') && address.address.size == 4 || !public(address)) reject(SubscriptionFailure.DESTINATION)
        }
        return url
    }
    fun origin(url: HttpUrl): String = "https://${if (url.host.contains(':')) "[${url.host}]" else url.host}"
    fun public(address: InetAddress): Boolean {
        val b = address.address.map { it.toInt() and 255 }
        if (b.size == 4) {
            val a = b[0]; val c = b[1]
            return !(a == 0 || a == 10 || a == 127 || a >= 224 || a == 100 && c in 64..127 ||
                a == 169 && c == 254 || a == 172 && c in 16..31 || a == 192 ||
                a == 198 && (c == 18 || c == 19 || c == 51 && b[2] == 100) || a == 203 && c == 0 && b[2] == 113)
        }
        if (b.size != 16 || b[0] !in 0x20..0x3f) return false
        // Conservative global-unicast subset excludes IETF special-use, documentation and tunnels.
        if (b[0] == 0x20 && b[1] == 0x01 && (b[2] < 2 || b[2] == 0x0d && b[3] == 0xb8)) return false
        if (b[0] == 0x20 && b[1] == 0x02 || b[0] == 0x3f && b[1] == 0xff) return false
        return true
    }
}

internal class PublicDirectoryFetcher(
    private val resolver: DirectoryResolver = BoundedDirectoryResolver,
    private val exchange: DirectoryExchange = PinnedDirectoryExchange,
    private val timeoutMillis: Long = 10_000,
) {
    init { require(timeoutMillis in 1..30_000) }
    suspend fun fetch(raw: String): ByteArray {
        val url = PublicDirectoryPolicy.url(raw)
        return withTimeoutOrNull(timeoutMillis) {
            try {
                val addresses = withTimeoutOrNull(2_000) { resolver.resolve(url.host).toList() }
                    ?: reject(SubscriptionFailure.TIMEOUT)
                if (addresses.isEmpty() || addresses.size > 16 || addresses.any { !PublicDirectoryPolicy.public(it) }) reject(SubscriptionFailure.DESTINATION)
                // Exactly this immutable answer is used by the fresh client's DNS, never resolved again.
                val response = exchange.fetch(url, addresses, DirectoryValidator.MAX_BYTES)
                if (response.status in 300..399) reject(SubscriptionFailure.DESTINATION)
                if (response.status !in 200..299) reject(SubscriptionFailure.NETWORK)
                if (response.body.size > DirectoryValidator.MAX_BYTES) reject(SubscriptionFailure.TOO_LARGE)
                response.body.copyOf()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: SubscriptionException) { throw e }
            catch (_: Exception) { reject(SubscriptionFailure.NETWORK) }
        } ?: reject(SubscriptionFailure.TIMEOUT)
    }
}

/** At most two resolver workers; a stuck OS lookup cannot accumulate unbounded jobs or block the caller. */
private object BoundedDirectoryResolver : DirectoryResolver {
    private val executor = ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS, SynchronousQueue(),
        { task -> Thread(task, "candidate-directory-dns").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    override suspend fun resolve(host: String): List<InetAddress> = suspendCancellableCoroutine { continuation ->
        val future = try { executor.submit {
            try { val result = InetAddress.getAllByName(host).toList(); if (continuation.isActive) continuation.resume(result) }
            catch (_: Exception) { if (continuation.isActive) continuation.resumeWithException(SubscriptionException(SubscriptionFailure.NETWORK)) }
        } } catch (_: Exception) { continuation.resumeWithException(SubscriptionException(SubscriptionFailure.NETWORK)); null }
        continuation.invokeOnCancellation { future?.cancel(true) }
    }
}

private object PinnedDirectoryExchange : DirectoryExchange {
    override suspend fun fetch(url: HttpUrl, pinned: List<InetAddress>, maxBytes: Int): DirectoryResponse =
        suspendCancellableCoroutine { continuation ->
            val addresses = pinned.toList()
            val pool = ConnectionPool(0, 1, TimeUnit.SECONDS)
            val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).connectionPool(pool)
                .dns { host -> if (host == url.host) addresses else throw java.net.UnknownHostException("DESTINATION") }
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                .connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()
            val call = client.newCall(Request.Builder().url(url).get().build())
            continuation.invokeOnCancellation { call.cancel(); pool.evictAll(); client.dispatcher.executorService.shutdown() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    pool.evictAll(); client.dispatcher.executorService.shutdown()
                    if (continuation.isActive) continuation.resumeWithException(SubscriptionException(SubscriptionFailure.NETWORK))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            if (it.code in 300..399) reject(SubscriptionFailure.DESTINATION)
                            if (it.code !in 200..299) reject(SubscriptionFailure.NETWORK)
                            val body = it.body
                            if (body.contentLength() > maxBytes) reject(SubscriptionFailure.TOO_LARGE)
                            val output = java.io.ByteArrayOutputStream()
                            body.byteStream().use { input ->
                                val buffer = ByteArray(4096)
                                while (true) { val count = input.read(buffer); if (count < 0) break
                                    if (output.size() + count > maxBytes) reject(SubscriptionFailure.TOO_LARGE)
                                    output.write(buffer, 0, count)
                                }
                            }
                            DirectoryResponse(it.code, output.toByteArray())
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(
                            if (e is SubscriptionException) e else SubscriptionException(SubscriptionFailure.NETWORK))
                    } finally { pool.evictAll(); client.dispatcher.executorService.shutdown() }
                }
            })
        }
}
