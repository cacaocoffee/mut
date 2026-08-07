---
id: ISSUE-024
title: GET /search 통합 검색
domain: SEARCH
layer: api
wave: 4
status: TODO
depends_on: [ISSUE-017]
fr: [FR-SEARCH-008]
r: [R-F5-1, R-F2.1-3, R-F2.1-4]
inv: []
nfr: [NFR-SEC-05]
migration: —
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/search/query/**
---

## 근거

**`FR-SEARCH-008`**: 통합 검색에서 칵테일 · 바 · 재료 · 아티클을 **타입별로 그룹핑**해 노출한다 (`R-F5-1`)
**`FR-SEARCH-006`** (`R-F2.1-3`): `올드패션드` / `올드 패션드` / `Old Fashioned` / `올패`가 모두 매칭
**`FR-SEARCH-007`** (`R-F2.1-4`): 초성 검색. `ㅁㄹㄴ` → 마르가리타

**SPEC-07 §2.4**

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` | `/search?q=` | 통합 검색 — **타입별 그룹핑** (`R-F5-1`) |
| `GET` | `/search/suggest?q=` | 자동완성. 초성·별칭 매칭 |

**SPEC-05 §6**: 조회는 **`LIKE` 프리픽스 매칭 + GIN 인덱스**. **Postgres만으로 처리한다.** 별도 검색엔진은 코퍼스가 **수천 건을 넘을 때** 검토

**SPEC-06 §3.8 `search_document`** — 이슈 017이 만든 색인을 읽는다. `weight` 정렬 가중치

**SPEC-08 §6 레이트 리밋**: `/search` · `/search/suggest` = **60 req/min** (IP)
> 검색을 더 조이는 이유는 초성·별칭 매칭이 GIN 인덱스를 타긴 해도 **가장 비싼 조회**이기 때문이다

**SPEC-08 §7**: SQL 인젝션 — JPA 파라미터 바인딩. **초성 검색도 `LIKE` 파라미터로 처리**

**SPEC-10 §4.3 `search_miss` ★** — **Phase 1a에서 가장 쓸모 있는 이벤트**

> 에디터 1명이 하루 3~5종을 쓰는 상황에서 **"다음에 뭘 등재할까"에 데이터로 답한다.** 검색됐는데 없는 칵테일이 곧 **수요가 확인된 콘텐츠 후보**다.
>
> `hadChosung`을 따로 두는 이유 — 초성 검색이 0건이면 콘텐츠가 없는 게 아니라 **초성 색인이 고장난 것**일 수 있다. 두 원인을 구분해야 한다.

| payload | |
|---|---|
| `query` | 검색어 원문 |
| `matchedCount` | 0 |
| `hadChosung` | 초성 검색이었나 |

**SPEC-06 §7 미정**: 검색 **가중치 산정식** — G-13

**Phase 1a 범위**: `search_document`에 `cocktail`·`ingredient`만 있다 (이슈 017 RED 28). `bar`·`article`은 1b·2

## RED

### 매칭 (`FR-SEARCH-006`, `R-F2.1-3`)

1. `한글명으로_매칭된다` — `올드패션드`
2. `띄어쓰기_변형이_매칭된다` — `올드 패션드`
3. `영문명으로_매칭된다` — `Old Fashioned`
4. `영문_대소문자를_구분하지_않는다`
5. `축약형_별칭이_매칭된다` — `올패`
6. `부분_문자열이_매칭된다` — `네그로` → 네그로니
7. `4가지_표기가_같은_결과를_준다` (`R-F2.1-3` 요구 그대로)

### 초성 (`FR-SEARCH-007`, `R-F2.1-4`)

8. `초성으로_매칭된다` — `ㅁㄹㄱㄹㅌ` → 마르가리타
9. `초성_프리픽스가_매칭된다` — `ㅁㄹㄱ`
10. `초성과_일반_검색어를_구분한다` — 입력이 초성만으로 이뤄졌는지 판정
11. `초성_섞인_입력을_처리한다` ⚖️ — `마ㄹㄱ`. **보수적으로 일반 검색으로 처리** + GAPS
12. `초성_매칭이_GIN_인덱스를_탄다` — EXPLAIN (SPEC-05 §6)

### 타입별 그룹핑 (`FR-SEARCH-008`, `R-F5-1`)

13. `결과가_타입별로_그룹핑된다`
14. `Phase_1a는_cocktail과_ingredient_그룹만_있다`
15. `bar와_article_그룹_자리가_응답_스키마에_있다` — 1b·2에서 enum을 늘리면 클라이언트가 깨진다
16. `그룹마다_건수가_있다`
17. `그룹_내_정렬이_weight_기준이다`
18. `빈_그룹은_생략되는가` ⚖️ — 보수적으로 **빈 배열로 포함** (클라이언트가 자리를 알 수 있게) + GAPS

### 발행분만

19. `is_published_true만_반환된다`
20. `draft_칵테일이_없다`
21. `미승인_재료가_없다`

### search_miss (SPEC-10 §4.3)

22. `결과가_0건이면_search_miss_이벤트_기록이_가능하다` — 실제 수집은 이슈 034·035. 여기서는 **응답에 `matchedCount`와 `hadChosung`을 담아** FE가 이벤트를 쏠 수 있게
23. `hadChosung_플래그가_응답에_있다`
24. `초성_검색_0건과_일반_검색_0건이_구분된다` — SPEC-10 §4.3의 핵심

### 보안·성능

25. `SQL_인젝션이_막힌다` — `%`·`_`·`'` 포함 입력 (SPEC-08 §7)
26. `LIKE_와일드카드가_이스케이프된다`
27. `레이트_리밋_60rpm이_적용된다` (SPEC-08 §6, `NFR-SEC-05`)
28. `빈_q는_400이거나_빈_결과다` ⚖️
29. `q_길이_상한이_있다` — 과도한 입력 방어
30. `응답이_페이징되거나_상한이_있다`

### 자동완성

31. `suggest가_프리픽스_매칭이다`
32. `suggest_결과_개수에_상한이_있다`
33. `suggest도_60rpm이다`

### 규약

34. `내부_id가_없다`
35. `noindex가_붙는다` — 검색 결과는 색인 대상 아님 (`PRIN-P06` 정신)

## GREEN

### `search/query`

```kotlin
data class SearchResponse(
    val query: String,
    val hadChosung: Boolean,                 // SPEC-10 §4.3
    val matchedCount: Int,
    val groups: Map<EntityType, SearchGroup> // RED 13·15 — 4종 자리 확보
)
```

### 초성 판정 (RED 10)

```kotlin
// 입력이 전부 초성 자모면 초성 검색으로 간주
fun isChosungQuery(q: String): Boolean = q.isNotBlank() && q.all { it in CHOSUNG_SET }
```

**섞인 입력**(RED 11)은 일반 검색으로. 보수적 선택 + GAPS.

### 쿼리 (SPEC-05 §6)

```sql
SELECT * FROM search_document
WHERE is_published
  AND (
    name_ko ILIKE :prefix ESCAPE '\'          -- RED 26
    OR name_en ILIKE :prefix ESCAPE '\'
    OR :q = ANY(aliases)
    OR (:isChosung AND chosung LIKE :chosungPrefix ESCAPE '\')
  )
ORDER BY weight DESC, name_ko
```

**파라미터 바인딩** (SPEC-08 §7 — RED 25). 문자열 연결 금지.

띄어쓰기 변형(RED 2)은 **색인 시점에 공백 제거 버전을 별칭에 넣거나**, 조회 시 양쪽 공백을 제거해 비교한다. 이슈 017의 `Chosung.of()`가 이미 공백을 제거하므로(RED 4), **일반 검색에도 같은 정규화**를 적용한다.

⚖️ SPEC-05 §6은 "별칭은 `aliases[]`로 관리하고 어드민에서 편집한다"고 했다 — **띄어쓰기 변형을 에디터가 손으로 넣으라는 뜻**일 수도 있다. 보수적으로 **양쪽 다**: 정규화 매칭 + 에디터 별칭. GAPS 등재.

### `search_miss` 준비 (RED 22~24)

서버는 이벤트를 **직접 기록하지 않는다.** 응답에 `matchedCount`·`hadChosung`을 담고, FE(이슈 035)가 `POST /events`로 쏜다 — SPEC-10 §7의 수집 경로가 하나여야 한다.

**하지 말 것**:
- 이벤트 수집 — 이슈 034
- 검색 화면 — 이슈 042
- 가중치 산정식 확정 — G-13 (이슈 017의 기본값 사용)
- `bar`·`article` 색인 — Phase 1b·2

## DoD

- [ ] RED 35항 전부 통과
- [ ] `R-F2.1-3`의 **4가지 표기가 같은 결과** (RED 7)
- [ ] 초성 매칭이 GIN 인덱스 사용 (RED 12)
- [ ] SQL 인젝션·와일드카드 이스케이프 (RED 25·26)
- [ ] `hadChosung`이 응답에 포함 — `search_miss` 원인 구분 (RED 23·24)
- [ ] 레이트 리밋 60rpm (RED 27)
- [ ] ⚖️ 4건(초성 혼합 입력·빈 그룹·빈 q·띄어쓰기 정규화 방식) `GAPS.md` 등재
- [ ] 커밋: `feat(search): 통합 검색·초성·별칭 (FR-SEARCH-008, R-F5-1·R-F2.1-3·R-F2.1-4)`
