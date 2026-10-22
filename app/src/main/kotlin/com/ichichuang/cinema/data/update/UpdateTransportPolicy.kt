package com.ichichuang.cinema.data.update

import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

internal object UpdateTransportPolicy {
    fun client(base: OkHttpClient): OkHttpClient =
        base.newBuilder()
            .followSslRedirects(true)
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                verifyRedirect(request.url, chain.proceed(request))
            }
            .build()

    /** Runs before OkHttp follows each response, including intermediate redirect hops. */
    fun verifyRedirect(origin: HttpUrl, response: Response): Response {
        val destination = response.header("Location")?.let(origin::resolve)
        if (response.isRedirect && origin.isHttps && destination?.isHttps == false) {
            response.close()
            throw IOException("更新连接不允许从 HTTPS 降级到 HTTP")
        }
        return response
    }
}
