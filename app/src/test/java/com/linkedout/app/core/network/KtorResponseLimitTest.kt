package com.linkedout.app.core.network

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The cap through the real stack the apps use, a Ktor client over OkHttp
 * (https://ktor.io/docs/client-engines.html#okhttp) against a local server
 * of the JDK (https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/package-summary.html):
 * what the caller's catch receives when an answer is over the cap, with a
 * length declared and with none (chunked).
 */
class KtorResponseLimitTest {

    private lateinit var server: HttpServer
    private lateinit var client: HttpClient

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/sized") { exchange ->
            val body = ByteArray(5_000) { 'a'.code.toByte() }
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/chunked") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { out -> repeat(5) { out.write(ByteArray(1_000) { 'a'.code.toByte() }) } }
        }
        server.start()
        client = HttpClient(OkHttp) { engine { addInterceptor(ResponseLimit(4_000)) } }
    }

    @After
    fun stop() {
        client.close()
        server.stop(0)
    }

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    private fun failureOf(path: String): Throwable? = runBlocking {
        try {
            client.get(url(path)).bodyAsText()
            null
        } catch (failure: Throwable) {
            failure
        }
    }

    // The cause chain of a Throwable, https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-throwable/cause.html
    @Test
    fun `a declared length over the cap reaches the caller as a failure that isTooLarge`() {
        val failure = failureOf("/sized")
        assertTrue(failure.toString(), failure != null && failure.isTooLarge())
    }

    @Test
    fun `an unknown length over the cap reaches the caller as a failure that isTooLarge`() {
        val failure = failureOf("/chunked")
        assertTrue(failure.toString(), failure != null && failure.isTooLarge())
    }

    @Test
    fun `any other failure is not taken for the cap`() {
        assertFalse(RuntimeException("x", java.io.IOException("y")).isTooLarge())
        assertTrue(RuntimeException("x", ResponseTooLarge(1)).isTooLarge())
    }
}
