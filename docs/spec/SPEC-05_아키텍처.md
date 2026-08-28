# SPEC-05 — 아키텍처

| | |
|---|---|
| 버전 | v1.0 |
| 최종 수정 | 2026-08-06 |
| 상위 문서 | [SPEC-00 개발원칙](./SPEC-00_개발원칙.md) · [SPEC-01 범위](./SPEC-01_시스템개요_범위.md) |

## 1. 결정된 스택

| 층 | 선택 | 근거 |
|---|---|---|
| 백엔드 | Kotlin + Spring Boot 3.x | 팀 역량. Web · Data JPA · Security · Batch |
| DB | PostgreSQL 16 | 11장 ERD가 관계형 전제. 재료 역검색이 조인 문제 |
| 프론트 | Next.js 16 (App Router) + TypeScript | SSG/ISR 요구를 그대로 만족 (`PRIN-T04`) |
| 어드민 | Next.js `/admin` 라우트 그룹 | 별도 앱을 만들지 않는다 — 디자인 시스템과 인증을 공유 |
| 배포 | 프론트/백 분리 | 각자의 파이프라인 |

## 2. 저장소 구조

```
apps/
  web/                Next.js — 공개 페이지 + 어드민 UI
  api/                Kotlin + Spring Boot (Gradle)
    src/main/kotlin/kr/mut/
      cocktail/  ingredient/  bar/  partner/
      content/   user/        stock/  search/  admin/
      common/            공통 · 어댑터 · 감사
packages/
  domain/             TS — 프론트 필터·파인더 로직 + 생성 타입
  ui/                 매거진판 디자인 시스템 (ADR-0009)
docs/
```

npm workspaces는 `apps/web`과 `packages/*`만 관리한다.
`apps/api`는 Gradle이 독립적으로 관리하며 같은 저장소에 나란히 둔다.

## 3. 모듈 경계 (모놀리스 내부)

`PRIN-T03`. Spring 단일 배포지만 모듈 경계는 코드로 지킨다.

```
COCKTAIL ──uses──▶ INGREDIENT
    │
    └──referenced by──▶ BAR (시그니처 메뉴)
                         │
BAR ◀──extends── PARTNER │
                         │
CONTENT ──references──▶ BAR · COCKTAIL
USER ──owns──▶ STOCK · Bookmark
USER ──reads──▶ COCKTAIL · CONTENT
SEARCH ──reads──▶ COCKTAIL · BAR · INGREDIENT · CONTENT
ADMIN ──governs──▶ 전부 (발행 상태 · 감사)
```

**규칙**

- 모듈 간 호출은 공개 인터페이스(`XxxFacade`)로만 한다.
- 다른 모듈의 `Repository`나 `@Entity`를 직접 참조하지 않는다.
- 순환 의존을 만들지 않는다. `BAR`가 `COCKTAIL`을 참조하고 `COCKTAIL`이 "이 칵테일을 파는 바"를
  보여줘야 하므로 **양방향으로 보이지만**, 조회는 `SEARCH`나 조회 전용 서비스가 담당해 순환을 끊는다.
- 부수효과(알림 · 집계 · 검증 태스크 생성)는 도메인 이벤트로 발행하고 리스너가 처리한다.

> **개정 (2026-08-13, 이슈 031)** — `USER ──reads──▶ COCKTAIL` 를 추가했다.
>
> `Bookmark`는 이 표가 `USER`에 배정한 것인데(`owns`), 북마크는 본질적으로 **콘텐츠를 가리킨다.**
> 저장할 때 대상이 발행됐는지 확인해야 하고(`FR-USER-004`), 목록을 보여줄 때 무엇을 저장했는지
> 알려줘야 한다 — **가리키는 대상을 못 읽는 북마크는 쓸모가 없다.**
>
> `SEARCH` 경유를 검토했으나 그쪽은 `USER`를 읽지 못한다(표에 없다). 북마크 행을 읽으려면
> 반대 화살표가 필요해져 문제가 옮겨갈 뿐이다.
>
> 순환은 생기지 않는다 — `COCKTAIL`은 `USER`를 참조하지 않는다. 근거는
> [`GAPS.md` G-30](../prd/GAPS.md#g-30). 이슈 023이 같은 함정을 밟았고([G-28](../prd/GAPS.md#g-28)),
> 그때 배운 것이 **"Facade를 거쳐도 모듈 화살표는 그대로"** 다.

> **개정 (2026-08-28)** — `USER ──reads──▶ CONTENT` 를 추가했다.
>
> 2026-08-13 개정과 같은 이유다. 아티클이 DB 로 옮겨오며(ADR-0011) 실재하는 콘텐츠가 됐고,
> 북마크는 칵테일뿐 아니라 아티클도 가리킨다(다형 참조, SPEC-06 §3.5). 저장할 때 발행됐는지
> 확인하고 목록에서 제목을 보여주려면 `USER`가 `CONTENT`의 Facade(`ArticleFacade`)를 읽어야 한다.
>
> **팬아웃 상한(2)은 넘지 않는다.** `USER`가 실제로 읽는 것은 `COCKTAIL` 하나였고(내 술장이
> Phase 2 로 회귀해 `STOCK`은 코드에 없다), 여기에 `CONTENT`를 더해 둘이다 — 상한과 같다.
> `SEARCH`처럼 예외로 둘 필요가 없다. 순환도 없다 — `CONTENT`는 `USER`를 참조하지 않는다.

## 4. 프론트엔드 렌더링 전략

`PRIN-T04`. **경로마다 렌더링 방식이 정해져 있다.** 임의로 바꾸지 않는다.

| 경로 | 방식 | 재생성 | 색인 |
|---|---|---|---|
| `/` 홈 | ISR | 10분 | ✅ |
| `/cocktails/[slug]` | **SSG + ISR** | 발행 시 on-demand | ✅ |
| `/cocktails/base/[slug]` 외 카테고리 2종 | **SSG + ISR** | 발행 시 on-demand | ✅ |
| `/cocktails/search?…` | 클라이언트 필터 | — | ❌ `noindex` |
| `/finder` · `/finder?base=…&step=…` | 셸 정적 + 클라이언트 진행 | — | ✅ **canonical `/finder`** |
| `/bars/[slug]` | **SSG + ISR** | 발행 시 on-demand | ✅ |
| `/bars?…` 목록·지도 | 클라이언트 | — | ❌ `noindex` |
| `/articles` 목록 | SSG + ISR | 발행 시 | ✅ |
| `/articles/[slug]` | SSG + ISR | 발행 시 | ✅ |
| `/curations/[slug]` | SSG + ISR | 발행 시 | ✅ |
| `/my/*` | CSR (인증) | — | ❌ |
| `/partner/*` | CSR (인증) | — | ❌ |
| `/admin/*` | CSR (인증) | — | ❌ |

> 스토리 경로는 원래 `/stories/[slug]` 로 적혀 있었는데, SPEC-07 의 `GET /articles` ·
> SPEC-06 의 `article` 테이블과 이름이 어긋나 `/articles` 로 통일했다
> ([ADR-0010](../decisions/ADR-0010-articles-over-my-bar.md)). 아티클 앞당김(2026-08-20)
> 동안 재생성은 배포 시점뿐이다 — 어드민 발행이 없어서 on-demand 훅이 부를 일이 없다.

**on-demand 재생성** — 어드민에서 발행하면 API가 프론트의 revalidate 훅을 호출한다.
에디터가 발행하고 나서 반영을 기다리지 않아야 한다 (PRD 12장 — 개발자 없이 발행).

**파인더가 `noindex` 가 아닌 이유** — 답이 붙은 주소는 같은 화면의 **다른 상태**이지 다른
문서가 아니다. 탐색 필터(`/cocktails/search?…`)는 결과 목록 자체가 색인 가치를 갖지 않지만,
파인더는 주소와 무관하게 같은 질문 화면이다. 그래서 진입 화면은 색인하고 답이 붙은 주소는
canonical 로 `/finder` 에 합친다 — 조합마다 중복 문서가 생기는 것은 `PRIN-P06` 이 막는다.
답을 쿼리스트링에 싣는 것은 **공유 가능해야 하기 때문**이다 (이슈 041 · `FR-SEARCH-005`).

**필터를 서버로 보내지 않는 이유** — 필터 결과는 색인 대상이 아니고(`PRIN-P06`),
Phase 1 규모(칵테일 100 · 바 100)에서는 전체 목록을 받아 클라이언트에서 거르는 편이
왕복 없이 즉각적이다. 데이터가 커지면 서버 필터로 옮기되 **URL 계약은 유지한다.**

## 5. 패싯 카운트를 어디서 세나

`R-F2.1-2`는 모든 필터 값 옆에 실시간 결과 개수를 요구하고 0건은 비활성 처리하라고 한다.
이건 "초기부터 넣지 않으면 나중에 UI를 다시 짜야 한다"고 PRD가 못박은 항목이다.

| 단계 | 방식 |
|---|---|
| Phase 1 | 목록 응답에 전체 코퍼스가 담기므로 **클라이언트에서 계산**. 현재 `facetCounts()`가 하는 일 |
| 확장 후 | `GET /api/v1/cocktails/facets?…`가 축별 카운트를 반환 |

**UI 계약은 두 단계에서 동일하다.** 값 옆에 숫자, 0이면 비활성. 계산 위치만 바뀐다.

## 6. 검색

`R-F2.1-3`(한/영 별칭) · `R-F2.1-4`(초성 `ㅁㄹㄴ` → 마르가리타)가 요구사항이다.
**DB `LIKE`로는 초성이 안 된다.** [G-13](../prd/GAPS.md#g-13).

Phase 1 방식:

- 칵테일·바·재료에 **검색 색인 컬럼**을 둔다 — 한글명 · 영문명 · 별칭 · **초성 분해 문자열**.
- 저장 시점에 초성을 분해해 컬럼에 넣는다. 조회는 `LIKE` 프리픽스 매칭 + GIN 인덱스.
- 별칭은 `aliases[]`로 관리하고 어드민에서 편집한다 (`올패` 같은 축약형).

Postgres만으로 처리한다. 별도 검색엔진은 코퍼스가 수천 건을 넘을 때 검토한다 —
그 전에 도입하면 운영 부담만 늘어난다.

## 7. 외부 연동

`PRIN-T06`. 전부 인터페이스 뒤에 둔다.

| 어댑터 | 벤더 | 비고 |
|---|---|---|
| `MapProvider` | 카카오맵 또는 네이버 지도 | 구글맵 배제 — 국내 도보 길찾기·POI 정확도 (`R-F3.1-1`) |
| `ReservationLinkResolver` | 캐치테이블 · 테이블링 · 네이버 예약 | 딥링크만. 예약 인프라를 만들지 않는다 |
| `SocialAuthProvider` | 카카오 · 네이버 · 애플 | 이메일 가입은 후순위 |
| `InstagramFeed` | 인스타그램 | 피드 연동 + **폐업 감지 신호** |

## 8. 배치 · 스케줄

Spring Batch. 전부 멱등해야 한다 (`PRIN-T07`).

| 작업 | 주기 | 무엇 |
|---|---|---|
| 바 정보 검증 태스크 생성 | 일 1회 | `hours_verified_at` 90일 경과 시 관리자 태스크 (`R-F3.1-2`) |
| 파트너 통계 집계 | 일 1회 | 조회수 · 저장 · 외부 액션 · **유입 칵테일 랭킹** (`R-F4.3-2`) |
| 인스타 피드 동기화 | 일 1회 | 폐업 감지 신호 |
| 사이트맵 재생성 | 발행 시 + 일 1회 | 색인 대상 경로만 |

## 9. 계측

`PRIN-P01`이 크로스 이동률을 진짜 검증 지표로 규정했는데 v1.0에는 이벤트 정의가 없었다
([G-10](../prd/GAPS.md#g-10)). 최소 이벤트:

| 이벤트 | 필드 |
|---|---|
| `cocktail_view` | `cocktail_id`, `referrer_type` |
| `bar_view` | `bar_id`, `referrer_type` |
| **`cross_nav`** | `from_type`, `from_id`, `to_type`, `to_id` |
| `partner_action` | `bar_id`, `action`(reserve·map·call·instagram) |
| `filter_apply` | `axis`, `value`, `result_count` |
| `stock_match` | `matched_count`, `one_away_count` (Phase 2) |

`cross_nav`가 없으면 제품 가설을 확인할 방법이 없다. **Phase 1에 반드시 들어간다.**
상세는 [SPEC-10 계측·이벤트](./SPEC-10_계측_이벤트.md)에서 정의한다.

## 10. 아직 정하지 않은 것

| 항목 | 막고 있는 것 | 갭 |
|---|---|---|
| 호스팅 · 배포 환경 | CI/CD 설계 | [G-07](../prd/GAPS.md#g-07) |
| 이미지 저장소 · CDN | 사진 자산 전략 | PRD 16장 5번 |
| 캐시 계층 (Redis 도입 여부) | Phase 1에는 불필요 판단. 파트너 통계가 무거워지면 재검토 | — |

성인 인증은 [ADR-0004](../decisions/ADR-0004-age-gate.md)로 해소됐다 — 전면 게이트를 두지 않으므로
`PRIN-T04`와 충돌하지 않는다. 다만 **법률 검토 결과에 따라 되돌아올 수 있는 항목**이며,
그 경우 게이트와 SEO 중 하나를 포기해야 한다.

남은 것 중 **이미지 저장소·CDN 미정이 가장 급하다.** 사진이 콘텐츠 자산의 절반인데
저장 위치가 없으면 에디터가 발행을 시작할 수 없다.
