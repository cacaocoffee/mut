# 디자인 — 매거진판

Modernist를 대체한 2대 시스템이다. 경위와 되돌리는 조건은
[ADR-0009](../decisions/ADR-0009-design-magazine.md), 이전 시스템의 채택 경위는
[ADR-0001](../decisions/ADR-0001-design-system.md).

> Modernist 시안 원본은 [`source/MUT.dc.html`](./source/MUT.dc.html)에 역사 기록으로 남아 있다.
> **더 이상 대조 대상이 아니다** — hex 정본은 `scripts/color-parity.mjs`의 BASELINE이다.

## 정본은 문서가 아니라 CSS다

| 무엇 | 어디 |
|---|---|
| **토큰 · 컴포넌트 클래스** | `packages/ui/styles.css` ← 단일 진실 공급원 |
| 색의 hex 정본 (왕복 대조) | `scripts/color-parity.mjs` BASELINE |
| 화면 레이아웃 · 반응형 | `packages/ui/app.css` |

색 · 폰트 · 간격 · 라운드 · 그림자는 **전부 변수에서 가져온다.**
토큰이 이미 담고 있는 hex · 폰트명 · px를 하드코딩하지 않는다.

## 성격

잡지 지면처럼 짠다. 아이보리 바탕 위 잉크와 와인색 한 벌, 제목은 세리프
(Fraunces + Noto Serif KR, 600), 본문은 IBM Plex Sans KR. 라운드 0,
떠 있는 것도 장식된 것도 없다 — **규칙선의 세기가 조직화를 전담한다.**
강한 경계는 3px `--color-text`, 조직 구분은 2px, 항목 구분은 1px `--color-divider`.

## 지킬 것

- **라운드를 주지 않는다.** `--radius-md`가 0인 건 의도다.
- **전부 왼쪽 정렬.** 제목·본문은 물론 넓은 버튼 안의 레이블도 가운데 정렬하지 않는다.
  (예외는 마스트헤드의 워드마크 하나 — 잡지 제호는 중앙이다.)
- **구분선을 여백으로 바꾸지 않는다.**
- **사진은 컬러로 낸다** ([ADR-0008](../decisions/ADR-0008-color-photos.md)) — 시안의 흑백 규칙을 뗐다. 다만 **착색하지는 않는다**: 원본 그대로 내보내고 accent 를 이미지에 얹지 않는다.
- **카드보다 선, 컨트롤은 뱃지.** 콘텐츠(카드·목록)는 상자 대신 헤어라인으로 가르되,
  누르는 것(필터 칩·당도 칸)은 **뱃지**다 — 기본 테두리 상자 · 호버 옅은 채움 ·
  선택 accent-700 채움. 내비 탭·쪽 넘김만 밑줄 인디케이터를 쓴다 (ISSUE-055).
- accent는 아껴 쓴다. 링크 · 선택 표시 · 작은 강조에만. 시스템은 대부분 바탕 위의 잉크다.

## 색

아이보리 그라운드 `#f6f3eb` + 웜 블랙 `#241e18` + 와인 accent `#93293b`.
`--color-accent-2-*`(로즈 브라운)는 당도 태그처럼 accent보다 낮은 온도가 필요한 자리용이다.

각 역할은 100–900 톤 램프를 갖는다. Modernist와 **같은 OKLCH 명도 스케일**로 생성돼
어느 램프든 같은 단계는 같은 시각적 무게를 가진다 — 그래서 `contrast-check.mjs`의
쌍 등록이 시스템 교체 후에도 그대로 성립한다.

- 100–300 — 틴트 채움, 호버, 옅은 보더
- 500 — 역할의 기준값
- 700–900 — 틴트 위 텍스트, 눌린 상태

임시 `color-mix()`보다 램프 단계를 먼저 쓴다.

> accent 원색은 바탕 위 7.23:1로 본문 AA를 넘지만, **글자에는 여전히 `--color-accent-700`을
> 쓴다** — 가드(게이트 14)와 SPEC-04 §2.1 실측표가 그 규칙으로 굳어 있고,
> 완화는 실측표 갱신과 함께 별도 결정으로 한다 (ADR-0009 결정 1).

## 타이포

- **제목** — `--font-heading`: Fraunces(라틴) + Noto Serif KR(한글), 무게 600.
- **본문** — `--font-body`: IBM Plex Sans KR.
- **에디토리얼 본문** — `--font-serif`: Noto Serif KR. 칵테일 스토리와 캡션.
- 숫자 열은 `font-variant-numeric: tabular-nums` (가드 게이트 10이 강제).

## 상태

상태는 이미 만들어져 있다. 페이지마다 다시 칠하지 말 것.

- 선택 — 뱃지는 `accent-700` 채움 + `--color-bg` 글자 · 탭/쪽 넘김은 와인 글자 + 2px 밑줄
- 호버 — 뱃지는 테두리 진해지고 옅은 채움(잉크 6%) · 링크는 글자를 `--color-text`/`accent-700`으로
- 키보드 포커스 — `:focus-visible { outline: 2px solid var(--color-accent); outline-offset: 2px }`
- `::selection` — accent 틴트
- 비활성 — opacity + 이유를 글자로 병기 (`0` 등)

## 컴포넌트

| 클래스 | 무엇 |
|---|---|
| `.btn` + `.btn-primary` `.btn-secondary` `.btn-ghost` `.btn-icon` `.btn-block` | 액션. primary 바탕은 `accent-700` ([ADR-0006](../decisions/ADR-0006-btn-primary-contrast.md)) |
| `.tag` + `.tag-accent` `.tag-neutral` `.tag-outline` | 램프에서 틴트한 작은 라벨 |
| `.field` + `label`, `.input`, `.radio` + `.dot`, `.seg` + `.seg-opt` | 폼. 네이티브 요소 기반, 스크립트 없음 |
| `.card` + `.card-kicker` `.card-title` `.card-body` `.card-meta`, `.elev-sm/md/lg` | 콘텐츠 카드와 elevation |
| `.nav` + `.nav-brand` | 마스트헤드 |
| `.table` | 데이터 테이블 |
| `.dialog-backdrop` + `.dialog` | 모달 |
| `.hr` | 규칙선 |
| `.grayscale` | 이미지 래퍼 — **콘텐츠 사진에는 안 씌운다** ([ADR-0008](../decisions/ADR-0008-color-photos.md)). 클래스는 되돌릴 자리로 남겨 둔다 |

화면 단위 클래스(`.cocktail-card`, `.filter-panel`, `.spec-strip` …)는 `app.css`에 있다.
새 화면을 만들 때 위 목록에 있는 걸 두고 평행한 클래스를 새로 만들지 않는다.

## 반응형

`app.css`가 데스크톱 배치를 ≥1080px 케이스로 두고 840px · 560px에서 단으로 무너뜨린다.
PRD 12장이 모바일 트래픽 80%를 가정하므로 **새 레이아웃을 추가할 때 반응형은 선택이 아니다.**
