package kr.mut.admin.verification

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * CI 배포 게이트 (RED 27·28, `NFR-D-01` "실패 시 배포 차단").
 *
 * ```
 * ./gradlew verifyInvariants     # 위반이 있으면 exit 1
 * ```
 *
 * `npm run check` 와 대칭이다. 프론트는 시드 코퍼스를, 이쪽은 **DB 의 발행분**을 본다.
 *
 * ## `exitProcess` 를 부르지 않는다
 *
 * [ExitCodeGenerator] 로 코드만 남기고 종료는 스프링에 맡긴다. 직접 죽이면
 * 커넥션 풀도 트랜잭션도 정리되지 않고, 무엇보다 **테스트가 프로세스째 사라진다** —
 * RED 28("위반이면 비정상 종료 코드")을 확인할 방법이 없어진다.
 */
@Component
@ConditionalOnProperty(prefix = "mut.verification", name = ["cli"], havingValue = "true")
class VerifyInvariantsRunner(
    private val batch: InvariantVerificationBatch,
) : ApplicationRunner, ExitCodeGenerator {

    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var exitCode: Int = 0

    override fun run(args: ApplicationArguments) {
        val result = batch.run()

        if (!result.hasViolations) {
            log.info("✓ 불변식 위반 없음 — 발행분 {}건 · 재료 {}건", result.scannedCocktails, result.scannedIngredients)
            exitCode = OK
            return
        }

        // 전부 찍는다. 하나씩 고치고 다시 돌리게 하지 않는다 (`FR-ADMIN-003` 과 같은 취지)
        log.error("✗ 불변식 위반 {}건 — 배포를 막는다 (NFR-D-01)", result.violations.size)
        result.violations.forEach {
            log.error(
                "  [{}] {} #{} — {}",
                it.code,
                it.entityType,
                it.entityId,
                it.detail["message"] ?: it.detail["why"],
            )
        }
        exitCode = VIOLATIONS_FOUND
    }

    override fun getExitCode(): Int = exitCode

    companion object {
        const val OK = 0
        const val VIOLATIONS_FOUND = 1
    }
}
