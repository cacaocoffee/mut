---
id: ISSUE-015
title: 재생성 훅 (API → 프론트)
domain: COCKTAIL
layer: api
wave: 3
status: TODO
depends_on: [ISSUE-014]
fr: [FR-COCKTAIL-016, FR-ADMIN-008]
r: []
inv: []
nfr: [NFR-R-03, NFR-O-02]
migration: —
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/common/revalidate/**
---

## 근거

**`FR-COCKTAIL-016`**: 발행 시 해당 상세·카테고리 페이지의 **on-demand 재생성을 요청**한다. **에디터가 반영을 기다리지 않는다**
**`FR-ADMIN-008`**: 발행 시 프론트의 on-demand 재생성을 트리거한다

**SPEC-05 §4**: **on-demand 재생성** — 어드민에서 발행하면 API가 프론트의 revalidate 훅을 호출한다. 에디터가 발행하고 나서 반영을 기다리지 않아야 한다 (PRD 12장 — 개발자 없이 발행)

**SPEC-07 §4 재생성 훅 — 방향이 반대인 유일한 호출이다. 백엔드가 프론트를 부른다.**

```
POST {FRONTEND_URL}/api/revalidate
X-Revalidate-Secret: <공유 시크릿>

{ "paths": ["/cocktails/negroni", "/cocktails/base/gin"] }
```

> **실패해도 발행 트랜잭션을 되돌리지 않는다** — ISR 주기가 결국 따라잡는다. 실패는 로그로 남긴다.

**`NFR-R-03`**: 발행 트랜잭션은 **재생성 훅 실패로 롤백되지 않는다** (측정: 통합 테스트, 실패 시 배포 차단)
**`NFR-O-02`**: 발행 후 공개 반영 **≤ 30초**

**SPEC-05 §4 렌더링 전략** — 재생성 대상 경로

| 경로 | 방식 | 재생성 |
|---|---|---|
| `/` 홈 | ISR | 10분 |
| `/cocktails/[slug]` | SSG + ISR | **발행 시 on-demand** |
| `/cocktails/base/[slug]` 외 카테고리 2종 | SSG + ISR | **발행 시 on-demand** |

**`PRIN-T04`**: 칵테일 상세·카테고리는 SSG + ISR. 요청 시 렌더하지 않는다

**`NFR-S-04`**: 발행분 전체가 사이트맵에 포함 — 발행 시 재생성

### 어느 경로를 재생성하나

칵테일 하나가 발행되면 영향받는 정적 경로:

1. `/cocktails/{slug}` — 상세
2. `/cocktails/base/{baseSlug}` — 기주 카테고리
3. `/cocktails/style/{stylePrimarySlug}` — 스타일 카테고리
4. `/cocktails/method/{methodSlug}` — 메이킹 카테고리
5. `/sitemap.xml` — `NFR-S-04`

**`styles`가 복수여도 카테고리 경로는 `style_primary` 하나만** 생성된다 (`R-C-2` 축 조합 금지). **결정** 다만 SPEC-05 §4는 "카테고리 2종"이라 표기했는데 SPEC-03 `FR-COCKTAIL-029`는 **3축 전부** 경로를 만든다 — 후자가 맞다고 보고 3종으로 간다. GAPS 등재.

## RED

### 호출 (SPEC-07 §4)

1. `발행_성공시_재생성_훅이_호출된다`
2. `요청에_X_Revalidate_Secret_헤더가_있다`
3. `요청_본문에_paths_배열이_있다`
4. `상세_경로가_포함된다` — `/cocktails/{slug}`
5. `기주_카테고리_경로가_포함된다` — `/cocktails/base/{slug}`
6. `스타일_카테고리_경로가_포함된다` — `style_primary` 기준
7. `메이킹_카테고리_경로가_포함된다`
8. `styles가_복수여도_style_primary_경로만_포함된다` (`R-C-2`)
9. `축_조합_경로가_포함되지_않는다` — `/cocktails/base/gin/style/sour` 같은 것 0건
10. `회수시에도_재생성이_호출된다` — 페이지가 사라져야 한다
11. `경로가_중복되면_제거된다`

### 실패 격리 (`NFR-R-03`) — 이 이슈의 요체

12. `훅_호출_실패가_발행을_롤백시키지_않는다` — 발행은 커밋됨
13. `훅이_타임아웃돼도_발행이_유지된다`
14. `훅_대상이_다운돼도_발행이_유지된다` — 연결 거부
15. `훅_실패가_로그로_남는다` (SPEC-07 §4)
16. `훅_실패가_사용자에게_500으로_보이지_않는다` — 발행은 200
17. `훅은_커밋_후에_호출된다` — 트랜잭션 안에서 부르면 롤백 시 유령 호출

### 시크릿

18. `시크릿이_환경변수에서_온다` — 하드코딩 없음
19. `시크릿이_없으면_기동이_실패한다` **결정** — 또는 훅 비활성화. **로컬은 비활성화 허용, 운영은 필수**
20. `시크릿이_로그에_남지_않는다`

### 반영 시간 (`NFR-O-02`)

21. `발행부터_훅_호출까지_지연이_측정된다` — 30초 예산의 서버 몫
22. `훅_호출이_비동기다` — 발행 응답을 막지 않는다 (`FR-COCKTAIL-016` "기다리지 않는다")

### 사이트맵 (`NFR-S-04`)

23. `사이트맵_경로도_재생성_대상이다`

## GREEN

### `common/revalidate`

```kotlin
interface RevalidateHook {
    fun revalidate(paths: List<String>)      // fire-and-forget
}

@Component
class HttpRevalidateHook(...) : RevalidateHook {
    override fun revalidate(paths: List<String>) {
        // 커밋 후 실행 (RED 17)
        // 실패는 삼키고 로그만 (RED 12~16, NFR-R-03)
    }
}
```

### 커밋 후 실행 (RED 17)

```kotlin
TransactionSynchronizationManager.registerSynchronization(
    object : TransactionSynchronization {
        override fun afterCommit() { revalidateHook.revalidate(paths) }
    }
)
```

또는 Spring의 `@TransactionalEventListener(phase = AFTER_COMMIT)`.
**`BEFORE_COMMIT`을 쓰지 않는다** — 커밋이 실패하면 유령 재생성이 나간다.

### 경로 산출 (RED 4~9)

```kotlin
object RevalidatePaths {
    fun forCocktail(c: CocktailSnapshot): List<String> = listOf(
        "/cocktails/${c.slug}",
        "/cocktails/base/${c.baseSpirit.slug}",
        "/cocktails/style/${c.stylePrimary.slug}",       // R-C-2: primary 하나만
        "/cocktails/method/${c.method.slug}",
        "/sitemap.xml",
    ).distinct()
}
```

**축 조합 경로를 만들 수 없는 구조**여야 한다 (RED 9). 문자열 조합 로직에 축 2개를 이어붙이는 경로가 없어야 한다.

### 수신측

`POST {FRONTEND_URL}/api/revalidate` **수신 구현은 이슈 038**(프론트)이다. 이 이슈는 **호출까지**.
038 미완이면 RED 1~11은 mock 서버로 검증한다.

### 재시도

SPEC-07 §4가 "ISR 주기가 결국 따라잡는다"고 했으므로 **재시도를 만들지 않는다.** 큐·백오프를 넣으면 복잡도만 늘고, 실패해도 자동 복구되는 설계다.

**하지 말 것**:
- 프론트 `/api/revalidate` 수신 — 이슈 038
- 재시도 큐

## DoD

- [ ] RED 23항 전부 통과
- [ ] **훅 실패가 발행을 롤백시키지 않음** (RED 12~16 — `NFR-R-03`)
- [ ] 훅이 **커밋 후** 비동기 (RED 17·22)
- [ ] 축 조합 경로 생성 불가 구조 (RED 9 — `R-C-2`)
- [ ] 시크릿이 환경변수, 로그 미노출
- [ ] 미결은 [`DECISIONS.md`](DECISIONS.md) §1 확정분을 따른다 — **이슈에서 판단하지 않는다**
- [ ] 커밋: `feat(api): 발행 시 on-demand 재생성 훅 (FR-COCKTAIL-016, FR-ADMIN-008, NFR-R-03)`
