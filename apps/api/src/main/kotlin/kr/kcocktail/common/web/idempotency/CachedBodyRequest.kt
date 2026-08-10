package kr.kcocktail.common.web.idempotency

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

/**
 * 본문을 통째로 들고 있다가 **몇 번이든 다시 읽게** 해 준다.
 *
 * 스프링의 `ContentCachingRequestWrapper` 로는 안 된다. 그것은 *읽힌 만큼* 사후 조회용으로
 * 남겨 둘 뿐이라, 지문을 만들려고 한 번 읽으면 컨트롤러의 `@RequestBody` 가 빈 스트림을 만난다.
 * 요청이 400 으로 떨어지고 **필터가 원인이라는 단서는 아무 데도 남지 않는다.**
 */
class CachedBodyRequest(request: HttpServletRequest) : HttpServletRequestWrapper(request) {

    val body: ByteArray = request.inputStream.readBytes()

    override fun getInputStream(): ServletInputStream {
        val source = ByteArrayInputStream(body)
        return object : ServletInputStream() {
            override fun read() = source.read()
            override fun available() = source.available()
            override fun isFinished() = source.available() == 0
            override fun isReady() = true
            override fun setReadListener(listener: ReadListener) = Unit
        }
    }

    override fun getReader(): BufferedReader =
        BufferedReader(InputStreamReader(inputStream, characterEncoding ?: Charsets.UTF_8.name()))
}
