package kr.kcocktail.search.facet

import org.springframework.jdbc.core.JdbcTemplate

/**
 * 패싯 테스트가 쓰는 고정 코퍼스 (ISSUE-019).
 *
 * **이슈 018 의 목록 테스트와 같은 8종이다.** 두 API 가 같은 데이터를 봐야
 * RED 14(카운트와 실제 목록 결과 일치)가 뜻을 갖는다 — 데이터가 다르면
 * 수가 맞아도 우연이고, 어긋나도 어느 쪽이 틀렸는지 알 수 없다.
 *
 * | 슬러그 | 기주 | 스타일(전체) | 방법 | 당도 | 도수 | 향·맛 |
 * |---|---|---|---|---|---|---|
 * | `negroni` | gin | spirit-forward | stir | semi_dry | 24 | bitter · herbal |
 * | `gin-tonic` | gin | highball | build | dry | 10 | citrus |
 * | `moscow-mule` | vodka | highball · sour | build | semi_sweet | 12 | citrus · spicy |
 * | `whisky-sour` | whisky | sour | shake | semi_dry | 20 | sour · citrus |
 * | `espresso-martini` | vodka | spirit-forward | shake | semi_sweet | 22 | bitter · nutty |
 * | `pina-colada` | rum | creamy · tiki | blend | sweet | 13 | fruity · creamy |
 * | `makgeolli-punch` | korean | sour | shake | semi_sweet | 8 | fruity |
 * | `virgin-mojito` | non-alcoholic | highball | build | sweet | 0 | citrus · herbal |
 *
 * `tiki` 는 진과 함께인 것이 없다 — RED 9(0 인데 키는 있다)를 만드는 자리다.
 * `frozen` · `shot` · `spritz` · `hot` 은 아예 없다 — RED 10(키 자체가 없다)이다.
 */
object FacetCorpus {

    /** `styles.first()` 가 `style_primary` 다 — 복합 FK 가 포함을 강제한다 (V009). */
    data class Row(
        val slug: String,
        val nameKo: String,
        val nameEn: String,
        val base: String,
        val styles: List<String>,
        val method: String,
        val sweet: String,
        val abv: Int,
        val flavors: List<String>,
    )

    fun rows(): List<Row> = listOf(
        Row("negroni", "네그로니", "Negroni", "gin",
            listOf("spirit-forward"), "stir", "semi_dry", 24, listOf("bitter", "herbal")),
        Row("gin-tonic", "진토닉", "Gin and Tonic", "gin",
            listOf("highball"), "build", "dry", 10, listOf("citrus")),
        Row("moscow-mule", "모스코 뮬", "Moscow Mule", "vodka",
            listOf("highball", "sour"), "build", "semi_sweet", 12, listOf("citrus", "spicy")),
        Row("whisky-sour", "위스키 사워", "Whisky Sour", "whisky",
            listOf("sour"), "shake", "semi_dry", 20, listOf("sour", "citrus")),
        Row("espresso-martini", "에스프레소 마티니", "Espresso Martini", "vodka",
            listOf("spirit-forward"), "shake", "semi_sweet", 22, listOf("bitter", "nutty")),
        Row("pina-colada", "피나 콜라다", "Pina Colada", "rum",
            listOf("creamy", "tiki"), "blend", "sweet", 13, listOf("fruity", "creamy")),
        Row("makgeolli-punch", "막걸리 펀치", "Makgeolli Punch", "korean",
            listOf("sour"), "shake", "semi_sweet", 8, listOf("fruity")),
        // INV-COCKTAIL-06 — 무알콜 ⟺ abv = 0. DB CHECK 가 양방향으로 강제한다.
        Row("virgin-mojito", "버진 모히토", "Virgin Mojito", "non-alcoholic",
            listOf("highball"), "build", "sweet", 0, listOf("citrus", "herbal")),
    )

    fun insert(jdbc: JdbcTemplate, row: Row, status: String = "published") {
        val id = jdbc.queryForObject(
            """
            INSERT INTO cocktail (slug, name_ko, name_en, summary,
                base_spirit, style_primary, method, sweetness, glass_type,
                abv_calculated, tasting_note, status, published_at)
            VALUES (?, ?, ?, '한 줄 요약', ?, ?, ?, ?, '글라스', ?, '향과 맛', ?,
                CASE WHEN ? = 'draft' THEN NULL ELSE now() END)
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            row.slug, row.nameKo, row.nameEn,
            row.base, row.styles.first(), row.method, row.sweet, row.abv,
            status, status,
        )!!
        row.styles.forEach { jdbc.update("INSERT INTO cocktail_style VALUES (?, ?)", id, it) }
        row.flavors.forEach { jdbc.update("INSERT INTO cocktail_aroma_tag VALUES (?, ?)", id, it) }
    }
}
