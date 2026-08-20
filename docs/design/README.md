# 디자인 — Modernist

시안 출처: Claude Design 프로젝트 `27a7afff-c9db-44e9-834a-359e928c9b81`
원본 사본: [`source/MUT.dc.html`](./source/MUT.dc.html)

> **PRD 부록 A를 읽지 말 것.** 그건 시안을 못 본 상태에서 쓴 제안안이고 폐기됐다.
> 경위는 [ADR-0001](../decisions/ADR-0001-design-system.md).

## 정본은 문서가 아니라 CSS다

| 무엇 | 어디 |
|---|---|
| **토큰 · 컴포넌트 클래스** | `packages/ui/styles.css` ← 단일 진실 공급원 |
| 화면 레이아웃 · 반응형 | `packages/ui/app.css` |
| 시안 원본 (읽기 전용) | `docs/design/source/` |

색 · 폰트 · 간격 · 라운드 · 그림자는 **전부 변수에서 가져온다.**
토큰이 이미 담고 있는 hex · 폰트명 · px를 하드코딩하지 않는다.

## 성격

플랫하고 건축적이며 전부 Archivo로 짠다. 흰 바탕 위 거의 단색인 빨강,
보이는 모듈러 그리드, 라운드 0, 강한 2px 규칙선. **떠 있는 것도 장식된 것도 없다** —
정렬과 구분선의 세기가 조직화를 전담한다.

## 지킬 것

- **라운드를 주지 않는다.** `--radius-md`가 0인 건 의도다.
- **전부 왼쪽 정렬.** 제목·본문은 물론 **넓은 버튼 안의 레이블도** 가운데 정렬하지 않는다.
- **구분선을 여백으로 바꾸지 않는다.** 섹션 사이는 2px `var(--color-divider)`.
- **사진은 컬러로 낸다** ([ADR-0008](../decisions/ADR-0008-color-photos.md)) — 시안의 흑백 규칙을 뗐다. 다만 **착색하지는 않는다**: 원본 그대로 내보내고 accent 를 이미지에 얹지 않는다.
- accent는 아껴 쓴다. 주요 액션과 작은 강조에만. 시스템은 대부분 바탕 위의 잉크다.

## 색

라이트 그라운드 `#f3f2f2` + 텍스트 `#201e1d` + 단일 accent `#ec3013`.
**단색 체계다** — `--color-accent-2-*`는 기계 생성 대역이라 accent와 같은 역할로 취급한다.

각 역할은 100–900 톤 램프를 갖는다. OKLCH 공통 밝기 스케일로 생성돼서
어느 램프든 같은 단계는 같은 시각적 무게를 가진다.

- 100–300 — 틴트 채움, 호버, 옅은 보더
- 500 — 역할의 기준값
- 700–900 — 틴트 위 텍스트, 눌린 상태

임시 `color-mix()`보다 램프 단계를 먼저 쓴다.

> ⚠ **accent↔ground 대비는 3:1이다.** 아이콘 · 큰 글자 · 인터페이스 크롬까지만 쓸 수 있고
> **본문 크기 텍스트에는 못 쓴다.** 그럴 땐 `--color-accent-700`을 쓴다. (WCAG AA 전제)

## 타이포

제목도 본문도 Archivo. 밀도 1.00×, 라운드 0px은 `--space-*` / `--radius-*`에 이미 반영돼 있으니
변수를 쓰고 생 숫자를 쓰지 않는다.

한글은 Noto Sans KR이 이어받고, **에디토리얼 본문만 Noto Serif KR**(`--font-serif`)을 쓴다 —
칵테일 스토리와 이미지 자리 캡션이 여기 해당한다.

## 상태

상태는 이미 만들어져 있다. 페이지마다 다시 칠하지 말 것.

- 호버 · 눌림 — accent 램프에서 (기준값 한 단계 옆)
- 키보드 포커스 — `:focus-visible { outline: 2px solid var(--color-accent); outline-offset: 2px }`
- `::selection` — accent 틴트
- 비활성 — opacity 0.45

브라우저 기본 파란 포커스 링을 남기지 않는다.

## 컴포넌트

| 클래스 | 무엇 |
|---|---|
| `.btn` + `.btn-primary` `.btn-secondary` `.btn-ghost` `.btn-icon` `.btn-block` | 액션. primary는 단색 채움 — 바탕은 `app.css` 에서 `accent-700` 로 덮는다 ([ADR-0006](../decisions/ADR-0006-btn-primary-contrast.md)) |
| `.tag` + `.tag-accent` `.tag-neutral` `.tag-outline` | 램프에서 틴트한 작은 라벨 |
| `.field` + `label`, `.input`, `.radio` + `.dot`, `.seg` + `.seg-opt` | 폼. 네이티브 요소 기반, 스크립트 없음 |
| `.card` + `.card-kicker` `.card-title` `.card-body` `.card-meta`, `.elev-sm/md/lg` | 콘텐츠 카드와 elevation |
| `.nav` + `.nav-brand` | 헤더 바 |
| `.table` | 데이터 테이블 |
| `.dialog-backdrop` + `.dialog` | 모달 |
| `.hr` | 2px 규칙선 |
| `.grayscale` | 이미지 래퍼 — **콘텐츠 사진에는 안 씌운다** ([ADR-0008](../decisions/ADR-0008-color-photos.md)). 클래스는 되돌릴 자리로 남겨 둔다 |

화면 단위 클래스(`.cocktail-card`, `.filter-panel`, `.spec-strip` …)는 `app.css`에 있다.
새 화면을 만들 때 위 목록에 있는 걸 두고 평행한 클래스를 새로 만들지 않는다.

## 반응형

시안은 데스크톱 그리드만 잡았다. `app.css`가 그 배치를 ≥1080px 케이스로 두고
840px · 560px에서 단으로 무너뜨린다. PRD 12장이 모바일 트래픽 80%를 가정하므로
**새 레이아웃을 추가할 때 반응형은 선택이 아니다.**
