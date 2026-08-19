package kr.mut.common.logging

/**
 * 로그에 남기면 안 되는 요청 파라미터 (ISSUE-033 · SPEC-08 §5.2 · `PRIN-D04`).
 *
 * > 거리 계산은 서버가 하되 **좌표를 로그에 남기지 않도록 요청 로깅에서 해당 파라미터를
 * > 마스킹**한다. — SPEC-08 §5.2
 *
 * ## 왜 지금 만드나 — Phase 1a 에는 좌표를 받는 기능이 없다
 *
 * "내 주변 바" 는 Phase 1b 다. 그런데 `NFR-SEC-04`(좌표가 DB·로그 어디에도 없다)가
 * **코드·로그 검사**를 측정 방법으로 못박았고, `FR-USER-006` 은 P0 다.
 *
 * 1b 에서 좌표를 받기 시작할 때 마스킹이 **이미 있어야** 한다. 그때 만들면
 * "일단 되게 하고 나중에" 가 되고, 그 사이에 남은 로그는 되돌릴 수 없다.
 *
 * ## 목록이 코드 상수다
 *
 * 설정으로 빼면 운영 중에 꺼진다. `CsrfExemptions` 와 같은 이유로 —
 * **여기 한 줄을 고치려면 커밋이 필요하고, 그 커밋에 이유를 적게 된다.**
 */
object SensitiveParams {

    /**
     * 마스킹 대상.
     *
     * `PRIN-D04` 가 금지하는 것은 **유저의 좌표**다. 파라미터 이름은 요청이 정하므로
     * 흔한 표기를 넓게 잡는다 — 좁게 잡고 새는 것보다 넓게 잡고 로그가 조금 덜 친절한 편이 낫다.
     */
    val MASKED: Set<String> = setOf(
        "lat", "lng", "lon", "latitude", "longitude",
        "coord", "coords", "location", "geo", "position",
    )

    const val MASK = "***"

    fun isMasked(name: String): Boolean = name.lowercase() in MASKED

    /** 파라미터 맵. 값만 가린다 — **키는 남긴다**: 무엇을 받았는지는 알아야 디버깅이 된다. */
    fun mask(params: Map<String, String?>): Map<String, String?> =
        params.mapValues { (name, value) -> if (isMasked(name)) MASK else value }

    /**
     * 쿼리스트링을 통째로 받아 가린다.
     *
     * `HttpServletRequest.getQueryString()` 을 그대로 로그에 넣는 코드가 이 프로젝트에서
     * 새는 가장 쉬운 경로다 — URI 만 남기면 안전하지만, 디버깅하다 보면 쿼리를 붙이고 싶어진다.
     * 붙이되 **여기를 거치게** 한다.
     */
    fun maskQueryString(query: String?): String? {
        if (query.isNullOrBlank()) return query

        return query.split('&').joinToString("&") { pair ->
            val name = pair.substringBefore('=')
            if (isMasked(name)) "$name=$MASK" else pair
        }
    }

    /** `/api/v1/bars?lat=***&lng=***` 형태. 로그에 경로와 쿼리를 함께 남길 때 쓴다. */
    fun maskUri(path: String, query: String?): String =
        maskQueryString(query)?.let { "$path?$it" } ?: path
}
