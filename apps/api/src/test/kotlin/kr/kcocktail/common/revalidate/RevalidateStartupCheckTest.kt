package kr.kcocktail.common.revalidate

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment

/**
 * ISSUE-015 RED 18~20 — 시크릿 (DECISIONS §1 "로컬 비활성화 허용, 운영 필수").
 *
 * ## 왜 기동에서 보나
 *
 * 발행 시점에 발견하면 늦다. 훅이 401 로 조용히 실패하고 `NFR-R-03` 때문에 발행은
 * 성공한 채 넘어간다 — **아무도 모르는 사이 프론트만 안 바뀐다.**
 */
class RevalidateStartupCheckTest {

    @Test
    fun `RED19 - 로컬에서는 꺼 둘 수 있다`() {
        assertThatCode { check(local(), disabled()) }.doesNotThrowAnyException()
    }

    @Test
    fun `RED19 - 운영에서는 끌 수 없다`() {
        assertThatThrownBy { check(prod(), disabled()) }
            .hasMessageContaining("운영에서는 재생성 훅을 끌 수 없다")
    }

    @Test
    fun `RED18-19 - 켜져 있는데 시크릿이 없으면 기동이 실패한다`() {
        assertThatThrownBy { check(prod(), configured(secret = "")) }
            .hasMessageContaining("secret")
    }

    @Test
    fun `RED18-19 - 켜져 있는데 URL 이 없으면 기동이 실패한다`() {
        assertThatThrownBy { check(prod(), configured(url = "")) }
            .hasMessageContaining("url")
    }

    /**
     * RED 20 — 실패 메시지가 **무엇이 비었는지**만 말한다.
     *
     * 기동 실패 로그는 대개 그대로 수집기로 간다. 거기에 시크릿을 실어 보내면
     * 설정을 고치는 동안 값이 밖에 남는다.
     */
    @Test
    fun `RED20 - 실패 메시지에 시크릿 값이 없다`() {
        val thrown = runCatching { check(prod(), configured(url = "", secret = "leak-me")) }
            .exceptionOrNull()!!

        assertThat(thrown.message).doesNotContain("leak-me")
    }

    @Test
    fun `설정이 갖춰지면 통과한다`() {
        assertThatCode { check(prod(), configured()) }.doesNotThrowAnyException()
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun check(environment: MockEnvironment, properties: RevalidateProperties) =
        RevalidateStartupCheck(properties, environment).afterPropertiesSet()

    private fun prod() = MockEnvironment().apply { setActiveProfiles("prod") }

    private fun local() = MockEnvironment()

    private fun disabled() = RevalidateProperties(enabled = false)

    private fun configured(
        url: String = "https://k-cocktail.example",
        secret: String = "s3cr3t",
    ) = RevalidateProperties(enabled = true, url = url, secret = secret)
}
