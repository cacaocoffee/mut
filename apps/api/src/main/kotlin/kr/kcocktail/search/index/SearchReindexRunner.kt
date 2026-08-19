package kr.kcocktail.search.index

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 색인 되살리기 CLI (ISSUE-053 · [G-34](../../../../../../../../docs/prd/GAPS.md)).
 *
 * ```
 * ./gradlew reindexSearch     # 시드 뒤 · 색인이 깨졌을 때
 * ```
 *
 * `verifyInvariants`(이슈 016)와 같은 모양이다 — DB 를 붙잡으므로 `check` 에 매달지 않고,
 * 사람이 부르거나 배포 스텝이 부른다.
 *
 * ## 실패해도 종료 코드를 만들지 않는다
 *
 * 불변식 검증은 **배포 차단**이라 위반이 곧 exit 1 이지만(`NFR-D-01`), 재색인은 고치는
 * 작업이다. 예외가 나면 스프링이 그대로 죽어 스택이 남는다 — 성공/실패를 우리가 다시
 * 코드로 옮길 이유가 없다.
 */
@Component
@ConditionalOnProperty(prefix = "kcocktail.search", name = ["reindex-cli"], havingValue = "true")
class SearchReindexRunner(private val batch: SearchReindexBatch) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val result = batch.run()

        log.info(
            "✓ 재색인 완료 — 칵테일 {}건 공개 · 내림 {}건 · 재료 {}건",
            result.cocktails,
            result.demoted,
            result.ingredients,
        )
    }
}
