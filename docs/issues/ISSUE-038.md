---
id: ISSUE-038
title: 칵테일 상세 SSG+ISR 연동
domain: COCKTAIL
layer: web
wave: 8
status: TODO
depends_on: [ISSUE-020, ISSUE-037]
fr: [FR-COCKTAIL-017, FR-COCKTAIL-018, FR-COCKTAIL-027]
r: []
inv: []
nfr: [NFR-S-01, NFR-P-05, NFR-R-01, NFR-O-02]
migration: —
owns:
  - apps/web/app/cocktails/[slug]/page.tsx
  - apps/web/app/cocktails/[slug]/layout.tsx
  - apps/web/app/api/revalidate/**
---

> **소유 경로 주의**: 같은 디렉터리의 `opengraph-image.tsx` 는 ISSUE-044 소유다.

## 근거

**`PRIN-T04` — SEO 경로는 정적 우선.** 검색 유입이 초기 성장의 절반이다 (PRD 2.2 — 유기 검색 50% 이상)
> 칵테일 상세·바 상세·카테고리 페이지는 **SSG + ISR. 요청 시 렌더하지 않는다**

**SPEC-05 §4 렌더링 전략** — **경로마다 렌더링 방식이 정해져 있다. 임의로 바꾸지 않는다**

| 경로 | 방식 | 재생성 | 색인 |
|---|---|---|---|
| `/cocktails/[slug]` | **SSG + ISR** | **발행 시 on-demand** | ✅ |

**SPEC-05 §4 on-demand 재생성**: 어드민에서 발행하면 **API가 프론트의 revalidate 훅을 호출**한다. 에디터가 발행하고 반영을 기다리지 않아야 한다

**SPEC-07 §4 재생성 훅** (이슈 015가 호출측)

```
POST {FRONTEND_URL}/api/revalidate
X-Revalidate-Secret: <공유 시크릿>
{ "paths": ["/cocktails/negroni", "/cocktails/base/gin"] }
```

**SPEC-07 §5**: **SSG 빌드와 브라우저가 같은 엔드포인트를 쓴다.** 별도의 내부 전용 조회 API를 두지 않는다 — **두 벌이 되면 반드시 어긋난다**

**SPEC-07 §1.2**: Next.js 서버 컴포넌트에서 API를 호출할 때는 **들어온 쿠키를 그대로 전달**한다

**`NFR-S-01`**: 칵테일·바 상세, 카테고리는 **SSG + ISR** — 빌드 리포트, **배포 차단**
**`NFR-P-05`**: 상세·카테고리 **TTFB ≤ 200ms** — SSG라 정적 응답이어야 한다
**SPEC-04 §1.1**: `/cocktails/{slug}` **LCP ≤ 2.0s** — SSG + ISR이라 더 엄격. **못 지키면 렌더링 전략이 어긋난 것이다**
**`NFR-R-01`**: **API가 죽어도 정적 페이지는 살아 있다** — **SSG를 고른 이유의 절반**
**`NFR-O-02`**: 발행 후 공개 반영 **≤ 30초**

**`FR-COCKTAIL-017`**: 8개 **필수 블록** (이슈 020 RED 13)
**`FR-COCKTAIL-018`**: 분류 3축 각각을 **카테고리 페이지로 링크** (`R-C-2`)
**`FR-COCKTAIL-028`**: 과음 경고 하단 고정 (이슈 032)

**SCREENS-01 칵테일** — 화면 정본
**현재**: `apps/web/app/cocktails/[id]/page.tsx` — **`[id]`가 아니라 `[slug]`로 바뀐다** (SPEC-07 §1.1)

## RED

### 렌더링 전략 (`NFR-S-01`, SPEC-05 §4)

1. `SSG로_생성된다` — 빌드 산출물에 정적 HTML 존재
2. `generateStaticParams가_발행분을_반환한다`
3. `ISR이_설정돼_있다`
4. `요청시_렌더하지_않는다` — `dynamic = 'force-dynamic'` 부재 (`PRIN-T04`)
5. `TTFB가_200ms_이하다` (`NFR-P-05`) — 정적 응답
6. `LCP가_2_0s_이하다` (SPEC-04 §1.1)

### 경로 (SPEC-07 §1.1)

7. `경로가_slug_기반이다` — `[id]` → `[slug]`
8. `기존_id_경로가_제거된다`
9. `없는_slug는_404다`
10. `draft_slug도_404다` (이슈 020 RED 3)

### 필수 블록 (`FR-COCKTAIL-017`)

11. `8개_블록이_렌더된다` — 히어로·분류·스펙·재료·만드는 법·향과 맛·국내 구매 가이드·액션
12. `블록이_초기_HTML에_있다` — 크롤러가 본다 (`PRIN-T04`)
13. `JS_없이_읽을_수_있다`

### 카테고리 링크 (`FR-COCKTAIL-018`)

14. `기주가_카테고리_페이지로_링크된다` — `/cocktails/base/{slug}`
15. `스타일이_링크된다` — `style_primary`
16. `메이킹이_링크된다`
17. `축_조합_링크가_없다` (`R-C-2`, `NFR-S-03`)

### 재생성 수신 (SPEC-07 §4 — 이슈 015 결합점)

18. `api_revalidate가_POST를_받는다`
19. `X_Revalidate_Secret이_검증된다`
20. `시크릿이_틀리면_401`
21. `paths_배열의_경로가_재생성된다`
22. `재생성_후_새_내용이_보인다`
23. `30초_이내에_반영된다` (`NFR-O-02`)
24. `시크릿이_클라이언트_번들에_없다` — 서버 전용 환경변수

### API 장애 격리 (`NFR-R-01`)

25. `API가_죽어도_정적_페이지가_보인다` — **SSG를 고른 이유의 절반**
26. `빌드된_페이지가_API_없이_서빙된다`
27. `ISR_재생성_실패가_기존_페이지를_지우지_않는다`

### 단일 엔드포인트 (SPEC-07 §5)

28. `SSG_빌드가_공개_API를_쓴다` — 내부 전용 API 부재
29. `브라우저와_같은_엔드포인트다`

### 색인 (`NFR-S-01`)

30. `noindex가_없다`
31. `canonical이_설정된다` ⚖️ + GAPS

### 액션 블록 (`FR-COCKTAIL-027`)

> **`FR-COCKTAIL-027`**: **저장 · 공유 · 내 술장 재료 대조** 액션을 제공한다 (**대조는 P2**)
> `FR-COCKTAIL-017`의 8개 블록 중 마지막 "액션"이 이것이다.

32. `저장_버튼이_있다` — 북마크 (이슈 031의 API)
33. `비로그인_저장시_로그인이_유도된다` ⚖️ — `R-F2.2-4`의 정신. 보수적으로 유도 + GAPS
34. `공유_버튼이_있다` — OG 카드 (이슈 044)
35. `내_술장_대조는_P2라_없다` (`FR-COCKTAIL-027` 괄호)
36. `저장이_bookmark_add_이벤트를_발생시킨다` (SPEC-10 §4.6 — 이슈 035)
37. `공유가_share_click_이벤트를_발생시킨다` — `channel` 포함

### 법적 (`NFR-L-01`)

38. `과음_경고가_하단에_있다` (이슈 032)

## GREEN

### `app/cocktails/[slug]/page.tsx`

```tsx
export async function generateStaticParams() {
  // SPEC-07 §5 — 공개 API를 쓴다. 내부 전용 API를 만들지 않는다
  const res = await fetch(`${API}/api/v1/cocktails?size=1000`);
  return (await res.json()).items.map((c) => ({ slug: c.slug }));
}

export const revalidate = 3600;   // ISR 폴백. 주 경로는 on-demand (SPEC-05 §4)
```

**`[id]` 디렉터리를 `[slug]`로 옮긴다** (RED 7·8). `PRIN-D02`상 slug가 공개 식별자다.

### `app/api/revalidate/route.ts` (RED 18~24)

```ts
export async function POST(req: Request) {
  const secret = req.headers.get("X-Revalidate-Secret");
  if (secret !== process.env.REVALIDATE_SECRET) {
    return new Response(null, { status: 401 });
  }
  const { paths } = await req.json();
  paths.forEach((p: string) => revalidatePath(p));
  return new Response(null, { status: 200 });
}
```

**`process.env.REVALIDATE_SECRET`은 `NEXT_PUBLIC_` 접두가 없어야 한다** (RED 24) — 붙이면 번들에 들어간다.

### API 장애 격리 (RED 25~27 — `NFR-R-01`)

SSG 페이지는 빌드 시점에 HTML이 만들어져 있다. **런타임에 API를 부르지 않는다.**
ISR 재생성이 실패하면 Next.js는 **기존 페이지를 계속 서빙**한다 — 기본 동작이지만 테스트로 고정한다.

### 쿠키 전달 (SPEC-07 §1.2)

상세는 공개 페이지라 인증이 없다. **어드민 미리보기**(이슈 045)에서 쿠키 전달이 필요해진다 — 그때 처리.

### 데이터 소스 전환

지금 `page.tsx`는 `packages/domain`의 `COCKTAILS` 배열을 읽는다.
→ **API fetch로 교체**. `data.ts` 직접 import 제거 (이슈 037 RED 21).

**하지 말 것**:
- 잔 수·단위·대체재 인터랙션 — 이슈 043
- Schema.org·OG — 이슈 044
- 배리에이션 표시 ⚖️ — 이슈 021의 API는 있다. **상세 화면 일부이므로 여기서 렌더**하되 GAPS에 경계 기록
- "이 칵테일을 마실 수 있는 바" — Phase 1b

## DoD

- [ ] RED 38항 전부 통과
- [ ] **액션 블록 3종** — 저장·공유(대조는 P2) (RED 32~35 — `FR-COCKTAIL-027`)
- [ ] **SSG + ISR** (RED 1~4 — `NFR-S-01` 배포 차단)
- [ ] LCP ≤ 2.0s, TTFB ≤ 200ms (RED 5·6)
- [ ] 경로가 `[slug]` (RED 7·8)
- [ ] **재생성 훅 수신** 동작, 시크릿 번들 미노출 (RED 18~24)
- [ ] **API 죽어도 페이지 생존** (RED 25 — `NFR-R-01`)
- [ ] `data.ts` 직접 import 제거
- [ ] ⚖️ 2건(canonical·배리에이션 렌더 위치) `GAPS.md` 등재
- [ ] 커밋: `feat(web): 칵테일 상세 SSG+ISR 연동 (FR-COCKTAIL-017·018, PRIN-T04, NFR-S-01)`
