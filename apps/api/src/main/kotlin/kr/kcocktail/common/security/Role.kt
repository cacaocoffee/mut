package kr.kcocktail.common.security

import java.time.Duration

/**
 * SPEC-06 §3.5 · SPEC-08 §1 — 역할 4종.
 *
 * ## 왜 `user` 가 아니라 `common` 에 있나
 *
 * 역할을 쓰는 곳이 셋이다 — 세션 수명(SPEC-08 §4.1) · 권한 매트릭스(이슈 006) · 감사 로그(이슈 014).
 * `user/domain` 에 두면 **공용 커널이 도메인 모듈을 되참조**해야 하고, 그 순간 의존이 양방향이 된다
 * (`PRIN-T03`). 역할은 특정 도메인의 소유물이 아니라 **보안 어휘**다.
 *
 * 표를 저장하는 `user_role` 은 그대로 `user` 모듈에 있다 (SPEC-06 §3.5).
 *
 * ## 누적되지 않는다
 *
 * `editor` 가 `admin` 권한을 갖지 않는다 (SPEC-08 §1). 계층이 아니라 **집합**이다.
 * 한 사람이 에디터이면서 관리자여야 하면 두 행을 준다 — 팀이 작아 실제로 생기는 일이다.
 *
 * ## 세션 수명이 역할에 달렸다
 *
 * `editor`·`admin` 은 **8시간 절대**, 나머지는 30일 rolling (SPEC-08 §4.1).
 * 발행 권한이 곧 콘텐츠 신뢰라서, 공용 PC 에 남은 세션으로 아무나 발행할 수 있으면 안 된다.
 */
enum class Role(val code: String) {
    MEMBER("member"),
    EDITOR("editor"),

    /** Phase 1b. enum 에는 두되 1a 에서 부여하지 않는다 (권한 매트릭스 축소 — 이슈 006). */
    PARTNER_OWNER("partner_owner"),

    ADMIN("admin"),
    ;

    /** 발행 권한을 가진 역할. 세션을 짧게 가져가는 기준이다. */
    val isElevated: Boolean get() = this == EDITOR || this == ADMIN

    companion object {
        /** SPEC-08 §4.1 — 일반 사용자. 활동하면 갱신된다. */
        val ROLLING_30D: Duration = Duration.ofDays(30)

        /** SPEC-08 §4.1 — `editor`·`admin`. **활동해도 연장되지 않는다.** */
        val ABSOLUTE_8H: Duration = Duration.ofHours(8)

        fun ofCode(code: String): Role =
            entries.firstOrNull { it.code == code } ?: error("알 수 없는 역할: $code")

        /**
         * 역할 집합의 세션 수명.
         *
         * **짧은 쪽이 이긴다** — `editor` + `member` 면 8시간이다 (DECISIONS §1).
         * 긴 쪽을 택하면 `member` 를 함께 가진 에디터가 8시간 규칙을 우회하게 된다.
         */
        fun sessionLifetime(roles: Set<Role>): Duration =
            if (roles.any { it.isElevated }) ABSOLUTE_8H else ROLLING_30D

        /** 발급 시각 기준으로 끊는가. `editor`·`admin` 만 그렇다. */
        fun isAbsoluteExpiry(roles: Set<Role>): Boolean = roles.any { it.isElevated }
    }
}
