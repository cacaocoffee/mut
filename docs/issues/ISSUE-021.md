---
id: ISSUE-021
title: GET /cocktails/{slug}/related 배리에이션
domain: COCKTAIL
layer: api
wave: 4
status: TODO
depends_on: [ISSUE-020]
fr: [FR-COCKTAIL-024]
r: [R-C-3]
inv: []
nfr: []
migration: —
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/cocktail/related/**
---

## 근거

**`FR-COCKTAIL-024`** (P0): 배리에이션을 **`style_primary` 일치 1순위 · 기주 일치 2순위**로 추천한다 (`R-C-3`)

**SPEC-07 §2.1**: `GET /cocktails/{slug}/related` — 배리에이션 (`R-C-3`)

**SPEC-02 §2.1**: `style_primary`는 **배리에이션 추천의 1순위 기준**

**`packages/domain/src/types.ts`** (프로토타입 주석):
> `stylePrimary` — 배리에이션 추천의 1순위 기준 (PRD `R-C-3`). `styles`에 반드시 포함돼야 한다

**`PRIN-P06`**: `style_primary`가 카테고리 축이자 추천 기준이다. 이것이 `INV-COCKTAIL-03`(복합 FK)이 존재하는 이유 중 하나 — **추천이 신뢰할 수 있으려면 primary가 실제 styles 안에 있어야 한다**

**SPEC-06 §5**: `cocktail(status, style_primary)` · `(status, base_spirit)` B-tree — 이 조회의 인덱스 경로

**SPEC-05 §3**: 순환 의존 주의 — 배리에이션은 `cocktail` 내부 조회라 문제없다. (`BAR` 연결은 1b에서 `SEARCH` 경유)

### 순위 규칙이 이 이슈의 전부다

```
1순위: style_primary 일치
2순위: base_spirit 일치
```

명시되지 않은 것들 — **보수적 기본값 + GAPS**:
- 둘 다 일치하는 것이 최상위인가 → **그렇다** (1순위 안에서 2순위로 정렬)
- 자기 자신 제외 → **그렇다**
- 개수 상한 → SPEC에 없다. **보수적으로 6~8건**
- 동점 시 정렬 → **결정론적이어야 한다** (테스트 안정성)

## RED

### 순위 (`FR-COCKTAIL-024`, `R-C-3`) — 요체

1. `style_primary_일치가_1순위다`
2. `base_spirit_일치가_2순위다`
3. `둘_다_일치하면_최상위다`
4. `style만_일치_vs_base만_일치면_style이_앞선다`
5. `둘_다_불일치면_결과에_없다` ⚖️ — 또는 후순위 채움. **보수적으로 제외** + GAPS
6. `자기_자신은_제외된다`
7. `동점_정렬이_결정론적이다` — 같은 입력에 항상 같은 순서 (예: slug 사전순)

### 발행분만

8. `published만_추천된다`
9. `draft는_추천에_없다`
10. `archived도_없다`

### 개수·형태

11. `개수_상한이_있다` ⚖️ — 보수적으로 8건 + GAPS
12. `추천이_없으면_빈_배열이다` — 404가 아니다
13. `응답_항목에_slug와_이름과_요약이_있다` — 카드 렌더링용
14. `응답에_내부_id가_없다`
15. `응답에_추천_사유가_포함되는가` ⚖️ — "같은 스타일" 배지 등. 보수적으로 **매칭 축을 포함**(FE가 쓸 수 있게)

### 정합 (`INV-COCKTAIL-03`)

16. `style_primary가_styles에_포함된_것만_추천된다` — 복합 FK가 이미 보장하나 추천 신뢰성의 근거
17. `추천_결과의_style_primary가_원본과_같다` — 1순위 그룹

### 성능

18. `인덱스를_탄다` — `(status, style_primary)` EXPLAIN
19. `N_plus_1이_없다`

### 캐싱

20. `ETag와_Cache_Control이_붙는다` — 상세와 같은 정책 (SPEC-07 §1.6)
21. `noindex가_붙지_않는다` — 상세 페이지 일부

## GREEN

### `cocktail/related`

```kotlin
object RelatedRanker {                       // 순수 함수
    fun rank(target: CocktailRef, candidates: List<CocktailRef>): List<Related> =
        candidates
            .filterNot { it.slug == target.slug }                       // RED 6
            .mapNotNull { c ->
                val styleMatch = c.stylePrimary == target.stylePrimary
                val baseMatch  = c.baseSpirit   == target.baseSpirit
                when {
                    styleMatch && baseMatch -> Related(c, rank = 0, MatchedOn.BOTH)
                    styleMatch              -> Related(c, rank = 1, MatchedOn.STYLE)
                    baseMatch               -> Related(c, rank = 2, MatchedOn.BASE)
                    else                    -> null                     // RED 5
                }
            }
            .sortedWith(compareBy({ it.rank }, { it.slug }))            // RED 7 결정론
            .take(LIMIT)                                                 // RED 11
}
```

**순수 함수라 RED 1~7·11을 DB 없이 전수 테스트한다.** 순위 규칙이 이 이슈의 유일한 로직이므로 여기에 테스트를 집중한다.

### 쿼리

```sql
SELECT ... FROM cocktail
WHERE status = 'published'
  AND id <> :selfId
  AND (style_primary = :stylePrimary OR base_spirit = :baseSpirit)
ORDER BY (style_primary = :stylePrimary) DESC, (base_spirit = :baseSpirit) DESC, slug
LIMIT :limit
```

**SQL에서 순위를 매길지 애플리케이션에서 매길지**: 500종 규모면 후보가 적어 어느 쪽도 무방하다. **순수 함수 테스트를 위해 애플리케이션 정렬을 기본**으로 하되, 후보 조회는 위 `WHERE`로 좁힌다.

### 매칭 축 노출 (RED 15)

```kotlin
enum class MatchedOn { BOTH, STYLE, BASE }
```

FE가 "같은 스타일" 배지를 붙일 수 있다. SPEC에 명시는 없지만 **정보를 버리지 않는 편**이 낫다 — GAPS에 근거를 남긴다.

**하지 말 것**:
- "이 칵테일을 마실 수 있는 바" (`FR-COCKTAIL-025`) — **Phase 1b**
- 개인화 추천 — 범위 밖

## DoD

- [ ] RED 21항 전부 통과
- [ ] `RelatedRanker` 가 **순수 함수**, 순위 규칙 전수 테스트 (RED 1~7)
- [ ] 동점 정렬 결정론적 (RED 7)
- [ ] 인덱스 사용 (RED 18)
- [ ] ⚖️ 4건(둘 다 불일치 처리·개수 상한·추천 사유 노출·정렬 기준) `GAPS.md` 등재
- [ ] 커밋: `feat(cocktail): 배리에이션 추천 (FR-COCKTAIL-024, R-C-3)`
