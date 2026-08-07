---
id: ISSUE-037
title: types.ts → OpenAPI 생성물 교체
domain: —
layer: contract
wave: 8
status: TODO
depends_on: [ISSUE-004, ISSUE-036]
fr: []
r: []
inv: []
nfr: []
migration: —
owns:
  - packages/domain/src/types.ts
  - packages/domain/src/index.ts
  - packages/domain/src/data.ts
---

## 근거

**`PRIN-T02` — 계약이 정본이다**

> 분류 축 enum(`BaseSpirit` · `StyleKey` · `FlavorKey` · `SweetLevel` · `Technique`)의 정본은 **Kotlin 쪽**이다.
> **현재 `packages/domain/src/types.ts`는 프로토타입 산물이며, API 연동 시점에 생성물로 대체한다.**

**SPEC-01 §6 API 연동 시점에 정리되는 것**

| 지금 | 이후 |
|---|---|
| `packages/domain/src/data.ts` 24종 | Postgres 시드 (이슈 036) |
| **`packages/domain/src/types.ts`** | **OpenAPI 생성 타입으로 대체** (`PRIN-T02`) |
| `packages/domain/src/validate.ts` | Kotlin 이관 — 발행 게이트는 서버 강제 (`PRIN-T05`) |
| `packages/domain/src/search.ts` 필터 로직 | **클라이언트 필터로 유지**, 패싯 카운트는 서버 응답 사용 |
| `packages/ui` | **그대로 유지** |

**`PRIN-T02`**: 손으로 쓴 TS DTO를 두지 않는다. **생성물은 커밋하되 손으로 고치지 않는다.** **계약이 깨지면 빌드가 깨져야 한다**

**INDEX 결합점**: 037을 **먼저** 끝내고 038~045를 연다. **순서를 뒤집으면 두 번 고친다**

### 무엇이 바뀌나

```ts
// 지금 (프로토타입)
export type BaseSpirit = "진" | "보드카" | ... ;          // 한국어가 값
export const BASE_SLUGS: Record<BaseSpirit, string> = { 진: "gin", ... };
export type SweetLevel = 0 | 1 | 2 | 3;                   // 숫자

// 이후 (생성물)
type BaseSpirit = "gin" | "vodka" | ... ;                 // 슬러그가 값
// 한국어는 API 응답의 labelKo 또는 별도 레이블 맵
type Sweetness = "dry" | "semi_dry" | "semi_sweet" | "sweet";   // 문자열
```

**화면이 한국어를 어디서 얻는가**가 이 이슈의 실질 과제다.

### 무엇이 남나

- **`search.ts` 필터·파인더 로직** — SPEC-01 §6이 "클라이언트 필터로 유지"라고 명시
- **`validate.ts`** — `PRIN-T05`가 "프론트 쪽은 남겨도 되지만 보조 수단"이라 했다. `npm run check`도 유지 (CONVENTIONS §3.4)
- **`packages/ui`** — 그대로

## RED

### 생성물 사용

1. `types_ts의_수기_DTO가_제거된다` — 분류 축 5종
2. `생성물에서_import한다` — `packages/domain/src/generated/api.ts`
3. `수기_타입과_생성_타입이_공존하지_않는다` — 이중 정의 부재
4. `TS_컴파일이_통과한다`
5. `npm_run_build가_통과한다`

### 값 전환 (슬러그)

6. `BaseSpirit_값이_슬러그다` — `"gin"`, `"진"` 아님
7. `Sweetness가_문자열이다` — `"dry"`, `0` 아님
8. `BASE_SLUGS_맵이_불필요해진다` — 값 자체가 슬러그
9. `기존_BASE_SLUGS_참조가_전부_제거된다` — 컴파일 에러로 검출
10. `카테고리_URL이_그대로다` — `/cocktails/base/gin` (`PRIN-D02` — **슬러그 불변**)

### 한국어 레이블 (전환의 실질 과제)

11. `화면에_한국어가_표시된다` — 회귀 없음
12. `레이블_출처가_한_곳이다` **결정** — API 응답(`labelKo`) vs 프론트 상수 맵. **API 응답**(`PRIN-T02` — 정본은 Kotlin)
13. `레이블이_없는_값이_없다` — 10+9+10+5+4종 전수

### 기존 로직 보존 (SPEC-01 §6)

14. `search_ts_필터가_동작한다` — **유지 대상**
15. `facetCounts가_동작한다` — 이슈 019 결합점
16. `파인더_로직이_동작한다`
17. `validate_ts가_남아_있다` (`PRIN-T05` — 보조 수단)
18. `npm_run_check가_통과한다` (CONVENTIONS §3.4)
19. `packages_ui가_수정되지_않았다` (CONVENTIONS §4)

### data.ts (이슈 036 연계)

20. `data_ts가_어떻게_되는가` **결정** — 이관 후에도 **테스트 픽스처**로 쓸모가 있다. **유지하되 화면이 참조하지 않게**
21. `화면이_data_ts를_직접_import하지_않는다` — API 경유
22. `npm_run_check가_여전히_data_ts를_본다` **결정** — 코퍼스 검증 대상이 DB로 옮겨갔다(이슈 016). **둘 다 유지**

### 드리프트 (`PRIN-T02`)

23. `생성물이_최신이_아니면_빌드가_실패한다` (이슈 004 RED 7)
24. `생성물을_손으로_고치면_CI가_잡는다`

### 회귀

25. `기존_3화면이_동작한다` — 탐색·상세·파인더
26. `기존_필터_결과가_동일하다` — 이관 전후 대조

## GREEN

### 전환 순서

```
1. 생성물 확인 (이슈 004) — enum 5종이 나오는가
2. types.ts에서 분류 축 5종 제거, 생성물 re-export
3. 컴파일 에러를 따라가며 참조 수정 (BASE_SLUGS 등)
4. 한국어 레이블 경로 확정
5. 화면 회귀 확인
```

**컴파일 에러가 안내자다** (RED 9). 한국어 리터럴 → 슬러그 전환은 타입이 바뀌므로 모든 참조가 빨갛게 된다. 그것을 따라가면 누락이 없다.

### `types.ts`의 새 역할

```ts
// 분류 축은 생성물에서 온다 (PRIN-T02)
export type { BaseSpirit, StyleKey, FlavorKey, Sweetness, Technique }
  from "./generated/api";

// 프론트 전용 타입만 여기 남는다 (필터 상태, 파인더 단계 등)
export interface FilterState { ... }
```

**손으로 쓴 DTO가 아니라 프론트 내부 타입만 남는다.**

### 한국어 레이블 (RED 11~13)

**결정** 두 안:

**A. API 응답에 포함** — `{ "base": { "slug": "gin", "labelKo": "진" } }`
- `PRIN-T02` 정신에 맞다(정본은 Kotlin)
- 응답이 커지고, 정적 값을 매번 실어 나른다

**B. 프론트 상수 맵** — `const BASE_LABELS: Record<BaseSpirit, string>`
- 가볍다
- **손으로 쓴 것이라 `PRIN-T02` 위반 소지**

**A**: `GET /categories`(이슈 022)가 이미 `labelKo`를 준다. 화면은 그것을 캐시해 쓴다. GAPS 등재.

### `data.ts` (RED 20~22)

이관(036) 후에도 **테스트 픽스처·`npm run check`** 로 쓸모가 있다.
**화면에서만 떼어낸다** (RED 21). 삭제는 나중에 판단.

**하지 말 것**:
- `packages/ui` 수정
- `search.ts` 필터 로직 제거 (SPEC-01 §6 — 유지)
- `validate.ts` 제거 (`PRIN-T05` — 보조 수단으로 유지)

## DoD

- [ ] RED 26항 전부 통과
- [ ] **분류 축 5종이 생성물에서** 온다 (RED 1·2 — `PRIN-T02`)
- [ ] 값이 슬러그, 카테고리 URL 불변 (RED 6·10 — `PRIN-D02`)
- [ ] `search.ts`·`validate.ts`·`packages/ui` 보존 (RED 14·17·19 — SPEC-01 §6)
- [ ] 기존 3화면 회귀 없음 (RED 25·26)
- [ ] 미결은 [`DECISIONS.md`](DECISIONS.md) §1 확정분을 따른다 — **이슈에서 판단하지 않는다**
- [ ] 커밋: `refactor(domain): types.ts → OpenAPI 생성물 교체 (PRIN-T02, SPEC-01 §6)`
