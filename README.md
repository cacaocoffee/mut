# K-Cocktail Archive

국내 칵테일 · 바 큐레이션 플랫폼. 레시피 DB와 바 큐레이션을 하나의 그래프로 묶는다.

**현재 상태 — 스펙 완료 · 프론트 프로토타입.**
칵테일 24종이 정적 배열에 있고 탐색 · 상세 · 파인더 3개 화면이 동작한다.
백엔드(Kotlin/Spring)와 어드민은 아직 코드가 없다.

```bash
npm install
npm run dev        # http://localhost:3000
npm run verify     # 코퍼스 검증 → lint → build
npm run check      # 코퍼스 불변식만 (빠름)
```

## 구조

```
apps/web/                 Next.js 16 (App Router)
  app/                      / · /cocktails/[id] · /finder
  components/               화면 단위 컴포넌트
packages/domain/          타입 · 데이터 · 검색/파인더 로직 — 프레임워크 비의존
  src/types.ts              분류 3축의 정본
  src/data.ts               칵테일 24종
  src/search.ts             필터 · 패싯 카운트 · 파인더 점수
  src/validate.ts           PRD 하드 제약 강제
packages/ui/              Modernist 디자인 시스템
  styles.css                시안 토큰 + 컴포넌트 클래스 (정본)
  app.css                   화면 레이아웃 · 반응형
docs/                     문서 23개 — 아래 참조
```

## 어디를 봐야 하나

작업 전에 [`CLAUDE.md`](CLAUDE.md)를 본다. 출처가 넷이고 다 읽으면 5,000줄이 넘어서,
하는 일에 해당하는 것만 열도록 라우팅해 둔다. 전체 지도는 [`docs/README.md`](docs/README.md).

| | |
|---|---|
| **무엇이든 시작할 때** | [`docs/spec/SPEC-00_개발원칙.md`](docs/spec/SPEC-00_개발원칙.md) — 다른 모든 문서보다 우선 |
| 제품 요구사항 | [`docs/prd/README.md`](docs/prd/README.md) — 장별 줄 범위 (PRD v1.2) |
| 시스템 스펙 | [`docs/spec/`](docs/spec/) — SPEC-00~08 · 10 |
| 화면 명세 | [`docs/screens/`](docs/screens/) |
| 디자인 | [`docs/design/README.md`](docs/design/README.md) |
| 결정 기록 | [`docs/decisions/`](docs/decisions/) — ADR 4건 |
| 추적 · 게이트 | [`docs/TRACE-00_추적매트릭스.md`](docs/TRACE-00_추적매트릭스.md) |
| 아직 안 정해진 것 | [`docs/prd/GAPS.md`](docs/prd/GAPS.md) — 17건 중 2건 남음 |

## 범위

Phase 1을 둘로 나눴다 ([SPEC-01 §4](docs/spec/SPEC-01_시스템개요_범위.md)).

| | P0 | 내용 |
|---|---|---|
| **Phase 1a** | 56 | 칵테일 · 재료 · 검색 · 어드민 · 로그인 |
| **Phase 1b** | 21 | 바 · 제휴 — **스펙은 완료, 착수 보류** |

1b를 미루는 비용은 명시돼 있다 — **그래프 가설(`PRIN-P01`)이 1b까지 검증되지 않는다.**

## 지금 동작하는 것

- **탐색** (`/cocktails/search`) — 기주 · 스타일 · 메이킹 · 당도 · 도수 · 맛/향 6축 교차 필터.
  각 값 옆에 실시간 결과 개수가 붙고 0건은 비활성 (`FR-SEARCH-002`). 필터 상태는 URL에 실려
  공유된다. 클라이언트 계산이 서버 `/facets` 와 같은지 CI가 대조한다
- **상세** — 발행분 전부 정적 생성. 인분 환산(1~8잔), ml/oz 토글, 대체 재료 안내,
  맛 프로필 레이더, Schema.org `Recipe` (`FR-COCKTAIL-026`)
- **파인더** (`/finder`) — 4문항으로 후보를 좁혀 TOP 3 추천. 도수 구간은 탐색과 같은 정의다
- **통합 검색** (`/search`) — 이름 · 별칭 · 초성으로 칵테일과 재료를 한 번에. 타입별 그룹핑
- **공유 카드** — 모든 공개 화면에 OG 태그, 칵테일마다 이름으로 그린 1200×630 카드
- 과음 경고 하단 고정, 반응형, 라이트 단일 테마, WCAG AA 대비

이미지는 전부 시안의 자리표시자다. 실제 사진 자산이 없다.

## 공유 카드 확인 (배포 전)

`NFR-S-06` 은 **카카오톡 카드 미리보기**를 사람이 확인하라고 한다 — 자동화할 수 없는 항목이라
릴리즈 체크리스트에 있다 (SPEC-04 §9.2 · 이슈 050).

1. [카카오 디벨로퍼스 → 도구 → 디버거](https://developers.kakao.com/tool/debugger/sharing)에
   주소를 넣고 **초기화(캐시 삭제)** 를 누른다. **카카오는 자체 캐시를 들고 있어** 태그를 고쳐도
   갱신하지 않으면 옛 카드가 계속 나온다
2. 카카오톡에서 그 주소를 자기에게 보내 카드가 뜨는지 본다 — 제목 · 설명 · 1200×630 이미지
3. 구글 [리치 결과 테스트](https://search.google.com/test/rich-results)로 `Recipe` 를 확인한다
   (`NFR-S-05`) — 실패는 **경고**지 배포 차단이 아니다

카드 이미지는 `/{경로}/opengraph-image` 로 직접 열어 볼 수 있다. 사진이 아니라 이름으로
그린 것이고, 이유는 [G-36](docs/prd/GAPS.md)에 있다.

## 남은 일

| | 성격 |
|---|---|
| 호스팅 · 이미지 저장소 결정 | **사업/인프라** — 스펙으로 풀리지 않는다 |
| 법률 검토 1회 | **외부** — [ADR-0004](docs/decisions/ADR-0004-age-gate.md)의 전제 확인 포함 |
| Kotlin/Spring 백엔드 착수 | 구현 |
| 어드민 UI 구현 | 구현 — 명세는 [SCREENS-06](docs/screens/SCREENS-06_어드민.md) |
