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

enum class TaskType(val slug: String, val phase: Phase = Phase.P1A) {

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

    // ── Phase 1b (SPEC-05 §8) ─────────────────────────────────────────────
    //
    // 생성자는 BAR 도메인이라 1b 다. 그래도 **지금 정의한다** —
    // `FR-ADMIN-004` 가 이 둘을 명시적으로 예로 들었으므로 계약에 있어야 하고,
    // 나중에 열거를 늘리면 이 목록을 읽는 쪽(큐 필터 · 어드민 UI)이 그때 깨진다.
    // 조회는 지금도 되고 결과가 빈 것뿐이다 (이슈 028 RED 24·25).

    /** `hours_verified_at` 90일 경과 (`R-F3.1-2`). */
    HOURS_EXPIRED("hours_expired", Phase.P1B),

    /** 인스타 피드에서 온 폐업 신호 (SPEC-05 §8). */
    INSTAGRAM_SIGNAL("instagram_signal", Phase.P1B),
    ;

    /** Phase 1a 배치가 실제로 만드는 종류인가. `resolveMissing` 의 스캔 범위가 이것을 본다. */
    val isPhase1a: Boolean get() = phase == Phase.P1A

    enum class Phase { P1A, P1B }

    companion object {
        fun ofSlug(slug: String): TaskType =
            entries.firstOrNull { it.slug == slug } ?: error("알 수 없는 태스크 종류: $slug")

        /** 조회 필터용. 모르는 슬러그면 `null` — 400 으로 옮기는 것은 호출부의 일이다. */
        fun findBySlug(slug: String): TaskType? = entries.firstOrNull { it.slug == slug }
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
