package kr.mut.ingredient.internal

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 매일 04:10 (KST) 매칭 배치 (#213). 검증 배치(04:00) 뒤에 돈다.
 * `mut.ingredient.match-scheduled=false` 로 끈다 — 테스트와 CLI 가 그렇게 한다.
 * 실패는 로그로 남기고 다음 주기에 다시 돈다 (`VerificationSchedule` 과 같은 이유).
 */
@Component
@EnableScheduling
@ConditionalOnProperty(
    prefix = "mut.ingredient",
    name = ["match-scheduled"],
    havingValue = "true",
    matchIfMissing = true,
)
class IngredientMatchSchedule(private val batch: IngredientMatchBatch) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 10 4 * * *", zone = "Asia/Seoul")
    fun daily() {
        runCatching { batch.run() }
            .onFailure { log.error("재료 매칭 배치가 실패했다 — 다음 주기에 다시 돈다", it) }
    }
}
