package kr.kcocktail.common.openapi

import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import kr.kcocktail.common.taxonomy.BaseSpirit
import kr.kcocktail.common.taxonomy.FlavorKey
import kr.kcocktail.common.taxonomy.Slugged
import kr.kcocktail.common.taxonomy.StyleKey
import kr.kcocktail.common.taxonomy.SweetLevel
import kr.kcocktail.common.taxonomy.Technique
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 분류 축 5종을 OpenAPI `components.schemas` 에 **항상** 싣는다 (`PRIN-T02`, ISSUE-004).
 *
 * ## 왜 명시적으로 넣나
 *
 * springdoc 은 엔드포인트가 참조하는 타입만 스키마로 뽑는다. Phase 1a 초반에는
 * 엔드포인트가 아직 없어서 **분류 축이 계약에서 통째로 빠진다** — 그러면 프론트가
 * 생성 타입을 못 받고, `types.ts` 를 계속 손으로 유지하게 된다. 그게 `PRIN-T02` 가 막으려는 상황이다.
 *
 * 분류 축은 특정 엔드포인트의 부속이 아니라 **계약 자체의 어휘**다. 엔드포인트가 생기면
 * 자연스럽게 참조되고, 그때도 이 스키마가 그대로 정본이다.
 *
 * ## 값은 슬러그다
 *
 * enum 이름(`NON_ALCOHOLIC`)이 아니라 슬러그(`non-alcoholic`)를 낸다. DB 와 URL 이 쓰는 것이
 * 슬러그이고, 이름을 내보내면 프론트가 다시 매핑 표를 들고 있어야 한다.
 * 한국어 레이블은 `x-labels` 확장에 실어 보낸다 — 표시용이라 타입에는 들어가지 않는다.
 */
@Configuration
class TaxonomySchemaCustomizer {

    @Bean
    fun taxonomySchemas() = OpenApiCustomizer { openApi ->
        val schemas = openApi.components.schemas ?: mutableMapOf<String, Schema<Any>>()
            .also { openApi.components.schemas = it }

        schemas["BaseSpirit"] = slugEnum(BaseSpirit.entries, "축 1 · 기주 (단일값 필수, R-C-1)")
        schemas["StyleKey"] = slugEnum(StyleKey.entries, "축 2 · 스타일 (복수, style_primary 필수)")
        schemas["Technique"] = slugEnum(Technique.entries, "축 3 · 메이킹 방법 (단일값 필수)")
        schemas["FlavorKey"] = slugEnum(FlavorKey.entries, "향 태그 1~3개 (R-F1.2-1). 카테고리가 아니다")
        schemas["SweetLevel"] = slugEnum(SweetLevel.entries, "당도 4단계")
    }

    private fun slugEnum(values: List<Slugged>, description: String): Schema<*> =
        StringSchema().apply {
            this.description = description
            values.forEach { addEnumItemObject(it.slug) }
            // 표시 레이블은 타입이 아니다. 필요한 쪽이 확장에서 읽어 간다.
            addExtension("x-labels", values.associate { it.slug to it.labelKo })
        }
}
