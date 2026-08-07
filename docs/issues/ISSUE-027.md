---
id: ISSUE-027
title: 노출 규칙 부재 검증 (PRIN-P02)
domain: ADMIN
layer: api
wave: 5
status: TODO
depends_on: [ISSUE-025]
fr: [FR-ADMIN-006]
r: [R-F4.2-2, R-F4.2-3, R-F4.2-4, R-F3.3-3]
inv: [INV-PARTNER-01, INV-PARTNER-02, INV-PARTNER-03, INV-PARTNER-04]
nfr: [NFR-L-02, NFR-L-03]
migration: —
owns:
  - apps/api/src/test/kotlin/kr/kcocktail/architecture/ExposureRuleAbsenceTest.kt
---

## 근거

**`PRIN-P02` — 큐레이션 중립성은 하드 제약이다**

> **영업 편의로 조정할 수 있게 만들면 반드시 조정된다.** 어드민에 수치 입력란을 두는 순간 **그 수치는 올라간다.** 그러므로 아래는 **코드 상수로 박고, 어드민에서 노출하지 않는다.**

| 제약 | 근거 |
|---|---|
| 정렬 상위 3개 중 파트너 부스팅 **최대 1개** | `R-F4.2-2` |
| 홈 파트너 슬롯 **≤ 전체의 30%** | `R-F4.2-4` |
| 순위형 리스트에서 **제휴 여부가 순위에 영향 없음** | `R-F3.3-3` |
| 유료 대가 콘텐츠에 **`제휴 콘텐츠` 라벨 상단 표기** | `R-F4.2-3` — **공정위 심사지침상 의무** |

> **라벨을 축소하거나 흐리게 처리하는 스타일 변경도 이 원칙 위반이다.**

**`FR-ADMIN-006`** (**P0**): **노출 규칙(부스팅 한도 · 홈 슬롯 비율)을 어드민에서 조정할 수 없다. 입력란 자체를 만들지 않는다**

**SPEC-08 §2 마지막 줄**: 노출 규칙 변경 = **모든 역할이 `—`. `admin`도 못 바꾼다**

**SPEC-08 §2.1**

> API 표면에도 DB 컬럼에도 존재하지 않는다. 바꾸려면 **코드를 고치고 배포해야 한다. 그게 의도다** — 영업 압박이 들어오는 순간 "어드민에서 잠깐만"이 가능하면 반드시 그렇게 된다.

**SPEC-06 §3.4 `partner_contract`**

> **노출 규칙(부스팅 한도 · 홈 슬롯 비율)에 해당하는 컬럼을 만들지 않는다.** `PRIN-P02` — **저장 가능하게 만들면 조정된다.** 코드 상수로만 존재한다.

**SPEC-06 §4.4**: `INV-PARTNER-01~04`가 **"DB에 없다"는 것이 곧 `PRIN-P02`의 구현**이다 — **저장 가능하게 만들지 않아야 조정할 수 없다**

**SPEC-07 §2.6**: **노출 규칙을 조정하는 엔드포인트를 만들지 않는다.** 부스팅 한도·홈 슬롯 비율은 **API 표면에 존재하지 않는다**

**`NFR-L-02`**: `is_sponsored` 콘텐츠에 `제휴 콘텐츠` 라벨이 **끌 수 없게** 표기 — 배포 차단 · **공정위 의무**
**`NFR-L-03`**: 파트너 배지와 제휴 라벨이 **다른 색** — 배포 차단

### 이 이슈가 특이한 이유

**Phase 1a에는 PARTNER 도메인이 없다.** 구현할 기능이 없다.

그런데 `FR-ADMIN-006`은 **P0**이고, `PRIN-P02`가 요구하는 것은 **"만들지 않음"** 이다.
**만들지 않았다는 것을 테스트로 고정**하는 것이 이 이슈의 산출물이다.

이것이 없으면 Phase 1b에서 `partner_contract`를 만들 때 누군가 `boost_limit` 컬럼을 아무 생각 없이 추가한다. 그때 이 테스트가 빨갛게 된다.

## RED

**이 이슈의 산출물은 전부 "부재 검증" 테스트다.**

### DB 컬럼 부재 (SPEC-06 §3.4·§4.4)

1. `부스팅_한도_컬럼이_존재하지_않는다` — 전 테이블 스캔. `boost`·`priority`·`rank_weight` 류 컬럼명 금지 목록
2. `홈_슬롯_비율_컬럼이_존재하지_않는다` — `slot_ratio`·`home_quota` 류
3. `제휴_라벨_표시여부_컬럼이_존재하지_않는다` — `show_sponsor_label` 류. **`is_sponsored`는 허용**(사실 저장), 표시 제어는 금지
4. `금지_컬럼명_목록이_코드_상수다` — 늘릴 때 리뷰에 보인다

### API 엔드포인트 부재 (SPEC-07 §2.6)

5. `노출_규칙_조정_엔드포인트가_없다` — 라우트 전수 스캔
6. `admin_경로에도_없다` (SPEC-08 §2 — `admin`도 `—`)
7. `설정_엔드포인트가_없다` — `/admin/settings` 류로 우회 불가

### 권한 액션 부재 (이슈 006 RED 16)

8. `Action_enum에_노출규칙_관련_값이_없다`
9. `권한_매트릭스에_해당_행이_없다`

### 코드 상수 (`PRIN-P02`)

10. `부스팅_한도가_코드_상수로_정의돼_있다` — 상위 3개 중 **1개** (`INV-PARTNER-01`)
11. `홈_슬롯_비율이_코드_상수다` — **30%** (`INV-PARTNER-02`)
12. `상수가_설정파일에서_주입되지_않는다` — `@Value`·`application.yml` 참조 부재. **환경변수로도 못 바꾼다**
13. `상수값이_SPEC과_일치한다` — 1 / 0.30

> ⚠️ RED 10~13은 **PARTNER 도메인이 없어 상수를 둘 곳이 없다.**
> **보수적 판단**: Phase 1a에서는 상수를 **정의하지 않는다**(쓰이지 않는 상수는 죽은 코드). 대신 RED 10~13을 `@Disabled` + **"Phase 1b ISSUE-XXX에서 해제"** 주석으로 남긴다. `EPICS-1B-PHASE2.md`에 이 항목을 명시한다.

### 라벨 강제 (`NFR-L-02`, `INV-PARTNER-04`)

14. `is_sponsored_응답에서_라벨_플래그를_끌_수_없다` — 이슈 023 RED 20 재확인. **Phase 1a에 실재하는 유일한 적용 지점**(재료 브랜드)
15. `라벨_억제_쿼리파라미터가_없다`
16. `라벨_억제_헤더가_없다`

### 문서 동기화

17. `금지_목록이_SPEC_인용과_함께_주석돼_있다` — 왜 없는지 읽어서 알 수 있어야 한다

## GREEN

### `ExposureRuleAbsenceTest.kt`

```kotlin
/**
 * PRIN-P02 · FR-ADMIN-006 · SPEC-08 §2.1 · SPEC-06 §4.4
 *
 * 이 테스트는 "만들지 않았다"를 검증한다.
 * 빨갛게 되면 누군가 노출 규칙을 조정 가능하게 만든 것이고,
 * 그것은 기능 추가가 아니라 원칙 위반이다.
 *
 * 되돌리는 조건: SPEC-00 PRIN-P02 개정 + ADR (SPEC-00 §4)
 */
@Tag("boundary")
class ExposureRuleAbsenceTest {

    private val FORBIDDEN_COLUMN_PATTERNS = listOf(   // RED 4
        "boost", "slot_ratio", "home_quota", "rank_weight",
        "show_sponsor", "sponsor_label_visible", "exposure_",
    )
    ...
}
```

`boundaryTest` 태스크에 포함해 CI에서 돈다 (이슈 001).

### 컬럼 스캔 (RED 1~3)

```kotlin
// information_schema 전수 스캔
val columns = jdbc.queryForList("SELECT table_name, column_name FROM information_schema.columns WHERE table_schema='public'")
val violations = columns.filter { c -> FORBIDDEN.any { c.columnName.contains(it) } }
assertThat(violations).isEmpty()
```

**`is_sponsored`는 통과해야 한다** (RED 3) — 사실을 저장하는 것은 허용이고, **표시 여부를 제어하는 것**이 금지다. 이 구분을 주석으로 남긴다.

### 라우트 스캔 (RED 5~7)

Spring의 `RequestMappingHandlerMapping`에서 전체 경로를 뽑아 금지 패턴과 대조한다.

### 왜 테스트가 산출물인가

`PRIN-P02`는 "코드 상수로 박고 어드민에서 노출하지 않는다"고 했다. **부재는 코드로 표현할 수 없다** — 테스트로만 고정된다.

SPEC-06 §4.4의 문장이 이것을 정확히 말한다:

> `INV-PARTNER-01~04`가 **"DB에 없다"는 것이 곧 `PRIN-P02`의 구현**이다.

**하지 말 것**:
- PARTNER 도메인 구현 — Phase 1b
- 상수 정의 — RED 10~13 참조 (`@Disabled`)
- 프론트 라벨 렌더링 — 이슈 032

## DoD

- [ ] RED 17항 통과 (10~13은 `@Disabled` + Phase 1b 참조)
- [ ] 테스트가 `boundaryTest`에 포함돼 **CI에서 상시 실행**
- [ ] 금지 목록에 **SPEC 인용 주석** (RED 17)
- [ ] `is_sponsored`(허용) vs 표시 제어(금지) 구분이 주석에 명시
- [ ] `EPICS-1B-PHASE2.md`에 상수 정의 항목 등재
- [ ] 커밋: `test(admin): 노출 규칙 부재 검증 (FR-ADMIN-006, PRIN-P02, INV-PARTNER-01~04)`
