package kr.kcocktail.ingredient.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal

/**
 * 어드민이 재료를 다루는 **유일한 창구** (ISSUE-026 · `PRIN-T03`).
 *
 * ## 승인제가 방어선이다
 *
 * `PRIN-D01` — 재료가 문자열이 아니라 **참조**인 이유는 마스터가 오염되면
 * 역검색과 바 연결이 무너지기 때문이다. 승인 단계가 그 오염을 막는다.
 *
 * ## 생성과 승인의 권한이 다르다 — 이 이슈의 요점
 *
 * | 행위 | 권한 | 근거 |
 * |---|---|---|
 * | 생성 | `editor` 이상 | 레시피를 쓰다 새 재료가 필요하면 기다리지 않는다 (DECISIONS §1.1) |
 * | **승인** | **`admin` 만** | SPEC-08 §2 — **권한 분리 자체가 중립성 장치다** (§2.2) |
 *
 * `FR-ADMIN-007` 은 "에디터 승인 단계" 라고 적었지만 SPEC-07 §1.3 이 스코프를 SPEC-08 로
 * 위임했고 그 표가 `admin` 이다. "에디터가 만들고 관리자가 승인" 의 축약으로 읽는다
 * (GAPS G-29).
 */
interface IngredientAdminFacade {

    /** 항상 **승인 대기**로 만들어진다 (SPEC-07 §2.2). */
    fun create(request: CreateIngredientRequest): AdminIngredientResponse

    /**
     * 승인. **`admin` 만** 부를 수 있다 — 권한 판정은 호출부가 한다.
     *
     * 재승인은 409 다 (DECISIONS §1.11) — 멱등하게 넘기면 승인 이력이 흐려진다.
     * **승인 취소는 제공하지 않는다** — 스펙에 없고, 이미 레시피가 참조 중일 수 있다.
     */
    fun approve(id: Long): AdminIngredientResponse

    fun find(id: Long): AdminIngredientResponse

    /** 승인 대기 큐. 에디터가 무엇을 기다리는지 알아야 한다. */
    fun pending(): List<AdminIngredientResponse>

    /**
     * 승인된 재료 수와 상한 (`FR-INGREDIENT-001` · SPEC-02 §3).
     *
     * **차단이 아니라 경고다.** 상한 근거가 "역검색 UX" 이지 데이터 무결성이 아니다
     * (DECISIONS §1.2) — 301번째 재료가 데이터를 깨뜨리지는 않는다.
     */
    fun capacity(): IngredientCapacity

    /**
     * 레시피 편집이 재료를 고를 때 쓰는 목록 (이슈 051).
     *
     * 공개 사전(`/ingredients`)을 쓸 수 없다 — 승인된 것만 나가서 방금 만든 재료를
     * 레시피에 넣을 수가 없다. 여기는 **미승인도 준다** (DECISIONS §1.1).
     */
    fun search(query: String?, limit: Int): List<AdminIngredientResponse>
}

/** `Size` 상한은 `V008__ingredient.sql` 의 컬럼 길이와 같다 — 어긋나면 DB 가 500 으로 막는다. */
data class CreateIngredientRequest(
    @field:NotBlank @field:Size(max = 120) val slug: String,
    @field:NotBlank @field:Size(max = 120) val nameKo: String,
    @field:NotBlank @field:Size(max = 120) val nameEn: String,
    @field:NotBlank val category: String,
    @field:NotBlank val domesticAvailability: String,
    val aliases: List<String> = emptyList(),
    val abv: BigDecimal? = null,
    val description: String? = null,
    /** 미유통이면 필수다 (`INV-INGREDIENT-01`). DB CHECK 가 강제한다 */
    val substituteNote: String? = null,
    val priceBand: String? = null,
)

/**
 * 어드민 응답. 공개 응답과 달리 **`id` 와 `isApproved` 를 담는다** (SPEC-07 §1.1).
 *
 * 공개 사전(이슈 023)은 승인된 것만 내보내므로 `isApproved` 가 무의미하지만,
 * 어드민은 **무엇이 대기 중인지** 알아야 한다.
 */
data class AdminIngredientResponse(
    val id: Long,
    val slug: String,
    val nameKo: String,
    val nameEn: String,
    val category: String,
    val domesticAvailability: String,
    val isApproved: Boolean,
    val aliases: List<String>,
    val abv: BigDecimal?,
    val description: String?,
    val substituteNote: String?,
    val priceBand: String?,
)

/**
 * @param warning 상한을 넘었는가. **승인을 막지 않는다** (RED 19).
 */
data class IngredientCapacity(
    val approved: Long,
    val cap: Long,
    val warning: Boolean,
)
