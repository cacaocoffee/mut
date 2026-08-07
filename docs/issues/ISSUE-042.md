---
id: ISSUE-042
title: 통합 검색 화면
domain: SEARCH
layer: web
wave: 8
status: TODO
depends_on: [ISSUE-024, ISSUE-037]
fr: [FR-SEARCH-006, FR-SEARCH-007, FR-SEARCH-008]
r: [R-F5-1, R-F2.1-3, R-F2.1-4]
inv: []
nfr: [NFR-A-04, NFR-S-02]
migration: —
owns:
  - apps/web/app/search/**
  - apps/web/components/search-box.tsx
---

## 근거

**`FR-SEARCH-008`**: 통합 검색에서 칵테일 · 바 · 재료 · 아티클을 **타입별로 그룹핑**해 노출한다 (`R-F5-1`)
**`FR-SEARCH-006`**: `올드패션드` / `올드 패션드` / `Old Fashioned` / `올패`가 모두 매칭
**`FR-SEARCH-007`**: 초성 검색. `ㅁㄹㄴ` → 마르가리타

**SPEC-07 §2.4**: `GET /search?q=` (타입별 그룹핑) · `GET /search/suggest?q=` (자동완성. 초성·별칭 매칭)

**SPEC-10 §4.3 `search_miss` ★ — Phase 1a에서 가장 쓸모 있는 이벤트**

> 검색됐는데 없는 칵테일이 곧 **수요가 확인된 콘텐츠 후보**다.
> `hadChosung`을 따로 두는 이유 — 초성 검색이 0건이면 콘텐츠가 없는 게 아니라 **초성 색인이 고장난 것**일 수 있다. **두 원인을 구분해야 한다.**

**SPEC-10 §6.1 파생 지표**: **검색 실패율**(`search_miss`/전체) → 콘텐츠 우선순위 · **미등재 수요 랭킹**(`search_miss.query` 빈도순) → **다음에 쓸 칵테일**

**SPEC-08 §6**: `/search`·`/search/suggest` = **60 req/min** (IP) — **가장 비싼 조회**

**`PRIN-P06`**: 검색 결과는 필터 성격 → **색인하지 않는다** (`NFR-S-02` 정신)

**Phase 1a 범위**: `search_document`에 `cocktail`·`ingredient`만 (이슈 017 RED 28). `bar`·`article` **그룹 자리는 확보**

## RED

### 검색 (`FR-SEARCH-006`, `R-F2.1-3`)

1. `한글명으로_검색된다`
2. `띄어쓰기_변형이_검색된다`
3. `영문명으로_검색된다`
4. `축약형_별칭이_검색된다`
5. `4가지_표기가_같은_결과를_준다` (이슈 024 RED 7)

### 초성 (`FR-SEARCH-007`)

6. `초성으로_검색된다` — `ㅁㄹㄱㄹㅌ`
7. `초성_프리픽스가_검색된다`
8. `초성_입력이_UI에서_안내된다` **결정** — 사용자가 초성 검색 가능함을 아는가. **플레이스홀더에 힌트**

### 타입별 그룹핑 (`FR-SEARCH-008`, `R-F5-1`)

9. `결과가_타입별로_그룹핑돼_보인다`
10. `Phase_1a는_칵테일과_재료_그룹이_보인다`
11. `그룹마다_건수가_표시된다`
12. `빈_그룹_처리가_일관된다` (이슈 024 RED 18)
13. `그룹_순서가_결정론적이다` **결정** — 칵테일 우선 (weight).

### 결과 0건 (SPEC-10 §4.3 — 요체)

14. `0건일_때_안내가_나온다`
15. **`search_miss_이벤트가_발생한다`**
16. `이벤트에_query와_matchedCount와_hadChosung이_담긴다`
17. `hadChosung이_서버_응답에서_온다` — 프론트가 다시 판정하지 않는다 (이슈 024)
18. `0건_안내가_다음_행동을_제시한다` **결정** — 탐색으로 유도 등

### 자동완성

19. `입력중_제안이_나온다`
20. `제안이_디바운스된다` — 60rpm 제한 (SPEC-08 §6)
21. `제안_개수에_상한이_있다`
22. `제안_선택시_상세로_이동한다`
23. `키보드로_제안을_고를_수_있다` (`NFR-A-04`)

### 레이트 리밋 (SPEC-08 §6)

24. `429를_받으면_안내가_나온다`
25. `429가_UI를_깨뜨리지_않는다`

### 색인

26. `검색_결과에_noindex가_있다`

### 접근성

27. `검색_입력에_라벨이_있다`
28. `결과_수가_스크린리더에_안내된다` — `aria-live`
29. `키보드로_전체_흐름이_가능하다` (`NFR-A-04`)
30. `focus_visible_아웃라인` (`NFR-A-05`)

### 법적

31. `과음_경고가_하단에_있다`

## GREEN

### `app/search/page.tsx`

CSR. 색인 대상이 아니다 (RED 26).

### `search_miss` (RED 15~17 — 가장 중요)

```ts
// SPEC-10 §4.3 — 판정은 서버가 했다. 프론트는 옮기기만
if (res.matchedCount === 0) {
  track("search_miss", {
    query: res.query,
    matchedCount: 0,
    hadChosung: res.hadChosung,   // 서버 응답 (이슈 024)
  });
}
```

**프론트가 `hadChosung`을 다시 판정하지 않는다** (RED 17). 서버와 다른 답을 내면 SPEC-10 §4.3의 "두 원인 구분"이 무너진다.

### 자동완성 디바운스 (RED 20)

60 req/min = 초당 1회. **300ms 디바운스** 정도가 안전하다. 레이트 리밋에 걸리면 UI가 조용히 실패해야 한다 (RED 25).

### 그룹 자리 확보 (RED 10·12)

```tsx
const GROUP_ORDER = ["cocktail", "ingredient", "bar", "article"] as const;
// bar·article 은 Phase 1b·2 — 지금은 빈 배열이라 렌더되지 않는다
```

**enum을 나중에 늘리지 않는다** (이슈 024 RED 15와 같은 이유).

**하지 말 것**:
- 검색 로직 재구현 — 서버가 한다 (이슈 024)
- `hadChosung` 프론트 판정 (RED 17)
- 필터 화면 — 이슈 040

## DoD

- [ ] RED 31항 전부 통과
- [ ] **`search_miss` 이벤트 발생, `hadChosung`이 서버 응답** (RED 15~17 — SPEC-10 §4.3)
- [ ] 타입별 그룹핑, 1b·2 그룹 자리 확보 (RED 9·10)
- [ ] 자동완성 디바운스로 60rpm 준수 (RED 20)
- [ ] `noindex` (RED 26)
- [ ] 키보드 전체 흐름 (RED 29 — `NFR-A-04`)
- [ ] 미결은 [`DECISIONS.md`](DECISIONS.md) §1 확정분을 따른다 — **이슈에서 판단하지 않는다**
- [ ] 커밋: `feat(web): 통합 검색 화면 (FR-SEARCH-006·007·008, SPEC-10 §4.3)`
