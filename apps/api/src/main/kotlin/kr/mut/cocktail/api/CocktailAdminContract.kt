package kr.mut.cocktail.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant

/**
 * 어드민이 칵테일을 다루는 **유일한 창구** (`PRIN-T03` · SPEC-05 §3).
 *
 * `admin` 은 `cocktail` 의 엔티티도 리포지토리도 볼 수 없다 — 방향표상 의존은 허용되지만
 * 경로는 `api` 뿐이다 (경계 테스트 RED 3). 상태 전이까지 여기로 내보내는 이유는,
 * 어드민이 `PublishService` 를 직접 부르면 그 경로가 두 번째 발행 문이 되기 때문이다.
 */
interface CocktailAdminFacade {

    fun create(request: CreateCocktailRequest): AdminCocktailResponse

    fun update(id: Long, request: UpdateCocktailRequest): AdminCocktailResponse

    /** `draft` 포함. 어드민만 쓴다 — 공개 조회는 여전히 발행분만 본다 (SPEC-07 §5). */
    fun find(id: Long): AdminCocktailResponse

    /**
     * 목록 (`FR-ADMIN-002`). `draft` 포함이 이 경로의 존재 이유다 — 초안이 몇 건
     * 밀려 있는지가 에디터의 오늘 할 일이다. 웹 어드민 목록(ISSUE-047)이 읽는다.
     * 공개 목록(발행분만)과 헷갈리지 않게 상태 필터는 슬러그 그대로 받는다.
     */
    fun list(status: String?, limit: Int): AdminCocktailListResponse

    /** 게이트 6종을 전부 검사한다. 실패는 `violations` 를 전부 담아 던진다. */
    fun publish(id: Long): AdminCocktailResponse

    fun unpublish(id: Long): AdminCocktailResponse

    fun archive(id: Long): AdminCocktailResponse
}

/**
 * 어드민 칵테일 생성 요청 (ISSUE-025 · `FR-ADMIN-001`).
 *
 * ## 여기 없는 것이 중요하다
 *
 * `status` · `publishedAt` 이 **입력에 없다** (RED 33·35). 발행은 전용 엔드포인트만이
 * 할 수 있고, 그 경로가 게이트를 거친다 (`PRIN-T05` · 이슈 013).
 * 필드를 두면 `PATCH` 로 게이트를 우회하는 길이 생기고, 그때 `NFR-D-02`
 * ("게이트를 우회한 published 0건")를 지킬 방법이 없다.
 *
 * **타입에 없으면 우회할 수 없다.** 서비스에서 걸러 내는 것보다 강하다.
 */
data class CreateCocktailRequest(
    @field:NotBlank @field:Size(max = 120) val slug: String,
    @field:NotBlank @field:Size(max = 120) val nameKo: String,
    @field:NotBlank @field:Size(max = 120) val nameEn: String,
    @field:NotBlank val summary: String,

    // 분류 3축 — 전부 필수다 (`R-C-1`). null 을 허용하면 카테고리에서 누락된다
    @field:NotBlank val baseSpirit: String,
    @field:NotBlank val stylePrimary: String,
    @field:NotBlank val method: String,
    @field:NotBlank val sweetness: String,
    @field:NotBlank val glassType: String,

    /** `stylePrimary` 를 반드시 포함해야 한다 (`INV-COCKTAIL-03`, 복합 FK 가 강제). */
    val styles: List<String> = emptyList(),

    /** 1~3개 (`INV-COCKTAIL-04`). 발행 게이트가 검사한다 */
    val aromaTags: List<String> = emptyList(),

    val aliases: List<String> = emptyList(),
    val tastingNote: String? = null,
    val story: String? = null,
    val isClassic: Boolean = false,
    val prepTimeMin: Short? = null,
    val abvOverride: BigDecimal? = null,
    val originYear: String? = null,
    val originPlace: String? = null,
    val originCreator: String? = null,
)

/**
 * 수정 요청. **`null` 은 "안 바꾼다"** 는 뜻이다.
 *
 * `slug` 는 최초 발행 이후 못 바꾼다 (RED 34 · `INV-COCKTAIL-05`) — 여기 있지만
 * 엔티티의 [kr.mut.cocktail.domain.Cocktail.changeSlug] 가 잠금을 본다.
 * 필드를 빼지 않은 이유: `draft` 단계에서는 바꿀 수 있어야 하고, 거부된 시도가
 * 감사에 남아야 한다 (`NFR-D-04`).
 */
data class UpdateCocktailRequest(
    val slug: String? = null,
    val nameKo: String? = null,
    val nameEn: String? = null,
    val summary: String? = null,
    val baseSpirit: String? = null,
    val stylePrimary: String? = null,
    val method: String? = null,
    val sweetness: String? = null,
    val glassType: String? = null,
    val styles: List<String>? = null,
    val aromaTags: List<String>? = null,
    val aliases: List<String>? = null,
    val tastingNote: String? = null,
    val story: String? = null,
    val isClassic: Boolean? = null,
    val prepTimeMin: Short? = null,
    val abvOverride: BigDecimal? = null,
)

/**
 * 어드민 응답 (SPEC-07 §1.1 — **어드민만 `id` 를 쓴다**).
 *
 * 공개 응답과 다른 것 셋이 의도다.
 *
 * | 필드 | 공개 | 어드민 | 왜 |
 * |---|---|---|---|
 * | `id` | ❌ | ✅ | 어드민 경로가 `id` 로 지목한다 |
 * | `status` | ❌ | ✅ | 에디터는 지금 상태를 알아야 한다 |
 * | `abvCalculated` · `abvOverride` | ❌ | ✅ **구분** | 오버라이드가 걸렸는지 알아야 고칠 수 있다 (이슈 011) |
 */
data class AdminCocktailResponse(
    val id: Long,
    val slug: String,
    val nameKo: String,
    val nameEn: String,
    val summary: String,
    val status: String,
    val publishedAt: Instant?,

    val baseSpirit: String,
    val stylePrimary: String,
    val styles: List<String>,
    val method: String,
    val sweetness: String,
    val aromaTags: List<String>,
    val glassType: String,
    val aliases: List<String>,

    /** 계산값과 수동값을 **나눠서** 준다. 공개 응답은 표시값 하나뿐이다 (DECISIONS §1.5). */
    val abvCalculated: BigDecimal?,
    val abvOverride: BigDecimal?,

    val tastingNote: String?,
    val story: String?,
    val isClassic: Boolean,
    val prepTimeMin: Short?,
)

/** 목록 봉투. 공개 목록과 같은 모양(`items`)이라 화면 코드가 같은 방식으로 읽는다. */
data class AdminCocktailListResponse(
    val items: List<AdminCocktailResponse>,
)

/** 발행 성공 응답 (SPEC-07 §3.4). */
data class PublishResponse(
    val slug: String,
    val status: String,
    val publishedAt: Instant?,
)
