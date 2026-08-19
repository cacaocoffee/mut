package kr.kcocktail.common.web.error

import io.swagger.v3.oas.annotations.media.Schema

/**
 * `422` 응답의 모양 (SPEC-07 §1.4 · G-39).
 *
 * ## 문서용이다 — 런타임은 이 클래스를 만들지 않는다
 *
 * 실제 응답은 [ApiExceptionHandler] 가 스프링의 `ProblemDetail` 로 만들고, `violations` 는
 * **동적 속성**으로 붙는다 (`setProperty`). 그 맵은 열려 있어서 OpenAPI 생성기가 모양을 모르고,
 * 그래서 생성 TS 에 `violations` 타입이 없었다 — 프론트가 같은 모양을 손으로 적고 있었다.
 *
 * 손으로 적은 타입은 **서버가 필드 이름을 바꿔도 빌드가 안 깨진다.** `field` 를 `path` 로
 * 바꾸면 발행 실패 패널이 조용히 빈 목록을 그린다. 이 클래스는 그 구멍을 막는다 —
 * 컨트롤러가 `@ApiResponse` 로 가리키면 계약에 실리고, 계약이 바뀌면 프론트 빌드가 깨진다.
 *
 * **모양을 바꿀 때는 [ApiExceptionHandler] 와 함께 바꾼다.** 둘이 갈라지면 계약이 거짓말을 한다.
 */
@Schema(
    name = "ValidationProblem",
    description = "도메인 규칙 위반. `violations` 에 실패한 항목을 **전부** 담는다 (FR-ADMIN-003)",
)
data class ValidationProblemResponse(
    @field:Schema(description = "RFC 7807 문제 유형 URI", example = "/problems/validation")
    val type: String,

    @field:Schema(description = "짧은 제목", example = "발행 조건 미충족")
    val title: String,

    @field:Schema(description = "HTTP 상태", example = "422")
    val status: Int,

    @field:Schema(description = "사람이 읽을 설명")
    val detail: String?,

    @field:Schema(description = "요청 경로", example = "/api/v1/admin/cocktails/1/publish")
    val instance: String?,

    /**
     * 실패한 항목 **전부**. 하나씩 고치게 하지 않는다 (`FR-ADMIN-003`).
     *
     * `code` 는 `INV-`·`GATE-` ID 를 그대로 쓴다 — 클라이언트가 **문구가 아니라 코드로 분기**한다.
     */
    @field:Schema(description = "실패한 항목 전부. 비어 있지 않다")
    val violations: List<Violation>,
)
