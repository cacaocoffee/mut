package kr.mut.common.web.error

/**
 * SPEC-07 §1.4 의 `violations` 한 건.
 *
 * `field` 는 없을 수 있다 — 여러 필드에 걸친 규칙(`INV-COCKTAIL-03` 같은)은 가리킬 곳이 하나가 아니다.
 */
data class Violation(
    val code: String,
    val field: String?,
    val message: String,
) {
    companion object {
        fun of(code: ViolationCode, message: String, field: String? = null) =
            Violation(code.code, field, message)
    }
}
