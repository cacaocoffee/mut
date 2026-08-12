package kr.kcocktail.common.revalidate

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * 설정을 **기동에서** 검사한다 (RED 19, DECISIONS §1 "로컬 비활성화 허용, 운영 필수").
 *
 * 발행 시점에 발견하면 늦다. 훅이 조용히 401 로 실패하고, `NFR-R-03` 때문에 발행은
 * 성공한 채 넘어간다 — **아무도 모르는 사이 프론트만 안 바뀐다.**
 * 설정 실수는 기동에서 시끄럽게 터지는 편이 낫다.
 */
@Component
class RevalidateStartupCheck(
    private val properties: RevalidateProperties,
    private val environment: Environment,
) : InitializingBean {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterPropertiesSet() {
        val isProd = PROD_PROFILE in environment.activeProfiles

        if (isProd && !properties.enabled) {
            error(
                "운영에서는 재생성 훅을 끌 수 없다 — 발행해도 프론트가 안 바뀐다 " +
                    "(kcocktail.revalidate.enabled).",
            )
        }

        if (properties.enabled && !properties.isConfigured) {
            // 무엇이 비었는지만 말한다. 값은 찍지 않는다 (RED 20)
            error(
                "재생성 훅이 켜져 있는데 설정이 비었다: " +
                    listOfNotNull(
                        "url".takeIf { properties.url.isBlank() },
                        "secret".takeIf { properties.secret.isBlank() },
                    ).joinToString(" · "),
            )
        }

        if (!properties.enabled) {
            log.info("재생성 훅 비활성화 — 발행해도 프론트를 부르지 않는다 (로컬 기본값)")
        }
    }

    companion object {
        const val PROD_PROFILE = "prod"
    }
}
