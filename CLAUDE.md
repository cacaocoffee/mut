# MUT — 작업 규칙

> 이 파일은 세션 시작 시 자동으로 읽힌다. 새 규칙은 여기에 적는다.
> `apps/web/AGENTS.md`와 `apps/web/CLAUDE.md`는 `next dev`가 생성·갱신하는 파일이라 손대지 않는다.

## 작업은 GitHub 이슈에서 집는다

원격: **`cacaocoffee/mut`** (private). 커밋 author 는 `cacaocoffee <cacaocoffee16@gmail.com>`.

```bash
gh issue list --state open --label wave-0     # 착수 가능한 것
gh issue view 15                               # 근거 · RED · GREEN · DoD 가 본문에 있다
```

**규약은 [`docs/issues/CONVENTIONS.md`](docs/issues/CONVENTIONS.md) 다. 이슈를 집기 전에 읽는다.**
웨이브 편성·의존 DAG·결합점은 [`docs/issues/INDEX.md`](docs/issues/INDEX.md), 미결 171건은 [`docs/issues/DECISIONS.md`](docs/issues/DECISIONS.md).

`main` 에 직접 커밋하지 않는다 — 브랜치 → PR(`Closes #N`) → CI 초록 → 머지.

## 출처가 넷이다. 하나만 열어라

성격이 다른 네 출처가 있고 다 읽으면 4,000줄이 넘는다.
**하는 일에 해당하는 것만 연다.** 전체 지도는 [`docs/README.md`](docs/README.md).

| 하는 일 | 여는 곳 | 열지 않는 곳 |
|---|---|---|
| **무엇이든 시작할 때** | [`docs/spec/SPEC-00_개발원칙.md`](docs/spec/SPEC-00_개발원칙.md) — 다른 모든 문서보다 우선 | — |
| 제품 요구사항 확인 | [`docs/prd/README.md`](docs/prd/README.md) — 표에서 줄 범위를 보고 그 구간만 | PRD 전문 통독 |
| 도메인 규칙 · 불변식 · 발행 게이트 | [`SPEC-02 도메인모델`](docs/spec/SPEC-02_도메인모델.md) | — |
| 기능 요구사항 (`FR-*`) | [`SPEC-03 기능요구사항`](docs/spec/SPEC-03_기능요구사항.md) | — |
| 스택 · 모듈 경계 · 렌더링 전략 | [`SPEC-05 아키텍처`](docs/spec/SPEC-05_아키텍처.md) | — |
| 테이블 · 제약 · 인덱스 | [`SPEC-06 ERD`](docs/spec/SPEC-06_데이터모델_ERD.md) | — |
| 엔드포인트 · 에러 · 인증 | [`SPEC-07 API명세`](docs/spec/SPEC-07_API명세.md) | — |
| 권한 · 스코프 · 개인정보 | [`SPEC-08 보안·권한`](docs/spec/SPEC-08_보안_권한_개인정보.md) | — |
| 색 · 간격 · 컴포넌트 | [`docs/design/README.md`](docs/design/README.md) → `packages/ui/styles.css` | PRD 부록 A (**폐기됨**) |
| 분류 축 · 데이터 | `packages/domain/src/types.ts` | PRD 전체 |
| 왜 이렇게 정했나 | [`docs/decisions/`](docs/decisions/) | — |
| 아직 안 정해진 것 | [`docs/prd/GAPS.md`](docs/prd/GAPS.md) | — |

PRD 전문(841줄)을 통째로 읽지 말 것. `docs/prd/README.md`의 표가 장별 offset/limit를 준다.

## 정본이 어디인지

문서와 코드가 다른 말을 하면 **아래가 이긴다.**

| 무엇 | 정본 |
|---|---|
| 원칙 — 다른 모든 것보다 우선 | `docs/spec/SPEC-00_개발원칙.md` |
| 디자인 토큰 · 컴포넌트 | `packages/ui/styles.css` |
| 도메인 규칙 · 불변식 | `docs/spec/SPEC-02_도메인모델.md` |
| 분류 3축 (기주 · 스타일 · 메이킹) | `packages/domain/src/types.ts` <br> *API 연동 후에는 Kotlin + OpenAPI 생성물 (`PRIN-T02`)* |
| 칵테일 데이터 | `packages/domain/src/data.ts` *(→ Postgres 시드로 이관 예정)* |
| 그 외 제품 요구사항 | `docs/prd/MUT_PRD_v1.md` (v1.3) |

어긋난 걸 발견하면 코드를 몰래 맞추지 말고 `GAPS.md`에 올린다.
**PRD 부록 A.1 · A.2 · A.6은 폐기됐다** — 구현 근거로 인용하지 않는다 (ADR-0001).

## 구조

```
apps/web/          Next.js 16 App Router — 화면
packages/domain/   타입 · 데이터 · 검색/파인더 로직 (프레임워크 비의존)
packages/ui/       매거진판 토큰 + 화면 레이아웃 CSS
docs/              prd · design · decisions
```

`packages/domain`에 React나 Next를 들이지 않는다. 어드민과 배치 작업이 같은 분류 로직을 쓴다.

## 확인

```bash
npm run verify     # check → lint → build
npm run check      # 코퍼스 불변식만 (빠름)
npm run dev        # localhost:3000
```

`npm run check`는 PRD가 하드 제약이라고 한 것들을 강제한다 —
3축 필수(`R-C-1`), `stylePrimary ∈ styles`(`R-C-3`), 향 태그 1~3개(`R-F1.2-1`).
데이터를 건드렸으면 이걸 먼저 돌린다.

## 요구사항 ID를 인용한다

`R-F2.1-2` 같은 ID는 코드 주석에 근거로 남긴다. 구현이 요구사항을 벗어나면
코드가 아니라 PRD를 먼저 고치고 개정 이력에 적는다.
