package kr.mut.common.taxonomy

/**
 * 분류 축 5종. **이 파일이 정본이다** (`PRIN-T02`).
 *
 * 언어가 둘이라 분류 축이 두 곳에 존재한다. 손으로 양쪽을 맞추면 반드시 어긋난다.
 * 그래서 Kotlin 이 정본이고 TS 는 OpenAPI 를 거친 **생성물**이다.
 * `packages/domain/src/types.ts` 는 프로토타입 산물이며 이슈 037 이 생성물로 대체한다.
 *
 * ## 슬러그가 값이다
 *
 * 프로토타입은 한국어 리터럴이 타입 값이고 슬러그가 별도 맵이었다. 여기서는 반대다 —
 * **슬러그가 값이고 한국어는 표시 레이블**이다. DB 도 슬러그를 저장한다 (SPEC-06 §3.1).
 *
 * 슬러그는 그대로 카테고리 URL 이 된다. **노출된 뒤에는 리다이렉트 없이 못 바꾼다**
 * (ADR-0002 · `PRIN-D02`).
 */
interface Slugged {
    val slug: String
    val labelKo: String
}

/**
 * 축 1 · 기주 — 단일값 필수 (`R-C-1` · `INV-COCKTAIL-01`).
 *
 * ADR-0002 §4 가 PRD 5.1 에서 두 곳을 고쳤다.
 * - `soju` → **`korean`** : 막걸리·문배주를 소주로 부르는 건 부정확하다
 * - 표시명 `데킬라` → `데킬라 · 메즈칼` : 슬러그 `agave` 범위에 맞춤
 */
enum class BaseSpirit(override val slug: String, override val labelKo: String) : Slugged {
    GIN("gin", "진"),
    VODKA("vodka", "보드카"),
    WHISKY("whisky", "위스키"),
    RUM("rum", "럼"),
    AGAVE("agave", "데킬라 · 메즈칼"),
    BRANDY("brandy", "브랜디"),
    LIQUEUR("liqueur", "리큐르"),
    WINE("wine", "와인 · 스파클링"),
    KOREAN("korean", "전통주"),
    NON_ALCOHOLIC("non-alcoholic", "무알콜"),
    ;

    /** `INV-COCKTAIL-06` — `non-alcoholic` ⟺ `abv = 0`. */
    val isNonAlcoholic: Boolean get() = this == NON_ALCOHOLIC

    companion object : SluggedLookup<BaseSpirit>(entries)
}

/**
 * 축 2 · 스타일 — 복수, `style_primary` 필수 (`R-C-1` · `INV-COCKTAIL-02`·`03`).
 *
 * 레시피 **구조** 기준이다. 시대 구분(클래식/모던)으로 잡으면 필터로 쓸모가 없다 (ADR-0002 §3).
 * `spritz` · `hot` · `frozen` · `shot` 은 현재 24종에 해당 항목이 없지만 enum 에는 둔다 —
 * 카테고리 URL 이 되므로 목록은 완전해야 한다.
 */
enum class StyleKey(override val slug: String, override val labelKo: String) : Slugged {
    HIGHBALL("highball", "하이볼"),
    SOUR("sour", "사워"),
    SPIRIT_FORWARD("spirit-forward", "스피릿 포워드"),
    SPRITZ("spritz", "스프리츠"),
    TIKI("tiki", "티키"),
    CREAMY("creamy", "크리미"),
    HOT("hot", "핫"),
    FROZEN("frozen", "프로즌"),
    SHOT("shot", "샷"),
    ;

    companion object : SluggedLookup<StyleKey>(entries)
}

/**
 * 축 3 · 메이킹 방법 — 단일값 필수 (`R-C-1`).
 *
 * 실질 가치는 **난이도 프록시**다. `build` 만 켜면 도구 없이 오늘 만들 수 있는 것만 남는다.
 *
 * > SPEC-06 §3.1 의 컬럼명은 `method` 다. 타입 이름(`Technique`)과 다른 것은 의도다 —
 * > 컬럼은 물리 설계, enum 은 도메인 어휘이고 프로토타입 `types.ts` 도 `Technique` 를 쓴다.
 */
enum class Technique(override val slug: String, override val labelKo: String) : Slugged {
    // **무엇을 하는지로 쓴다** (G-32). `스터`·`빌드`는 그 자체로 뜻이 통하지 않아
    // 화면이 `data.ts` 에 표시 문구를 따로 들고 있었고, API 를 붙이면 같은 자리에
    // 다른 이름이 나왔다. 이름은 한 벌이어야 한다 — 정본은 여기다 (`PRIN-T02`).
    BUILD("build", "잔에서 조립"),
    SHAKE("shake", "흔들어 섞기"),
    STIR("stir", "휘저어 섞기"),
    BLEND("blend", "블렌드"),
    ETC("etc", "기타"),
    ;

    companion object : SluggedLookup<Technique>(entries)
}

/**
 * 필터 축 · 향 — 1~3개 (`R-F1.2-1` · `INV-COCKTAIL-04`). **카테고리가 아니다** (`PRIN-P06`).
 *
 * ADR-0002 §2 가 PRD 6.3 의 9개와 시안의 7개를 합집합했다.
 * `citrus`(향)와 `sour`(맛)를 **분리**한 것이 핵심이다 — 위스키 사워는 `sour` 지만
 * 시트러스 향이 주인공이 아니다.
 *
 * `floral` 은 현재 24종에 0건이다. enum 에는 두되 필터 UI 에 노출하지 않는다 (ADR-0002 §4).
 */
enum class FlavorKey(override val slug: String, override val labelKo: String) : Slugged {
    CITRUS("citrus", "시트러스"),
    SOUR("sour", "산미"),
    FRUITY("fruity", "프루티"),
    FLORAL("floral", "플로럴"),
    HERBAL("herbal", "허브"),
    SPICY("spicy", "스파이시"),
    SMOKY("smoky", "스모키"),
    BITTER("bitter", "쓴맛"),
    NUTTY("nutty", "너티"),
    CREAMY("creamy", "크리미"),
    ;

    companion object : SluggedLookup<FlavorKey>(entries)
}

/**
 * 필터 축 · 당도 4단계.
 *
 * ## 표현이 프로토타입과 다르다
 *
 * `types.ts` 는 `0 | 1 | 2 | 3` 숫자이고 SPEC-06 §3.1 의 `sweetness` 컬럼은
 * `dry`·`semi_dry`·`semi_sweet`·`sweet` 문자열이다. **DB 를 따른다** —
 * 숫자는 의미가 위치에 숨어 마이그레이션에서 뒤집히기 쉽다.
 *
 * 이슈 037 이 프론트를 옮길 때 쓰라고 [level] 을 함께 들고 있는다.
 * 자세한 것은 GAPS G-23.
 */
enum class SweetLevel(
    override val slug: String,
    override val labelKo: String,
    /** 프로토타입 `types.ts` 의 `SweetLevel` 숫자값. 전환용이다. */
    val level: Int,
) : Slugged {
    DRY("dry", "드라이", 0),
    SEMI_DRY("semi_dry", "세미 드라이", 1),
    SEMI_SWEET("semi_sweet", "세미 스위트", 2),
    SWEET("sweet", "스위트", 3),
    ;

    companion object : SluggedLookup<SweetLevel>(entries) {
        fun ofLevel(level: Int): SweetLevel =
            entries.firstOrNull { it.level == level } ?: error("당도 단계가 범위 밖이다: $level")
    }
}

/** 슬러그로 되찾기. DB·API 가 저장하는 것이 슬러그라 이 경로가 항상 필요하다. */
abstract class SluggedLookup<T : Slugged>(private val all: List<T>) {
    val slugs: List<String> get() = all.map { it.slug }

    fun ofSlug(slug: String): T =
        all.firstOrNull { it.slug == slug } ?: error("알 수 없는 슬러그: $slug")

    fun ofSlugOrNull(slug: String): T? = all.firstOrNull { it.slug == slug }
}
