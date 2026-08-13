package kr.kcocktail.ingredient.internal

import kr.kcocktail.ingredient.api.IngredientProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/** [IngredientProperties] 를 빈으로 올린다 (이슈 026 RED 20). */
@Configuration
@EnableConfigurationProperties(IngredientProperties::class)
class IngredientConfig
