package kr.kcocktail.common.revalidate

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * `POST {FRONTEND_URL}/api/revalidate` (SPEC-07 §4).
 *
 * ## 다른 스레드로 넘긴다 (RED 22)
 *
 * `FR-COCKTAIL-016` — "에디터가 반영을 기다리지 않는다".
 * 발행 응답이 프론트의 재생성 시간만큼 늦어지면 그 요구를 어긴다.
 * 프론트가 느리든 죽었든 발행 응답은 그대로 나가야 한다.
 *
 * ## 예외를 밖으로 내보내지 않는다 (`NFR-R-03`)
 *
 * 어차피 다른 스레드라 던져도 호출부에 닿지 않지만, **삼키는 것을 명시적으로 적는다.**
 * 스레드를 나눈 것이 우연히 격리해 준 것과, 격리가 설계인 것은 다르다 —
 * 나중에 동기 호출로 바꾸는 사람이 이 `runCatching` 을 보고 멈춘다.
 */
@Component
class HttpRevalidateHook(
    private val properties: RevalidateProperties,
    @Qualifier("revalidateTaskExecutor") private val executor: TaskExecutor,
) : RevalidateHook {

    private val log = LoggerFactory.getLogger(javaClass)

    private val client: RestClient by lazy {
        RestClient.builder()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofMillis(properties.timeoutMs))
                    setReadTimeout(Duration.ofMillis(properties.timeoutMs))
                },
            )
            .build()
    }

    override fun revalidate(paths: List<String>) {
        if (paths.isEmpty()) return

        if (!properties.enabled) {
            // 로컬에서 프론트를 안 띄웠다고 발행이 막히면 안 된다 (DECISIONS §1)
            log.debug("재생성 훅 비활성화 — 건너뛴다: {}", paths)
            return
        }

        val startedAt = System.nanoTime()
        executor.execute { send(paths, startedAt) }
    }

    private fun send(paths: List<String>, startedAt: Long) {
        runCatching {
            client.post()
                .uri(properties.endpoint)
                .header(SECRET_HEADER, properties.secret)
                .body(RevalidateRequest(paths))
                .retrieve()
                .toBodilessEntity()
        }.onSuccess {
            // NFR-O-02 — 발행 후 공개 반영 30초 예산의 서버 몫을 남긴다
            log.info("재생성 요청 완료 ({}ms): {}", elapsedMs(startedAt), paths)
        }.onFailure {
            // 시크릿도 URL 의 쿼리도 찍지 않는다 (RED 20). 경로와 사유만 남긴다
            log.error(
                "재생성 요청 실패 — 발행은 유지한다 (NFR-R-03, ISR 이 따라잡는다). {}ms, paths={}, 사유={}",
                elapsedMs(startedAt),
                paths,
                it.toString(),
            )
        }
    }

    private fun elapsedMs(startedAt: Long) = (System.nanoTime() - startedAt) / 1_000_000

    companion object {
        const val SECRET_HEADER = "X-Revalidate-Secret"
    }
}

/** SPEC-07 §4 의 본문. `{ "paths": [...] }` */
data class RevalidateRequest(val paths: List<String>)
