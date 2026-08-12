package kr.kcocktail.admin.verification

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 일 1회 전수 스캔 (SPEC-06 §4.3 "일 1회", `NFR-D-01`·`D-02`).
 *
 * ## Spring Batch 를 지금 쓰지 않는다
 *
 * SPEC-05 §8 은 Spring Batch 를 지정했지만, `DECISIONS.md` §3 이 이 항목을
 * **"착수 후 판단 — 1b 에 배치가 3종 늘 때"** 로 미뤄 뒀다. 지금 도입하면
 * 메타 테이블 6개와 시퀀스 3개가 들어오는데, 그것들은 SPEC-06 §1.2 의 공통 컬럼 규약도
 * §6 의 명명 규칙도 따르지 않아 `SchemaLint` 예외를 그만큼 뚫어야 한다.
 * 배치가 하나뿐인 지금은 값보다 비용이 크다 (DECISIONS §1.9 에 옮겨 적었다).
 *
 * **재검토 시점은 Phase 1b** — 바 검증 · 파트너 집계 · 인스타 동기화가 붙을 때다.
 *
 * ## 실패가 다음 실행을 막지 않는다 (RED 26)
 *
 * `@Scheduled` 는 예외가 나도 다음 주기에 다시 부른다. 그래도 삼키는 것을 명시한다 —
 * 로그가 없으면 배치가 몇 주째 죽어 있어도 아무도 모른다.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(
    prefix = "kcocktail.verification",
    name = ["scheduled"],
    havingValue = "true",
    matchIfMissing = true,
)
class VerificationSchedule(private val batch: InvariantVerificationBatch) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 매일 04:00 (KST). 트래픽이 가장 적고, 아침에 사람이 큐를 열면 결과가 있다. */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    fun daily() {
        runCatching { batch.run() }
            .onSuccess { run ->
                if (run.hasViolations) {
                    // NFR-D-01 은 "발행분에 불변식 위반 0건" 이다. 0이 아니면 사람을 부른다.
                    log.warn("불변식 위반 {}건 — 검증 태스크를 확인한다", run.violations.size)
                }
            }
            .onFailure { log.error("불변식 검증 배치가 실패했다 — 다음 주기에 다시 돈다", it) }
    }
}
