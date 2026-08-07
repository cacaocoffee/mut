---
id: ISSUE-046
title: CI 자동 게이트 — Lighthouse · axe · 사이트맵 · 번들
domain: —
layer: web
wave: 8
status: TODO
depends_on: [ISSUE-038, ISSUE-039, ISSUE-040, ISSUE-042, ISSUE-047]
fr: []
r: []
inv: []
nfr: [NFR-P-01, NFR-P-02, NFR-P-03, NFR-P-04, NFR-P-06, NFR-P-07, NFR-A-01, NFR-A-02, NFR-A-03, NFR-A-05, NFR-S-02, NFR-S-03, NFR-D-01]
migration: —
owns:
  - .github/workflows/web-quality.yml
  - apps/web/lighthouserc.js
  - apps/web/.axerc.json
---

> **분할됨** — 이 이슈는 **CI가 자동으로 막는 것**까지다.
> 수동 체크리스트와 G-16 결정은 [050](ISSUE-050.md).

## 근거

**SPEC-04 §9 릴리즈 게이트** — **배포를 막는 것**과 경고만 하는 것을 구분한다.
**전부를 차단으로 만들면 아무것도 못 나간다.**

### §9.1 자동 — CI에서 검사 (이 이슈)

| 검사 | 대상 | 성격 |
|---|---|---|
| `npm run check` | `NFR-D-01` | 차단 |
| ESLint · TypeScript | — | 차단 |
| **Lighthouse CI** | `NFR-P-01~03` | **차단** |
| **axe-core** | `NFR-A-01~03`, `A-05` | **차단** |
| 번들 크기 | `NFR-P-04` | **경고** |
| 사이트맵 검사 | `NFR-S-02~03` | 차단 |

### 성능 (SPEC-04 §1) — 모바일 · 4G Slow · p75

| ID | 기준 | 실패 시 |
|---|---|---|
| `NFR-P-01` | **LCP ≤ 2.5s** | 배포 차단 |
| `NFR-P-02` | **INP ≤ 200ms** | 배포 차단 |
| `NFR-P-03` | **CLS ≤ 0.1** | 배포 차단 |
| `NFR-P-04` | 초기 JS ≤ **150KB** (gzip) | **경고** |
| `NFR-P-06` | 이미지 **WebP/AVIF** + `loading="lazy"` | 배포 차단 |
| `NFR-P-07` | 카드 이미지 전송폭 ≤ **480px** | 경고 |

**SPEC-04 §1.1**: `/cocktails/{slug}`·카테고리 **≤ 2.0s** — SSG+ISR이라 더 엄격.
**못 지키면 렌더링 전략이 어긋난 것이다.**

### 접근성 — §2.1 실측 대비표가 근거다

> **추정이 아니라 계산 결과이며, 이 표가 사용 가능 여부의 근거다.**

| 토큰 | 대비 | 본문(4.5:1) |
|---|---|---|
| `--color-text` | 14.86:1 | ✅ |
| `--color-accent-700` | 6.41:1 | ✅ |
| `--color-neutral-700` | 5.83:1 | ✅ |
| `--color-neutral-600` | 3.85:1 | ❌ |
| **`--color-accent`** | **3.76:1** | ❌ |
| `--color-neutral-500` | 2.59:1 | ❌ |

| ID | 기준 |
|---|---|
| `NFR-A-01` | 본문 **4.5:1 이상.** accent를 본문에 쓰지 않는다 — `accent-700` |
| `NFR-A-02` | 큰 글자·UI **3:1 이상** |
| `NFR-A-03` | **`neutral-500`을 텍스트에 쓰지 않는다** |
| `NFR-A-05` | `:focus-visible` 2px accent. **기본 링을 제거만 하지 않는다** |

**SPEC-04 §2.3**: ADR-0001이 각주로 경고했지만 **코드가 따르지 않고 있었다** — 18군데.
**"각주는 강제되지 않는다는 증거"** 라 `NFR-A-01`을 배포 차단으로 올렸다.

## RED

### CI 구성 (SPEC-04 §9.1)

1. `Lighthouse_CI가_동작한다` — 모바일 프리셋
2. `axe_core가_동작한다`
3. `사이트맵_검사가_동작한다`
4. `npm_run_check가_CI에_있다` (`NFR-D-01`)
5. **`차단과_경고가_구분된다`** — SPEC-04 §9 "전부를 차단으로 만들면 아무것도 못 나간다"

### 성능 차단 (`NFR-P-01~03`)

6. `LCP_2_5s_초과시_실패한다`
7. `상세_카테고리는_2_0s_기준이다` (SPEC-04 §1.1)
8. `INP_200ms_초과시_실패한다`
9. `CLS_0_1_초과시_실패한다`
10. `SSG_경로가_2_0s를_못_지키면_렌더링_전략_위반으로_보고된다`

### 성능 경고 (`NFR-P-04`·`P-07`)

11. `번들_150KB_초과시_경고한다` — **차단 아님**
12. `카드_이미지_전송폭_480px_초과시_경고한다`

### 이미지 (`NFR-P-06`)

13. `이미지가_WebP_또는_AVIF다`
14. `히어로_외에_loading_lazy가_있다`
15. `반응형_sizes가_설정된다`

### 대비 (`NFR-A-01~03`)

16. `본문에_accent를_쓰지_않는다` — 3.76:1
17. `본문에_neutral_600을_쓰지_않는다` — 3.85:1
18. `neutral_500이_텍스트에_없다` — 2.59:1
19. `본문은_accent_700_또는_neutral_700_이상이다`
20. `큰_글자_UI는_3_1_이상이다`
21. `소스_검사와_axe_양쪽에서_잡는다` — 렌더 안 되는 코드도 걸린다
22. `SPEC_04_§2_1_실측표와_일치한다` — 토큰별 대비 재계산

### 포커스 (`NFR-A-05`)

23. `focus_visible이_2px_accent다`
24. `기본_포커스_링을_제거만_하지_않았다`

### 사이트맵 (`NFR-S-02`·`S-03`)

25. **`축_조합_경로가_0개다`** — 이슈 039 RED 8과 쌍
26. `필터_경로가_사이트맵에_없다`
27. `카테고리_경로가_사이트맵에_있다`
28. `draft가_사이트맵에_없다`
29. `경로_패턴_밖의_URL이_있으면_실패한다` — allowlist 방식

### ⚠️ `.btn-primary` (G-16)

30. `btn_primary_대비_검사가_현재_실패한다` — **3.76:1로 `NFR-A-01` 미달**
    → **[050](ISSUE-050.md)의 결정 전까지 이 항목만 예외 등록**하고 사유를 주석에 남긴다

## GREEN

```yaml
# .github/workflows/web-quality.yml — SPEC-04 §9.1
- run: npm run check                    # NFR-D-01        차단
- run: npx tsc --noEmit && npx eslint . #                 차단
- run: npx lhci autorun                 # NFR-P-01~03     차단
- run: npx axe-ci                       # NFR-A-01~03·A-05 차단
- run: npm run sitemap-check            # NFR-S-02~03     차단
- run: npm run bundle-check             # NFR-P-04        경고
  continue-on-error: true               #                 ← 여기가 §9의 요점
```

**`continue-on-error`로 차단/경고를 구분한다** (RED 5).

### 대비 검사는 두 겹 (RED 21)

axe-core는 **렌더된 화면**을 본다. 렌더 안 되는 분기·조건부 클래스는 안 걸린다.
그래서 **소스 검사**를 함께 넣는다:

```bash
# NFR-A-01·A-03 — 금지 토큰이 텍스트 색으로 쓰였는가
grep -rn "color: var(--color-accent)"      apps/web   # 3.76:1
grep -rn "color: var(--color-neutral-500)" apps/web   # 2.59:1
```

SPEC-04 §2.3이 **"각주는 강제되지 않는다"** 며 배포 차단으로 올린 이유가 이것이다.

### 사이트맵 allowlist (RED 25·29)

```
/cocktails/{slug}
/cocktails/(base|style|method)/{slug}
```

패턴 밖 경로가 있으면 실패. `NFR-S-03`("축 조합 경로 0개")이 이것으로 구현된다.

**하지 말 것**:
- 수동 체크리스트 — 이슈 050
- G-16 결정 — 이슈 050
- `packages/ui` 수정 (ADR-0001)

## DoD

- [ ] RED 30항 전부 통과 (30은 예외 등록 + 사유 주석)
- [ ] **차단/경고 구분** (RED 5 — SPEC-04 §9)
- [ ] 대비 검사가 **axe + 소스 두 겹** (RED 21)
- [ ] 사이트맵 축 조합 0개 (RED 25 — `NFR-S-03`)
- [ ] 번들은 **경고** (RED 11)
- [ ] `.btn-primary` 예외에 G-16 참조 주석
- [ ] 커밋: `chore(web): CI 자동 게이트 (SPEC-04 §9.1)`
