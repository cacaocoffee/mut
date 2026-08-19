package kr.mut.common.revalidate

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * 프론트의 `/api/revalidate` 를 흉내 낸다 (수신 구현은 이슈 038).
 *
 * JDK 의 `HttpServer` 를 쓴다 — 목 서버 라이브러리를 새로 들이지 않는다.
 * 이 이슈가 확인해야 할 것은 **우리가 무엇을 어떻게 보내는가**뿐이라
 * 요청을 받아 적어 두는 것 이상이 필요 없다.
 */
class RevalidateMockFront(
    /** 응답 전 지연. 비동기(RED 22)와 타임아웃(RED 13)을 흉내 낸다. */
    private val delayMs: Long = 0,
    private val status: Int = 200,
) {
    val received = CopyOnWriteArrayList<Recorded>()

    private val server: HttpServer = HttpServer.create(InetSocketAddress(0), 0).apply {
        createContext("/api/revalidate") { exchange -> handle(exchange) }
        executor = null
        start()
    }

    val baseUrl: String get() = "http://localhost:${server.address.port}"

    private fun handle(exchange: HttpExchange) {
        val body = exchange.requestBody.readBytes().decodeToString()
        received += Recorded(
            method = exchange.requestMethod,
            secret = exchange.requestHeaders.getFirst(HttpRevalidateHook.SECRET_HEADER),
            body = body,
        )

        if (delayMs > 0) Thread.sleep(delayMs)

        exchange.sendResponseHeaders(status, -1)
        exchange.close()
    }

    /**
     * 요청이 도착할 때까지 기다린다. 훅이 **다른 스레드로 보내기 때문에**
     * 곧바로 단언하면 아직 안 왔을 수 있다 — 그 비동기성이 RED 22 가 요구하는 것이다.
     */
    fun awaitFirst(timeoutMs: Long = 3_000): Recorded {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            received.firstOrNull()?.let { return it }
            Thread.sleep(10)
        }
        error("재생성 요청이 ${timeoutMs}ms 안에 오지 않았다")
    }

    fun stop() = server.stop(0)

    data class Recorded(val method: String, val secret: String?, val body: String)
}
