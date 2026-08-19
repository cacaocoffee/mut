package kr.mut

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * MUT API.
 *
 * SPEC-05 §1 — 모듈러 모놀리스. 단일 배포지만 모듈 경계는 코드로 지킨다 (PRIN-T03).
 * 도메인 모듈은 kr.mut.<module> 아래 있고, 모듈 간 호출은 api 패키지로만 한다.
 */
@SpringBootApplication
class MutApplication

fun main(args: Array<String>) {
    runApplication<MutApplication>(*args)
}
