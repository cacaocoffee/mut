package kr.kcocktail.common.security.csrf

import kr.kcocktail.common.web.ApiPaths

/**
 * CSRF 면제 경로. **하나뿐이고 코드 상수다** (SPEC-08 §4.3).
 *
 * ## 왜 `/events` 만인가
 *
 * 인증이 필요 없고 부작용이 집계뿐이다. 남이 내 브라우저로 이벤트를 하나 더 쏴도
 * 잃을 것이 없다 — 대신 **레이트 리밋(120rpm, 세션 기준)이 방어**한다.
 *
 * ## 왜 설정이 아니라 상수인가
 *
 * 설정 파일로 빼면 늘어난다. "이 엔드포인트만 잠깐" 이 쌓이는 데 오래 걸리지 않고,
 * 늘어난 목록은 리뷰에 보이지 않는다. **여기 한 줄을 추가하려면 커밋이 필요하고,
 * 그 커밋에 이유를 적게 된다.**
 */
object CsrfExemptions {

    /** SPEC-08 §4.3 의 유일한 예외. */
    val PATHS: List<String> = listOf("${ApiPaths.BASE}/events")

    fun isExempt(path: String): Boolean = PATHS.any { path == it || path.startsWith("$it/") }
}
