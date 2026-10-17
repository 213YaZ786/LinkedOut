package com.linkedout.app.core.network

import java.io.ByteArrayInputStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Under, at and over the cap, with a length declared, missing or false.
 * The client is built as OkHttp's own interceptor tests do, an application
 * interceptor that answers a Response it builds, so no server is needed
 * (https://square.github.io/okhttp/features/interceptors/); the cases are
 * those of Signal-Android's OkHttpUtil.readAsBytes and readAsString
 * (https://github.com/signalapp/Signal-Android,
 * app/src/main/java/org/thoughtcrime/securesms/util/OkHttpUtil.java).
 */
class ResponseLimitTest {

    private fun bytes(n: Int) = ByteArray(n) { (it % 251).toByte() }

    @Test
    fun `a read under the cap returns every byte`() {
        val data = bytes(100_000)
        assertArrayEquals(data, readAsBytes(ByteArrayInputStream(data), 200_000))
    }

    @Test
    fun `a read at the cap returns every byte`() {
        val data = bytes(32 * 1024 * 3)
        assertArrayEquals(data, readAsBytes(ByteArrayInputStream(data), data.size.toLong()))
    }

    @Test
    fun `a read one byte over the cap fails`() {
        val data = bytes(32 * 1024 * 3 + 1)
        val failure = assertThrows(ResponseTooLarge::class.java) {
            readAsBytes(ByteArrayInputStream(data), (data.size - 1).toLong())
        }
        assertEquals(data.size - 1L, failure.limit)
    }

    @Test
    fun `an empty read is empty`() {
        assertEquals(0, readAsBytes(ByteArrayInputStream(ByteArray(0)), 0).size)
    }

    /** A client whose "network" answers [size] bytes, declaring [declared] as Content-Length (-1: none). */
    private fun call(limit: Long, size: Int, declared: Long, method: String = "GET"): ByteArray {
        val client = OkHttpClient.Builder()
            .addInterceptor(ResponseLimit(limit))
            .addInterceptor { chain ->
                val body = Buffer().write(bytes(size)).asResponseBody("application/json".toMediaType(), declared)
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            }
            .build()
        return client.newCall(Request.Builder().url("https://example.org/").method(method, null).build())
            .execute().use { if (method == "HEAD") ByteArray(0) else it.body.bytes() }
    }

    @Test
    fun `an answer under the cap comes through whole`() {
        assertArrayEquals(bytes(1000), call(limit = 2000, size = 1000, declared = 1000))
    }

    @Test
    fun `an answer at the cap comes through whole, with or without a length`() {
        assertArrayEquals(bytes(2000), call(limit = 2000, size = 2000, declared = 2000))
        assertArrayEquals(bytes(2000), call(limit = 2000, size = 2000, declared = -1))
    }

    @Test
    fun `an answer whose length is over the cap fails before a byte is read`() {
        assertThrows(ResponseTooLarge::class.java) { call(limit = 2000, size = 10, declared = 2001) }
    }

    @Test
    fun `an answer over the cap with no length, or a false one, fails while reading`() {
        assertThrows(ResponseTooLarge::class.java) { call(limit = 2000, size = 2001, declared = -1) }
        assertThrows(ResponseTooLarge::class.java) { call(limit = 2000, size = 50_000, declared = 100) }
    }

    @Test
    fun `an empty answer is empty`() {
        assertEquals(0, call(limit = 2000, size = 0, declared = 0).size)
    }

    @Test
    fun `a HEAD answer is not counted`() {
        call(limit = 2000, size = 0, declared = 50_000, method = "HEAD")
    }
}
