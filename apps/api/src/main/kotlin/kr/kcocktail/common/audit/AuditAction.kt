package kr.kcocktail.common.audit

/**
 * 감사 대상 행위 (SPEC-06 §3.8, `PRIN-T08`).
 *
 * ## Phase 1b·2 값을 지금 넣어 둔다
 *
 * `TIER_CHANGE` · `RANK_CHANGE` · `VERIFY` 는 아직 쓰는 곳이 없다. 그래도 지금 정의한다 —
 * 나중에 열거를 늘리면 이 목록을 읽는 쪽(어드민 조회 · 클라이언트 필터)이 그때 깨진다.
 *
 * `PRIN-T08` 이 감사 대상으로 못박은 4종:
 * 콘텐츠 발행 상태 전이 · 제휴 등급 · 큐레이션 순위 · 바 검증.
 *
 * ## 표에 없는 값 셋
 *
 * | 값 | 왜 |
 * |---|---|
 * | `ARCHIVE` · `RESTORE` | SPEC-02 §8.1 의 전이는 넷인데 표는 `publish`·`unpublish` 둘만 준다 |
 * | `SLUG_CHANGE_ATTEMPT` | `NFR-D-04` 가 "발견 시 즉시 조사"를 요구한다 — **거부된 시도도** 남아야 조사할 것이 있다 |
 *
 * SPEC-06 §3.8 표를 넘어서는 확장이라 `docs/prd/GAPS.md` 에 근거를 남겼다.
 */
enum class AuditAction(val slug: String) {

    // ── 콘텐츠 발행 상태 전이 (SPEC-02 §8.1) ──────────────────────────────
    PUBLISH("publish"),
    UNPUBLISH("unpublish"),
    ARCHIVE("archive"),

    /** `archived → draft`. 다시 손보려고 꺼내는 것이라 발행이 아니다. */
    RESTORE("restore"),

    // ── Phase 1b·2 (`PRIN-T08`) ───────────────────────────────────────────
    TIER_CHANGE("tier_change"),
    RANK_CHANGE("rank_change"),
    VERIFY("verify"),

    /** 거부된 시도를 남긴다 (`NFR-D-04` · `PRIN-D02`). 성공하는 경우는 없어야 정상이다. */
    SLUG_CHANGE_ATTEMPT("slug_change_attempt"),
    ;

    companion object {
        fun ofSlug(slug: String): AuditAction =
            entries.firstOrNull { it.slug == slug } ?: error("알 수 없는 감사 행위: $slug")
    }
}
