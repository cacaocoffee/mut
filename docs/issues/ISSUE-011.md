---
id: ISSUE-011
title: 도수 자동 계산 + 오버라이드
domain: COCKTAIL
layer: api
wave: 2
status: TODO
depends_on: [ISSUE-010]
fr: [FR-COCKTAIL-006]
r: [R-F1.1-4]
inv: [INV-COCKTAIL-06]
nfr: []
migration: V011
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/cocktail/abv/**
  - apps/api/src/main/resources/db/migration/V011__*.sql
---

## 근거

**`FR-COCKTAIL-006`**: 도수를 재료 도수·용량과 **기법별 희석률**로 자동 계산하고, **수동 오버라이드 필드**를 둔다. **`counts_for_stock=false`인 가니시는 계산에서 제외**한다.

**SPEC-02 §2.4 도수 계산**

| 기법 | 희석률 |
|---|---|
| Shake | **25%** |
| Stir | **20%** |
| Build | **10%** |
| Blend · Etc | **수동** |

```
abv_calculated = Σ(ingredient.abv × amount_ml) / Σ(amount_ml) × (1 - 희석률)
표시값 = abv_override ?? abv_calculated
```

`counts_for_stock = false`인 가니시는 계산에서 제외한다.

**SPEC-06 §3.1**

```
abv_calculated  NUMERIC(4,1)   자동 계산
abv_override    NUMERIC(4,1)   수동 우선
abv             NUMERIC(4,1)   GENERATED — COALESCE(abv_override, abv_calculated)
```

> `abv`를 생성 컬럼으로 둔 이유는 **조회·필터가 항상 표시값을 봐야 하기 때문**이다. 매 쿼리에서 `COALESCE`를 쓰면 인덱스가 안 붙는다.

**SPEC-06 §5**: `cocktail(status, abv)` B-tree — 도수 구간 필터

**SPEC-07 §5**: 공개 응답에 **`abv_calculated`/`abv_override` 구분을 담지 않는다** — 표시값 `abv` 하나만

**`INV-COCKTAIL-06`**: `base_spirit = non-alcoholic` ⟺ `abv = 0`

**ADR-0003 도수 4구간** (이슈 018·019가 소비): `na` · `low`(~10%) · `mid`(10–20%) · `high`(20%~)

### 계산이 순수 함수여야 하는 이유

이 계산은 **전수 테스트가 가능해야 한다**. 재료 조합 × 기법 5종의 경우의 수를 DB 없이 돌려야 회귀를 잡는다. `NFR-D-01`(발행분 불변식 위반 0건)의 검증 대상이기도 하다.

## RED

### 계산식 (SPEC-02 §2.4 — 순수 함수, 전수)

1. `단일_재료_희석없음` — 진 60ml 40% → 40.0 (기법 없음 가정 시)
2. `Shake_희석률_25퍼센트가_적용된다` — 계산값 × 0.75
3. `Stir_희석률_20퍼센트`
4. `Build_희석률_10퍼센트`
5. `Blend는_자동계산하지_않는다` — 수동 (SPEC-02 §2.4)
6. `Etc는_자동계산하지_않는다`
7. `복수_재료_가중평균` — Σ(abv × ml) / Σ(ml)
8. `무알콜_재료가_섞이면_도수가_희석된다` — abv=0인 주스 등
9. `소수점_한자리로_반올림된다` — `NUMERIC(4,1)`
10. `계산_결과가_음수가_되지_않는다`
11. `계산_결과가_100을_넘지_않는다`

### 계산 제외 (`FR-COCKTAIL-006`, `R-F2.2-5`)

12. `counts_for_stock_false_재료는_계산에서_제외된다` — **가니시**
13. `amount가_없는_재료는_계산에서_제외된다` — `top_up`·`1조각`
14. `단위가_ml이_아닌_재료는_계산에서_제외된다` ⚖️ — `dash`·`barspoon`은 용량이 작아 무시. **보수적으로 제외** + GAPS
15. `is_optional_재료는_포함되는가` ⚖️ — 표준 레시피 기준이므로 **포함**. 보수적 + GAPS
16. `계산_대상_재료가_0개면_계산값이_null이다` — 0이 아니라 null

### 오버라이드

17. `abv_override가_있으면_그것이_표시값이다`
18. `abv_override가_없으면_abv_calculated가_표시값이다`
19. `abv는_생성_컬럼이다` — 앱이 직접 쓰지 못함 (SPEC-06 §3.1)
20. `abv_override를_지우면_표시값이_계산값으로_돌아간다`
21. `abv_override가_범위_밖이면_거부` — 0~100

### 재계산 트리거

22. `재료_추가시_abv_calculated가_재계산된다`
23. `재료_용량_변경시_재계산된다`
24. `기법_변경시_재계산된다` — Shake → Stir
25. `재료의_마스터_도수가_바뀌면_재계산이_필요하다고_표시된다` ⚖️ — 전체 재계산은 배치. **보수적으로 검증 태스크 생성**(이슈 028) + GAPS

### 무알콜 정합 (`INV-COCKTAIL-06`)

26. `무알콜_기주인데_계산값이_0이_아니면_위반이_보고된다`
27. `계산_결과_0인데_기주가_무알콜이_아니면_위반이_보고된다`
28. `DB_CHECK가_최종_방어선이다` — 이슈 009 RED 23~26 재확인

### 노출 (SPEC-07 §5)

29. `공개_응답에_abv만_있고_calculated_override가_없다`
30. `어드민_응답에는_셋_다_있다` — 에디터가 오버라이드 여부를 알아야 한다

## GREEN

### `V011__abv.sql`

`abv_calculated`·`abv_override`·`abv` 컬럼은 이슈 009의 `V009`에 이미 있다.
이 마이그레이션은 **인덱스만**:

```sql
CREATE INDEX ON cocktail (status, abv);      -- SPEC-06 §5 도수 구간 필터
```

컬럼을 009에 둔 이유: `abv`가 생성 컬럼이라 테이블 생성 시점에 정의돼야 하고, `ck_cocktail_na` CHECK가 그것을 참조한다.

### `cocktail/abv` — 순수 함수

```kotlin
object AbvCalculator {
    private val DILUTION = mapOf(          // SPEC-02 §2.4
        Technique.Shake to 0.25,
        Technique.Stir  to 0.20,
        Technique.Build to 0.10,
        // Blend · Etc 는 없다 — 수동
    )

    fun calculate(ingredients: List<AbvInput>, method: Technique): BigDecimal? {
        if (method !in DILUTION) return null          // RED 5·6
        val target = ingredients.filter { it.countsForStock && it.unit == ML && it.amountMl != null }
        if (target.isEmpty()) return null             // RED 16
        val weighted = target.sumOf { it.abv * it.amountMl }
        val volume   = target.sumOf { it.amountMl }
        return (weighted / volume * (1 - DILUTION[method]!!)).setScale(1, HALF_UP)
    }
}
```

**DB 없이 전수 테스트가 가능하다** — RED 1~16이 전부 단위 테스트.

### 재계산 시점

레시피 저장 트랜잭션 안에서 계산해 `abv_calculated`에 쓴다. 이벤트로 미루지 않는다 — 저장 직후 어드민이 값을 봐야 한다.

`ingredient.abv` 변경 시의 전체 재계산은 **검증 태스크**로 올린다 (RED 25, 이슈 028). 즉시 전수 재계산은 500종 규모에서 부담이고, 에디터가 확인해야 할 변경이다.

**하지 말 것**:
- 도수 구간(`AbvBand`) 분류 — 이슈 018 (필터)
- 파인더의 도수 질문 — 이슈 041

## DoD

- [ ] RED 30항 전부 통과
- [ ] `AbvCalculator` 가 **순수 함수**, RED 1~16 단위 테스트 전수
- [ ] 희석률 3종이 SPEC-02 §2.4 표와 일치, `Blend`·`Etc`는 계산 안 함
- [ ] 가니시 제외 (RED 12 — `FR-COCKTAIL-006`)
- [ ] 공개 응답에 `abv` 하나만 (RED 29 — SPEC-07 §5)
- [ ] ⚖️ 4건(비-ml 단위·선택 재료·마스터 도수 변경·재계산 시점) `GAPS.md` 등재
- [ ] 커밋: `feat(cocktail): 도수 자동계산·오버라이드 (FR-COCKTAIL-006, R-F1.1-4)`
