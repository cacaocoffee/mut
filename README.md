<p align="center">
  <img src="apps/web/public/brand/mut-lockup.png" alt="MUT — 당신의 취향, 당신의 멋" width="360">
</p>

# MUT — 당신의 취향, 당신의 멋

국내 칵테일 · 바 큐레이션 플랫폼. 레시피 DB와 바 큐레이션을 하나의 그래프로 묶는다.

**현재 상태 — Phase 1a 구현 완료.**
Kotlin/Spring API · Postgres 시드 41종 · 어드민(편집 · 레시피 · 재료 승인 · 검증 태스크 ·
감사 로그) · 공개 화면 5종이 동작한다. e2e 237건이 CI 에서 돈다.
남은 것은 호스팅 · 이미지 저장소 같은 **사업 결정**이다 ([G-07](docs/prd/GAPS.md)).

```bash
npm install
npm run dev        # http://localhost:3000
npm run verify     # 코퍼스 검증 → lint → build
npm run check      # 코퍼스 불변식만 (빠름)
```

## 구조

```
apps/api/                 Kotlin/Spring — 도메인 · 어드민 API · 배치
  src/main/kotlin/kr/mut/   모듈 경계는 SPEC-05 §3 방향표가 정본
apps/web/                 Next.js 16 (App Router)
  app/                      /cocktails/search · /cocktails/[slug] · /finder · /search · /admin
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
| 결정 기록 | [`docs/decisions/`](docs/decisions/) — ADR 6건 |
| 배포 전 사람 확인 | [`docs/RELEASE-CHECKLIST.md`](docs/RELEASE-CHECKLIST.md) |
| 추적 · 게이트 | [`docs/TRACE-00_추적매트릭스.md`](docs/TRACE-00_추적매트릭스.md) |
| 아직 안 정해진 것 | [`docs/prd/GAPS.md`](docs/prd/GAPS.md) |

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

## 배포

호스팅은 [ADR-0007](docs/decisions/ADR-0007-hosting.md)이 정했다 — Vercel + Fly + Neon + R2 를
**세 단계로 나눠** 올린다. 지금 할 수 있는 것은 1단계다.

| 단계 | 무엇 | 값 | 막는 것 |
|---|---|---|---|
| **1** | 웹만 Vercel | **$0** | 없음 |
| 2 | 도메인 + API(Fly) + DB(Neon) | ~$3 + 도메인 값 | **도메인** |
| 3 | 이미지 R2 | ~$0 | 붙일 사진이 없다 |

### 1단계 — 웹만 올린다

[`apps/web/lib/api.ts`](apps/web/lib/api.ts)가 `MUT_API_URL` 이 비면 API를 부르지 않고
`packages/domain` 의 배열로 빌드한다. 그래서 **백엔드 없이 사이트가 뜬다.**

Vercel 대시보드에서 이 저장소를 가져오고:

| 설정 | 값 |
|---|---|
| Root Directory | **`apps/web`** — 루트 락파일을 보고 npm 워크스페이스를 알아서 설치한다 |
| Framework | Next.js (자동으로 잡힌다) |
| 환경변수 | `MUT_SITE_URL` **하나만** 넣는다 |

`MUT_SITE_URL` 을 빠뜨리면 [`apps/web/lib/site.ts`](apps/web/lib/site.ts)가
`http://localhost:3000` 을 쓴다. 그러면 OG · 사이트맵 · 구조화 데이터가 전부 로컬을 가리켜
**카카오톡 카드가 뜨지 않는다**. 화면은 멀쩡해 보이므로 눈으로는 못 잡는다.

`MUT_API_URL` 은 **넣지 않는다.** 넣는 순간 API 만 쓰는데 2단계 전에는 그게 없다.

변수 목록과 각각을 빠뜨렸을 때 무슨 일이 생기는지는 [`apps/web/.env.example`](apps/web/.env.example)에 있다.

**1단계에서 무엇이 도는가.** 서버를 부르는 것만 못 쓴다.

| | 1단계에서 |
|---|---|
| 탐색 · 파인더 · 내 술장 · 재료 사전 · 상세 | **전부 돈다** |
| 탐색 화면의 검색창 | **돈다.** 색인이 503을 주면 이름 부분일치로 물러난다 — 초성과 별칭만 빠진다 ([`use-name-index.ts`](apps/web/lib/use-name-index.ts)) |
| 자동완성 드롭다운 | 제안이 **안 뜬다** (빈 목록). 치는 것 자체는 막히지 않는다 |
| `/search` 통합 검색 | **빈 화면.** 셸만 있고 질의를 전부 API에 맡긴다. 내비에 없고 `noindex`라 주소를 직접 쳐야 닿는다 |
| 로그인 · 어드민 | 못 쓴다 |

세 줄 모두 조용히 물러난다 — 오류 화면이 뜨지 않는다.

올린 뒤에는 아래 **공유 카드 확인**을 한 번 돌린다 — 그게 1단계의 마지막 관문이다.

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
| 도메인 | **2단계를 막는다.** 쿠키 세션이라 프론트와 API가 같은 상위 도메인이어야 한다 ([ADR-0007](docs/decisions/ADR-0007-hosting.md)) |
| 히어로 사진 | 3단계를 막는다. 저장소는 R2로 정해졌고 붙일 사진이 없다 ([G-07](docs/prd/GAPS.md)) |
| 법률 검토 1회 | **외부** — [ADR-0004](docs/decisions/ADR-0004-age-gate.md)의 전제 확인 포함 |
| 시드 데이터 다듬기 | 에디터 판단 — 계량 표기 · oz 반올림 ([DECISIONS](docs/issues/DECISIONS.md) D-3~D-5) |
| Phase 1b 바 · 제휴 | 스펙 완료, 착수 보류 |
