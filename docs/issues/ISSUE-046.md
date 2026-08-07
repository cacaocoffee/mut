---
id: ISSUE-046
title: 접근성 · 성능 릴리즈 게이트
domain: —
layer: web
wave: 8
status: TODO
depends_on: [ISSUE-038, ISSUE-039, ISSUE-040, ISSUE-041, ISSUE-042, ISSUE-043, ISSUE-044, ISSUE-045]
fr: []
r: []
inv: []
nfr: [NFR-P-01, NFR-P-02, NFR-P-03, NFR-P-04, NFR-P-06, NFR-P-07, NFR-A-01, NFR-A-02, NFR-A-03, NFR-A-04, NFR-A-05, NFR-A-07, NFR-A-08, NFR-A-09, NFR-S-02, NFR-S-03, NFR-L-01, NFR-L-04]
migration: —
owns:
  - .github/workflows/web-quality.yml
  - apps/web/lighthouserc.js
  - docs/RELEASE-CHECKLIST.md
---

## 근거

**SPEC-04 §9 릴리즈 게이트** — **배포를 막는 것**과 경고만 하는 것을 구분한다. **전부를 차단으로 만들면 아무것도 못 나간다**

### 9.1 자동 — CI에서 검사

| 검사 | 대상 |
|---|---|
| `npm run check` | `NFR-D-01` |
| ESLint · TypeScript | — |
| **Lighthouse CI** | `NFR-P-01~03` |
| **axe-core** | `NFR-A-01~03`, `A-05` |
| 번들 크기 | `NFR-P-04` (**경고**) |
| **사이트맵 검사** | `NFR-S-02~03` |

### 9.2 수동 — 릴리즈 체크리스트

`NFR-A-04` `A-06~09` · `NFR-S-06` · `NFR-R-01~02` · `NFR-O-01` · `NFR-L-01~04`

### 9.3 정식 오픈 전 1회

`NFR-L-05` **법률 검토** · **호스팅 확정** · **이미지 저장소 확정** ← **G-07**

### 성능 (SPEC-04 §1) — 모바일 · 4G Slow · p75

| ID | 기준 | 실패 시 |
|---|---|---|
| `NFR-P-01` | **LCP ≤ 2.5s** | **배포 차단** |
| `NFR-P-02` | **INP ≤ 200ms** | **배포 차단** |
| `NFR-P-03` | **CLS ≤ 0.1** | **배포 차단** |
| `NFR-P-04` | 초기 JS ≤ **150KB** (gzip) | 경고 |
| `NFR-P-05` | 상세·카테고리 **TTFB ≤ 200ms** | 렌더링 전략 위반 조사 |
| `NFR-P-06` | 이미지 **WebP/AVIF** + `loading="lazy"` | **배포 차단** |
| `NFR-P-07` | 카드 이미지 전송폭 ≤ **480px** | 경고 |

**SPEC-04 §1.1 페이지별 예산**: `/cocktails/{slug}`·카테고리 **≤ 2.0s** (SSG+ISR이라 더 엄격). **못 지키면 렌더링 전략이 어긋난 것**

### 접근성 (SPEC-04 §2) — WCAG 2.2 AA

**§2.1 실측 대비표** — **추정이 아니라 계산 결과이며, 이 표가 사용 가능 여부의 근거다**

| 토큰 | 대비 | 본문(4.5:1) | 큰 글자·UI(3:1) |
|---|---|---|---|
| `--color-text` | **14.86:1** | ✅ | ✅ |
| `--color-accent-700` | **6.41:1** | ✅ | ✅ |
| `--color-neutral-700` | **5.83:1** | ✅ | ✅ |
| `--color-accent-600` | 4.25:1 | ❌ | ✅ |
| `--color-neutral-600` | 3.85:1 | ❌ | ✅ |
| **`--color-accent`** | **3.76:1** | ❌ | ✅ |
| `--color-neutral-500` | 2.59:1 | ❌ | ❌ |

| ID | 기준 | 실패 시 |
|---|---|---|
| `NFR-A-01` | 본문 **4.5:1 이상.** accent를 본문에 쓰지 않는다 — `accent-700` | **배포 차단** |
| `NFR-A-02` | 큰 글자·UI **3:1 이상** | **배포 차단** |
| `NFR-A-03` | **`neutral-500`을 텍스트에 쓰지 않는다** | **배포 차단** |
| `NFR-A-04` | 키보드 도달·조작 | **배포 차단** |
| `NFR-A-05` | `:focus-visible` 2px accent. **기본 링을 제거만 하지 않는다** | **배포 차단** |
| `NFR-A-06` | 비활성 칩 `disabled` + **개수 읽어주기** | 경고 |
| `NFR-A-07` | 차트 `role="img"` + `aria-label`, **수치를 표로도** | **배포 차단** |
| `NFR-A-08` | **색만으로 정보 전달 금지** | **배포 차단** |
| `NFR-A-09` | `prefers-reduced-motion` 존중 | 경고 |

### ⚠️ G-16 잔여 — `.btn-primary`

**SPEC-04 §2.4**: `.btn-primary`는 accent 배경 위 `--color-bg` 글자로 **3.76:1**. 14px/800 레이블은 "큰 글자"(18.66px+ bold)에 미치지 못해 **본문 기준 4.5:1이 적용되고, 미달이다.**

`styles.css`는 시안 정본이라 ADR-0001에 따라 **임의로 고치지 않는다.** 선택지 셋 — **제품 결정이다.**

| 안 | 내용 | 비용 |
|---|---|---|
| 가 | 배경을 `--color-accent-700`로 (6.41:1) | 시안의 선명한 빨강이 어두워진다 |
| 나 | 레이블을 18.66px+ bold로 | 버튼이 커진다 |
| 다 | 현행 유지 + 예외 문서화 | AA 미달을 안고 간다 |

**이 이슈에서 결정을 요청한다.** 미결이면 `NFR-A-01` 배포 차단과 충돌한다.

## RED

### CI 자동 (SPEC-04 §9.1)

1. `Lighthouse_CI가_동작한다` — 모바일 프리셋
2. `LCP_2_5s_초과시_실패한다` (`NFR-P-01`)
3. `상세_카테고리는_2_0s_기준이다` (SPEC-04 §1.1)
4. `INP_200ms_초과시_실패한다` (`NFR-P-02`)
5. `CLS_0_1_초과시_실패한다` (`NFR-P-03`)
6. `axe_core가_동작한다`
7. `대비_위반시_실패한다` (`NFR-A-01~03`)
8. `focus_visible_누락시_실패한다` (`NFR-A-05`)
9. `번들_150KB_초과시_경고한다` — **차단 아님** (`NFR-P-04`)
10. `사이트맵_검사가_동작한다` (`NFR-S-02~03`)
11. `축_조합_경로_발견시_실패한다` (`NFR-S-03` — 이슈 039 RED 8)
12. `npm_run_check가_CI에_있다` (`NFR-D-01`)
13. `차단과_경고가_구분된다` — SPEC-04 §9 "전부를 차단으로 만들면 아무것도 못 나간다"

### 대비 (SPEC-04 §2.1·§2.2)

14. `본문에_accent를_쓰지_않는다` (`NFR-A-01`) — `--color-accent`(3.76:1)
15. `본문에_neutral_600을_쓰지_않는다` — 3.85:1
16. `neutral_500이_텍스트에_없다` (`NFR-A-03`) — 2.59:1
17. `본문은_accent_700_또는_neutral_700_이상이다`
18. `큰_글자_UI는_3_1_이상이다` (`NFR-A-02`)
19. `SPEC_04_§2_1_실측표와_일치한다` — 토큰별 대비 재계산

### 이미지 (`NFR-P-06`·`P-07`)

20. `이미지가_WebP_또는_AVIF다`
21. `히어로_외에_loading_lazy가_있다`
22. `카드_이미지_전송폭이_480px_이하다` — 경고
23. `반응형_sizes가_설정된다`

### 키보드·스크린리더 (수동 — SPEC-04 §9.2)

24. `모든_인터랙션이_키보드로_도달한다` (`NFR-A-04`)
25. `focus_visible이_2px_accent다` (`NFR-A-05`)
26. `기본_포커스_링을_제거만_하지_않았다`
27. `색만으로_정보를_전달하지_않는다` (`NFR-A-08`)
28. `차트에_role_img와_aria_label이_있다` (`NFR-A-07`) — 맛 레이더는 **P1**이라 Phase 1a에 없을 수 있다 **결정**
29. `차트_수치가_표로도_제공된다` (`NFR-A-07`)
30. `prefers_reduced_motion을_존중한다` (`NFR-A-09`) — 경고

### 신뢰성 (수동)

31. `API가_죽어도_정적_페이지가_보인다` (`NFR-R-01` — 이슈 038 RED 25)
32. `API_장애시_필터_검색만_실패하고_안내가_뜬다` (`NFR-R-02`)

### 법적 (수동 — `NFR-L-01~04`)

33. `과음_경고가_모든_페이지_하단에_있다`
34. `제휴_라벨이_끌_수_없게_표기된다`
35. `파트너_배지와_제휴_라벨이_다른_색이다` — Phase 1b 대비 (이슈 032 RED 20)
36. `개인정보_처리방침_이용약관_페이지가_있다`

### 인수 (`NFR-O-01`)

37. `에디터가_개발자_없이_1건을_발행한다` (이슈 045 RED 41)

### G-16 결정

38. **`btn_primary_대비_결정이_내려졌다`** — 가/나/다 중 하나. **미결이면 이 이슈가 `BLOCKED`**

### 체크리스트 문서

39. `수동_항목이_문서화돼_있다` — SPEC-04 §9.2 목록
40. `정식_오픈_전_1회_항목이_있다` — `NFR-L-05` 법률 검토 · **G-07 호스팅·이미지 저장소**

## GREEN

### `.github/workflows/web-quality.yml`

```yaml
- run: npm run check                    # NFR-D-01
- run: npx lhci autorun                 # NFR-P-01~03 (차단)
- run: npx axe-ci                       # NFR-A-01~03·A-05 (차단)
- run: npm run bundle-check             # NFR-P-04 (경고 — continue-on-error)
- run: npm run sitemap-check            # NFR-S-02~03 (차단)
```

**`continue-on-error`로 차단/경고를 구분한다** (RED 13 — SPEC-04 §9의 요점).

### 대비 검사 (RED 14~19)

axe-core가 렌더된 화면을 본다. 그와 별개로 **소스 검사**도 넣는다:

```
// NFR-A-01·A-03 — 금지 토큰이 텍스트 색으로 쓰이지 않았는가
grep -rn "color: var(--color-accent)" apps/web packages/ui  # accent(3.76:1)
grep -rn "color: var(--color-neutral-500)"                   # 2.59:1
```

SPEC-04 §2.3이 **"각주는 강제되지 않는다는 증거"** 라며 이 검사를 배포 차단으로 올린 이유다.

### `docs/RELEASE-CHECKLIST.md` (RED 39·40)

SPEC-04 §9.2·§9.3을 체크박스로 옮긴다. **자동화할 수 없는 것들**이다.

### ⚠️ G-16 결정 요청 (RED 38)

이 이슈는 **`.btn-primary` 결정 없이 완료할 수 없다.**

- `NFR-A-01`이 배포 차단인데 `.btn-primary`가 3.76:1이다
- `packages/ui`는 ADR-0001상 수정 금지 (CONVENTIONS §4)

→ **사용자 결정 필요.** 미결이면 `INDEX.md`에서 이 이슈를 `BLOCKED` + `G-16`으로 표시한다.

**하지 말 것**:
- `packages/ui` 임의 수정 (ADR-0001) — 결정 후에만
- 전부를 배포 차단으로 (SPEC-04 §9)

## DoD

- [ ] RED 40항 전부 통과 (28·29는 P1 차트 부재 시 스킵)
- [ ] **차단/경고 구분** (RED 13 — SPEC-04 §9)
- [ ] Lighthouse·axe·사이트맵 검사가 CI에서 차단
- [ ] 번들 크기는 **경고** (RED 9)
- [ ] `docs/RELEASE-CHECKLIST.md` 작성 (수동 + 오픈 전 1회)
- [ ] **G-16 `.btn-primary` 결정** — 미결이면 `BLOCKED`
- [ ] G-07(호스팅·이미지 저장소)이 오픈 전 항목에 명시
- [ ] 커밋: `chore(web): 접근성·성능 릴리즈 게이트 (SPEC-04 §9)`
