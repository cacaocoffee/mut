package kr.kcocktail.admin.verification

/**
 * 배치가 찾은 위반 한 건 (SPEC-06 §4.3, `FR-ADMIN-004`).
 *
 * 큐 조회 API·UI 는 이슈 028·050 이다. 여기서는 **쌓는 것까지**.
 */
data class VerificationTask(
    val taskType: TaskType,
    val entityType: String,
    val entityId: Long,

    /** `INV-COCKTAIL-02` · `GATE-COCKTAIL-01` 같은 SPEC-02 ID. */
    val code: String,

    /** 사람이 읽을 설명. 어느 필드가 왜 걸렸는지. */
    val detail: Map<String, Any?> = emptyMap(),
)

enum class TaskType(val slug: String) {

    /** 앱 강제 불변식 위반 (SPEC-06 §4.3). */
    INVARIANT_VIOLATION("invariant_violation"),

    /**
     * **게이트를 통과하지 않은 `published`** (`NFR-D-02`).
     *
     * 불변식 위반과 나누는 이유: 이쪽은 데이터가 틀린 게 아니라 **경로가 뚫린** 것이다.
     * 고칠 곳이 데이터가 아니라 코드라 대응이 다르다.
     */
    GATE_BYPASS("gate_bypass"),

    /** `slug` 변경 흔적 (`NFR-D-04` — 있으면 0건이 아니다). */
    SLUG_CHANGED("slug_changed"),
    ;

    companion object {
        fun ofSlug(slug: String): TaskType =
            entries.firstOrNull { it.slug == slug } ?: error("알 수 없는 태스크 종류: $slug")
    }
}

/**
 * 배치 한 번의 결과 (RED 25).
 *
 * `batch_run` 테이블을 만들지 않는다 — DECISIONS §2 의 D-2 가 "요구 없는 테이블을
 * 만들지 않는다"로 닫혔다. 실행 이력은 이 값과 로그로 남는다.
 */
data class VerificationRun(
    val scannedCocktails: Int,
    val scannedIngredients: Int,
    val opened: Int,
    val reopened: Int,
    val resolved: Int,
    val violations: List<VerificationTask>,
) {
    val hasViolations: Boolean get() = violations.isNotEmpty()
}
