package kr.kcocktail.search

import kr.kcocktail.search.index.Chosung
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.text.Normalizer

/**
 * ISSUE-017 RED 1~9 — 초성 분해 (`FR-SEARCH-007` · `R-F2.1-4`).
 *
 * ## DB 가 없다
 *
 * `Chosung` 은 순수 함수다. 한글 초성 추출은 경계가 많아 (복합 초성 · 받침 · 정규화)
 * **전수로 고정할 수 있을 때 고정한다** — 컨테이너를 띄우는 테스트로 밀면
 * 경우의 수를 늘리는 값이 비싸지고, 결국 대표 사례 몇 개만 남는다.
 *
 * ## 비한글은 빈 문자열이다
 *
 * DECISIONS §1.9 — "비한글 문자열의 초성 → **빈 문자열**. `Negroni` 는 `name_en` 매칭으로 잡힌다."
 * 이슈 본문 RED 7 은 "원문 유지 또는 빈 문자열 **결정**" 으로 열어 뒀지만
 * 미결 대장이 이미 닫은 항목이라 이슈에서 다시 판단하지 않는다 (CONVENTIONS §5.5).
 */
class ChosungTest {

    // ── RED 1~2 : 기본 ────────────────────────────────────────────────────

    @Test
    fun `RED1 - 마르가리타가 ㅁㄹㄱㄹㅌ 로 분해된다`() {
        assertThat(Chosung.of("마르가리타")).isEqualTo("ㅁㄹㄱㄹㅌ")
    }

    @Test
    fun `RED2 - 올드패션드가 ㅇㄷㅍㅅㄷ 로 분해된다`() {
        assertThat(Chosung.of("올드패션드")).isEqualTo("ㅇㄷㅍㅅㄷ")
    }

    // ── RED 3 : 초성이 없는 문자 ──────────────────────────────────────────

    /**
     * 영문 · 숫자는 **그대로 둔다.** 버리면 `잭 다니엘 No.7` 과 `잭 다니엘 No.9` 의
     * 색인이 같아진다 — 초성만으로 구분되지 않는 이름이 실제로 있다.
     */
    @Test
    fun `RED3 - 초성이 없는 문자는 그대로 둔다`() {
        assertAll(
            { assertThat(Chosung.of("진피즈No1")).isEqualTo("ㅈㅍㅈNo1") },
            { assertThat(Chosung.of("잭다니엘No.7")).isEqualTo("ㅈㄷㄴㅇNo.7") },
        )
    }

    // ── RED 4 : 공백 ──────────────────────────────────────────────────────

    /**
     * `올드 패션드` 와 `올드패션드` 가 **같은 색인**이 돼야 한다 (`FR-SEARCH-006`).
     * 초성 검색에서 띄어쓰기를 맞춰 치는 사용자는 없다.
     */
    @Test
    fun `RED4 - 공백이 제거된다`() {
        assertAll(
            { assertThat(Chosung.of("올드 패션드")).isEqualTo("ㅇㄷㅍㅅㄷ") },
            { assertThat(Chosung.of("올드\t패션드\n")).isEqualTo("ㅇㄷㅍㅅㄷ") },
            {
                assertThat(Chosung.of("올드 패션드"))
                    .`as`("띄어쓰기 변형이 같은 값으로 모인다")
                    .isEqualTo(Chosung.of("올드패션드"))
            },
        )
    }

    // ── RED 5~6 : 자모 경계 ───────────────────────────────────────────────

    @Test
    fun `RED5 - 복합 초성이 정확하다`() {
        assertAll(
            { assertThat(Chosung.of("꿀")).isEqualTo("ㄲ") },
            { assertThat(Chosung.of("딸기")).isEqualTo("ㄸㄱ") },
            { assertThat(Chosung.of("빵")).isEqualTo("ㅃ") },
            { assertThat(Chosung.of("쌍화")).isEqualTo("ㅆㅎ") },
            { assertThat(Chosung.of("짜장")).isEqualTo("ㅉㅈ") },
        )
    }

    /** 받침을 초성으로 오인하면 `강` 이 `ㄱㅇ` 이 된다. 음절당 정확히 한 글자다. */
    @Test
    fun `RED6 - 받침이 초성에 영향을 주지 않는다`() {
        assertAll(
            { assertThat(Chosung.of("강")).isEqualTo("ㄱ") },
            { assertThat(Chosung.of("닭")).isEqualTo("ㄷ") },
            { assertThat(Chosung.of("값")).isEqualTo("ㄱ") },
            { assertThat(Chosung.of("삶은달걀")).hasSize(4) },
        )
    }

    // ── RED 7 : 비한글 (DECISIONS §1.9) ───────────────────────────────────

    /**
     * **빈 문자열이다.** 원문을 유지하면 `Negroni` 가 `chosung` 에도 `name_en` 에도 들어가
     * 같은 이름이 두 컬럼에서 두 번 걸린다 — 가중치 산정(G-13)이 그만큼 왜곡된다.
     */
    @Test
    fun `RED7 - 한글이 아닌 문자열은 빈 문자열이다`() {
        assertAll(
            { assertThat(Chosung.of("Negroni")).isEmpty() },
            { assertThat(Chosung.of("Old Fashioned")).isEmpty() },
            { assertThat(Chosung.of("1900")).isEmpty() },
            { assertThat(Chosung.of("")).isEmpty() },
        )
    }

    // ── RED 8 : 이모지 · 특수문자 ─────────────────────────────────────────

    /** 서로게이트 쌍을 반 토막 내면 색인에 깨진 문자가 들어가고 그 행은 영영 안 잡힌다. */
    @Test
    fun `RED8 - 이모지 특수문자가 안전하게 처리된다`() {
        assertAll(
            { assertThat(Chosung.of("마티니🍸")).isEqualTo("ㅁㅌㄴ🍸") },
            { assertThat(Chosung.of("🍸")).`as`("한글이 없다").isEmpty() },
            { assertThat(Chosung.of("모히또(쿠바)")).isEqualTo("ㅁㅎㄸ(ㅋㅂ)") },
        )
    }

    // ── RED 9 : 유니코드 정규화 ───────────────────────────────────────────

    /**
     * macOS 가 올려 보내는 파일명 · 붙여넣기는 NFD 다. 정규화하지 않으면
     * 눈에 똑같이 보이는 두 문자열이 다른 색인을 갖는다.
     */
    @Test
    fun `RED9 - 유니코드 정규화가 적용된다`() {
        val nfd = Normalizer.normalize("마르가리타", Normalizer.Form.NFD)

        assertAll(
            { assertThat(nfd).`as`("전제 — NFD 는 원문과 다른 바이트다").isNotEqualTo("마르가리타") },
            { assertThat(Chosung.of(nfd)).isEqualTo("ㅁㄹㄱㄹㅌ") },
            { assertThat(Chosung.of(nfd)).isEqualTo(Chosung.of("마르가리타")) },
        )
    }
}
