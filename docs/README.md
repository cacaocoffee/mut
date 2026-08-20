# 문서 맵

출처가 넷이다. **하는 일에 해당하는 것만 연다** — 다 읽으면 4,000줄이 넘는다.

| 층 | 무엇 | 어디 |
|---|---|---|
| **왜 · 무엇** | 제품 요구사항 | [`prd/`](./prd/) |
| **어떻게** | 시스템 스펙 | [`spec/`](./spec/) |
| **화면** | 프론트 화면 명세 | [`screens/`](./screens/) |
| **디자인** | 매거진판 토큰 · 규칙 | [`design/`](./design/) |
| **왜 그렇게 정했나** | ADR | [`decisions/`](./decisions/) |

## 읽기 순서

처음 오는 사람:

1. [`prd/README.md`](./prd/README.md) — 제품이 뭔지, 어느 장을 열지
2. [`spec/SPEC-00_개발원칙.md`](./spec/SPEC-00_개발원칙.md) — **다른 모든 문서보다 우선한다**
3. [`spec/SPEC-01_시스템개요_범위.md`](./spec/SPEC-01_시스템개요_범위.md) — 경계와 범위
4. 자기 작업 영역 문서

## SPEC

| 문서 | 무엇 | 상태 |
|---|---|---|
| [SPEC-00 개발원칙](./spec/SPEC-00_개발원칙.md) | Constitution — `PRIN-P` · `PRIN-T` · `PRIN-D` | ✅ |
| [SPEC-01 시스템개요·범위](./spec/SPEC-01_시스템개요_범위.md) | 도메인 코드 9개 · 경계 · Phase 1 범위 · 비범위 | ✅ |
| [SPEC-02 도메인모델](./spec/SPEC-02_도메인모델.md) | 애그리게이트 · 불변식 · 발행 게이트 · 상태 전이 | ✅ |
| [SPEC-03 기능요구사항](./spec/SPEC-03_기능요구사항.md) | `FR-<도메인>-nnn` — Phase 1 상세, 2·3 스텁 | ✅ |
| [SPEC-04 비기능요구사항](./spec/SPEC-04_비기능요구사항.md) | `NFR-<분류>-nn` · 대비 실측표 · 릴리즈 게이트 | ✅ |
| [SPEC-05 아키텍처](./spec/SPEC-05_아키텍처.md) | 스택 · 모듈 경계 · 렌더링 전략 · 검색 · 배치 | ✅ |
| [SPEC-06 데이터모델 ERD](./spec/SPEC-06_데이터모델_ERD.md) | 테이블 · 제약 · 인덱스 · 마이그레이션 | ✅ |
| [SPEC-07 API명세](./spec/SPEC-07_API명세.md) | `/api/v1/*` REST · 에러 · 인증 · 핵심 5건 상세 | ✅ |
| [SPEC-08 보안·권한·개인정보](./spec/SPEC-08_보안_권한_개인정보.md) | 역할 4종 · 권한 매트릭스 · 스코프 · 개인정보 · 위협 | ✅ |
| SPEC-09 외부연동 | 지도 · 예약 · 소셜 · 인스타 어댑터 | ⬜ |
| [SPEC-10 계측·이벤트](./spec/SPEC-10_계측_이벤트.md) | 이벤트 10종 · 지표 8개 매핑 · 수집 API | ✅ |

## 화면

| 문서 | 무엇 | 상태 |
|---|---|---|
| [SCREENS-00 인덱스·공통규칙](./screens/SCREENS-00_인덱스_공통규칙.md) | 상태 5종 · 반응형 · 접근성 · 카피 · 법적 표기 | ✅ |
| [SCREENS-01 칵테일](./screens/SCREENS-01_칵테일.md) | 탐색 · 상세 · 카테고리 · 재료 사전 | ✅ |
| SCREENS-02 바 탐색·상세 | | ⬜ |
| [SCREENS-03 파인더·내 술장](./screens/SCREENS-03_파인더.md) | 4문항 추천 · 보유 재료 역검색 | ✅ |
| SCREENS-04 마이·저장 | | ⬜ |
| SCREENS-05 파트너 대시보드 | | ⬜ |
| [SCREENS-06 어드민](./screens/SCREENS-06_어드민.md) | 할 일 · 편집 · 재료 마스터 · 감사 로그 | ✅ |

## 릴리즈

| 문서 | 무엇 |
|---|---|
| [RELEASE-CHECKLIST](./RELEASE-CHECKLIST.md) | 배포 전에 **사람이** 확인하는 것 (SPEC-04 §9.2·§9.3). 자동 검사는 CI 에 있다 |

## 추적

| 문서 | 무엇 | 상태 |
|---|---|---|
| [TRACE-00 추적매트릭스](./TRACE-00_추적매트릭스.md) | `R-*` ↔ `FR-*` ↔ ERD ↔ API ↔ 화면 · 게이트 G0~G7 | ✅ |
| QA-00 검수체계 | 게이트 · 검수 로그 | ⬜ |

## 결정

| ADR | 무엇 |
|---|---|
| [ADR-0001](./decisions/ADR-0001-design-system.md) | 디자인 시스템은 Modernist. PRD 부록 A.1·A.2·A.6 폐기, 라이트 단일 테마 |
| [ADR-0002](./decisions/ADR-0002-taxonomy.md) | 분류 체계 — 당도 4단계 · 향 태그 10개 · 스타일 축 신설 · 슬러그 정리 |
| [ADR-0003](./decisions/ADR-0003-graph-source-and-abv-bands.md) | 시그니처는 에디터·메뉴판은 파트너 / 도수 필터 4구간 통일 |
| [ADR-0004](./decisions/ADR-0004-age-gate.md) | 전면 성인 인증 게이트를 두지 않는다 (법률 검토 대기 조건부) |
| [ADR-0005](./decisions/ADR-0005-ui-package-scope.md) | `app.css` 는 우리 것, `styles.css` 는 시안 것 — 정본 개정 절차 |
| [ADR-0006](./decisions/ADR-0006-btn-primary-contrast.md) | 흰 글자를 얹는 면은 `accent-700` — `.btn-primary` 대비 (G-16) |
| [ADR-0007](./decisions/ADR-0007-hosting.md) | 호스팅은 Vercel · Neon · R2. 웹부터 혼자 $0 으로 올린다 (G-07 하단) |
| [ADR-0008](./decisions/ADR-0008-color-photos.md) | 콘텐츠 사진은 컬러 — 시안의 `.grayscale` 규칙을 뗀다 (G-46) |
| [ADR-0009](./decisions/ADR-0009-design-magazine.md) | 디자인 시스템을 **매거진판**으로 교체 — 아이보리 + 와인 + 세리프 제목 (ADR-0001 대체) |

## 미결

[`prd/GAPS.md`](./prd/GAPS.md) — 17건 중 **15건 해결, 2건 남음.**

남은 둘 중 호스팅은 [ADR-0007](./decisions/ADR-0007-hosting.md)이 정했고, **이미지 저장소를
실제로 붙이는 것과 사업 결정(G-17)이 남는다.** 이미지는 결정이 없어서가 아니라
붙일 사진이 아직 없어서 남아 있다.
