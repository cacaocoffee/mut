# 이슈 보드 — Phase 1a (칵테일 아카이브 단독)

> 착수 전 [`CONVENTIONS.md`](CONVENTIONS.md)와 [`DECISIONS.md`](DECISIONS.md)를 읽는다.
>
> **미결은 이슈가 아니라 [`DECISIONS.md`](DECISIONS.md)에 있다.** 171건을 §1 확정(그대로 구현) / §2 대기(`BLOCKED`) / §3 착수 후로 나눠 놨다.
> 이슈 본문의 "보수적으로 X"는 §1의 근거이지 판단 요청이 아니다 — **멈추지 말고 구현한다.**
> **이슈를 집으면 이 파일의 status를 먼저 바꾸고 커밋한다** — 중복 착수 방지.

범위 근거: [`SPEC-01 §4.1`](../spec/SPEC-01_시스템개요_범위.md) — **P0 56건 전수 커버 / 이슈 52개**.

| 도메인 | P0 | 비고 |
|---|---|---|
| `COCKTAIL` | 27 | `FR-COCKTAIL-025`(이 칵테일을 마실 수 있는 바)는 BAR 의존이라 **Phase 1b** |
| `SEARCH` | 9 | |
| `ADMIN` | 8 | |
| `INGREDIENT` | 6 | |
| `USER` | 6 | |

Phase 1b(BAR 14 · PARTNER 7)와 Phase 2 이후는 [`EPICS-1B-PHASE2.md`](EPICS-1B-PHASE2.md).

---

## 이 프로젝트는 그린필드가 아니다

`apps/web`에 동작하는 프로토타입이 있다 (SPEC-01 §6). **지우고 새로 쓰지 않는다.**
`apps/api`(Kotlin)를 새로 만들고, 프로토타입을 그 위로 갈아끼운다.

```
지금:  apps/web ──▶ packages/domain (정적 배열 24종)
목표:  apps/web ──▶ /api/v1 ──▶ apps/api ──▶ PostgreSQL
                └─▶ packages/domain (필터 로직 + OpenAPI 생성 타입)
```

---

## 진행 현황

| Wave | 무엇 | 이슈 | 완료 | 병렬 |
|---|---|---|---|---|
| 0 | 스펙 정합 · 스캐폴딩 · 계약 | 6 | **1** (A00) | 1~2 |
| 1 | 인증·권한 | 3 | 0 | 2 |
| 2 | 도메인 코어 | 5 | 0 | 2 |
| 3 | 발행 | 5 | 0 | 2 |
| 4 | 조회 API | 7 | 0 | **4** |
| 5 | 어드민 API | 5 | 0 | 3 |
| 6 | USER | 4 | 0 | 3 |
| 7 | 계측 수집 | 1 | 0 | 1 |
| 8 | 프론트 전환 · 어드민 UI · 계측 심기 | 16 | 0 | **4** |

**RED 테스트 항목 1,388건 / 이슈당 중앙값 28.5.** 40건 초과 0건 — 한 세션 크기를 넘지 않는다.

---

## Wave 0 — 기반 (직렬에 가까움)

| ID | 제목 | layer | status | 의존 | 근거 |
|---|---|---|---|---|---|
| [A00](ISSUE-A00.md) | 스펙 정합 — SPEC-06 보강·충돌 해소·`NFR-L-05` 분해 | docs | **DONE** | — | G-18~G-22 |
| [000](ISSUE-000.md) | `apps/api` Gradle 스캐폴딩 (Kotlin·Spring Boot 3.x) | infra | TODO | **A00** | `PRIN-T01` SPEC-05 §2 |
| [001](ISSUE-001.md) | 모듈 경계 테스트 | infra | TODO | 000 | `PRIN-T03` |
| [002](ISSUE-002.md) | Flyway 기반 + 공통 컬럼 규약 + `pg_trgm` | infra | TODO | 000 | SPEC-06 §1·§6 |
| [003](ISSUE-003.md) | REST 규약 — Problem Details·`violations`·페이징·ETag·멱등 | api | TODO | 000 | SPEC-07 §1 |
| [004](ISSUE-004.md) | **OpenAPI → TS 타입 생성 파이프라인** | contract | TODO | 003 | **`PRIN-T02`** |

> 004가 이 저장소에서 가장 중요한 이슈다. 계약이 깨지면 빌드가 깨져야 한다.

## Wave 1 — 인증·권한

| ID | 제목 | layer | status | 의존 | FR |
|---|---|---|---|---|---|
| [005](ISSUE-005.md) | `user`·`user_role` + httpOnly 세션 | api | TODO | 002,003 | FR-USER-001 |
| [006](ISSUE-006.md) | 권한 매트릭스 4역할 | api | TODO | 005 | SPEC-08 §2 |
| [007](ISSUE-007.md) | CSRF + 레이트 리밋 | api | TODO | 005 | SPEC-08 §4.3·§6 |

## Wave 2 — 도메인 코어 (2세션)

| ID | 제목 | status | 의존 | FR |
|---|---|---|---|---|
| [008](ISSUE-008.md) | `ingredient` 마스터 + 불변식 | TODO | 002 | FR-INGREDIENT-001·003·004·006 |
| [009](ISSUE-009.md) | `cocktail` + **3축 불변식** | TODO | 002 | FR-COCKTAIL-001·002·008 |
| [010](ISSUE-010.md) | `recipe`·`recipe_ingredient`·`recipe_step` | TODO | 008,009 | FR-COCKTAIL-003·004·005 |
| [011](ISSUE-011.md) | 도수 자동 계산 + 오버라이드 | TODO | 010 | FR-COCKTAIL-006 |
| [012](ISSUE-012.md) | 당도·별칭 | TODO | 009 | FR-COCKTAIL-007·009 |

## Wave 3 — 발행

| ID | 제목 | status | 의존 | FR |
|---|---|---|---|---|
| [013](ISSUE-013.md) | **발행 게이트 6종** | TODO | 010,011,012 | FR-COCKTAIL-010~013 |
| [014](ISSUE-014.md) | slug 불변 + 상태 전이 + 감사 로그 | TODO | 013 | FR-COCKTAIL-014·015 |
| [015](ISSUE-015.md) | 재생성 훅 (API → 프론트) | TODO | 014 | FR-COCKTAIL-016, FR-ADMIN-008 |
| [016](ISSUE-016.md) | 불변식 배치 검증 (`npm run check` 서버판) | TODO | 013 | NFR-D-01·D-02 |
| [017](ISSUE-017.md) | `search_document` 동기화 + 초성 분해 | TODO | 014 | FR-SEARCH-006·007 |

## Wave 4 — 조회 API (4세션 병렬)

| ID | 제목 | status | 의존 | FR |
|---|---|---|---|---|
| [018](ISSUE-018.md) | `GET /cocktails` 목록·필터 | TODO | 013 | FR-SEARCH-001·003·005 |
| [019](ISSUE-019.md) | **`GET /cocktails/facets` 패싯 카운트** | TODO | 018 | FR-SEARCH-002·009 |
| [020](ISSUE-020.md) | `GET /cocktails/{slug}` 상세 | TODO | 013 | FR-COCKTAIL-017 |
| [021](ISSUE-021.md) | `GET /cocktails/{slug}/related` 배리에이션 | TODO | 020 | FR-COCKTAIL-024 |
| [022](ISSUE-022.md) | `GET /categories` 3축 카테고리 | TODO | 013 | FR-COCKTAIL-029·030 |
| [023](ISSUE-023.md) | `GET /ingredients` 재료 사전 | TODO | 008 | FR-INGREDIENT-002·005 |
| [024](ISSUE-024.md) | `GET /search` 통합 검색 | TODO | 017 | FR-SEARCH-008 |

## Wave 5 — 어드민 API (3세션)

| ID | 제목 | status | 의존 | FR |
|---|---|---|---|---|
| [025](ISSUE-025.md) | 어드민 CRUD + `violations` 전부 반환 | TODO | 013,006 | FR-ADMIN-001·002·003 |
| [026](ISSUE-026.md) | 재료 승인 워크플로 | TODO | 008,006 | FR-ADMIN-007, FR-INGREDIENT-001 |
| [027](ISSUE-027.md) | **노출 규칙 부재 검증** (`PRIN-P02`) | TODO | 025 | FR-ADMIN-006 |
| [028](ISSUE-028.md) | 검증 태스크 큐 | TODO | 025 | FR-ADMIN-004 |
| [029](ISSUE-029.md) | 감사 로그 조회 | TODO | 014,006 | FR-ADMIN-005 |

## Wave 6 — USER (3세션)

| ID | 제목 | status | 의존 | FR |
|---|---|---|---|---|
| [030](ISSUE-030.md) | 소셜 로그인 3종 (OAuth PKCE) | TODO | 005,007 | FR-USER-001 |
| [031](ISSUE-031.md) | 북마크·컬렉션·공유 링크 | TODO | 030 | FR-USER-004 |
| [032](ISSUE-032.md) | 광고 고지·과음 경고·미성년 문구 | TODO | 008 | FR-USER-002·003 |
| [033](ISSUE-033.md) | 위치 미저장 검증 | TODO | 005 | FR-USER-006 |

## Wave 7 — 계측

| ID | 제목 | status | 의존 | 근거 |
|---|---|---|---|---|
| [034](ISSUE-034.md) | `POST /events` + `analytics_event` | TODO | 003,007 | SPEC-10 §7 |

> 이벤트를 **심는** 이슈(035)는 화면 뒤라 Wave 8에 있다. 수집 API(034)를 먼저 세우는 것이 SPEC-10 §9의 순서다 — **"받을 곳이 먼저"**.

## Wave 8 — 프론트 전환 (4세션 병렬)

> **API-First** (`PRIN-T04`·`PRIN-T02`): 각 화면 이슈는 의존 API 이슈가 DONE인 뒤 착수한다.

| ID | 제목 | status | 의존 | 근거 |
|---|---|---|---|---|
| [036](ISSUE-036.md) | **시드 이관** — `data.ts` 24종 → Postgres | TODO | 013 | SPEC-06 §6 |
| [037](ISSUE-037.md) | **`types.ts` → OpenAPI 생성물 교체** | TODO | 004,036 | **`PRIN-T02`** |
| [038](ISSUE-038.md) | 칵테일 상세 SSG+ISR 연동 | TODO | 020,037 | `NFR-S-01` |
| [039](ISSUE-039.md) | 카테고리 페이지 SSG (**축 조합 0개**) | TODO | 022,037 | `NFR-S-02·S-03` |
| [040](ISSUE-040.md) | 탐색·필터 화면 + 패싯 연동 | TODO | 019,037 | FR-SEARCH-002 |
| [041](ISSUE-041.md) | 파인더 화면 (도수 구간 공유) | TODO | 040 | FR-SEARCH-004 |
| [042](ISSUE-042.md) | 통합 검색 화면 | TODO | 024,037 | FR-SEARCH-008 |
| [043](ISSUE-043.md) | 상세 인터랙션 — 잔 수·단위·대체재 | TODO | 038 | FR-COCKTAIL-019·020·021 |
| [044](ISSUE-044.md) | Schema.org `Recipe` + OG 태그 | TODO | 038 | FR-COCKTAIL-026, FR-USER-005 |
| [045](ISSUE-045.md) | 어드민 셸 — 접근 제어·**노출 규칙 부재** | TODO | 025,037 | FR-ADMIN-001·006, `PRIN-P02` |
| [047](ISSUE-047.md) | 어드민 — 칵테일 편집·**발행 조건 패널** | TODO | 045 | FR-ADMIN-002·003, **`NFR-O-01`** |
| [048](ISSUE-048.md) | 어드민 — 승인·태스크·감사 화면 | TODO | 045,026,028,029 | FR-ADMIN-004·005·007 |
| [035](ISSUE-035.md) | 계측 기반 + `cocktail_view`·`search_miss` | TODO | 034,038,042 | SPEC-10 §9 **2단계** |
| [049](ISSUE-049.md) | 계측 3~4단계 이벤트 5종 | TODO | 035,040,041,043 | SPEC-10 §9 **3~4단계** |
| [046](ISSUE-046.md) | **CI 자동 게이트** — Lighthouse·axe·사이트맵 | TODO | 038,039,040,042,047 | SPEC-04 §9.1 |
| [050](ISSUE-050.md) | 수동 체크리스트 + **`.btn-primary` 결정** | **BLOCKED** | 046,048,049 | SPEC-04 §9.2·§9.3 · **G-16** |

> **Wave 8 분할** — 한 세션 크기를 넘던 셋을 쪼갰다.
> `045`(RED 44) → **045 셸 / 047 편집 / 048 관리화면**
> `035`(RED 42) → **035 기반+2단계 / 049 3~4단계** (SPEC-10 §9의 순서 그대로)
> `046`(RED 40) → **046 CI 자동 / 050 수동+결정**

---

## 의존 DAG (요약)

```
000 → 001
  └─→ 002 ─┬─→ 005 → 006, 007
           │           └─→ 030 → 031
           ├─→ 008 ─┬─→ 010 → 011 ─┐
           └─→ 009 ─┘              ├─→ 013 → 014 → 015
  └─→ 003 → 004        009 → 012 ──┘        └─→ 017 → 024
                                    └─→ 016
                              013 ─┬─→ 018 → 019
                                   ├─→ 020 → 021, 043, 044
                                   ├─→ 022
                                   ├─→ 025 → 027, 028
                                   └─→ 036 → 037 → 038·039·040·042·045
                                   008 → 023, 026, 032
                              003 → 034 → 035
                                          038~045 → 046
```

## 착수 순서 권장

**Wave 0을 한 세션이 통으로** 끝내는 편이 낫다 — 스캐폴딩·계약이 갈라지면 이후 전부가 흔들린다.

Wave 2부터 갈라진다:
- 세션 A: `008 → 023 → 026`  (INGREDIENT 계열)
- 세션 B: `009 → 012 → 010 → 011`  (COCKTAIL 코어)
- 세션 C: `005 → 006 → 007`  (인증·권한)

Wave 4가 최대 병렬 구간(4세션), Wave 8이 그다음(4세션)이다.

## 트랙 간 결합점

| 결합 | 내용 | 처리 |
|---|---|---|
| 013 ← 016 | 발행 게이트와 배치 검증이 **같은 규칙**을 본다 | 013이 `PublishGate` 를 순수 함수로 분리하고 016이 재사용. 두 벌 구현 금지 |
| 019 ← 040 | 패싯 카운트 계산 위치 (서버 vs 클라이언트) | SPEC-05 §5: Phase 1은 **클라이언트 계산**, UI 계약은 동일. 019는 엔드포인트를 만들되 040은 당분간 클라이언트 계산 유지 |
| 037 ← 전 FE | 생성 타입 교체가 모든 화면을 건드린다 | 037을 **먼저** 끝내고 038~045를 연다. 순서를 뒤집으면 두 번 고친다 |
| 015 ← 038 | 재생성 훅의 수신측이 프론트 | 015는 호출까지, 038이 `/api/revalidate` 수신 구현 |
| 035 ← 043 | 이벤트 심는 지점이 화면 | 034(수집 API)를 먼저, 035는 화면 이슈 뒤 |

---

## 확정된 것 (2026-08-07)

| 항목 | 결정 | 영향 |
|---|---|---|
| **에디터** | **사용자 본인 + 주변인** | G-17의 "에디터 채용 형태"가 부분 해소. `tasting_note` 작성 담당 확보 — [`ISSUE-036`](ISSUE-036.md)의 최대 리스크가 풀렸다 |
| **인증·권한 범위** | **`partner_owner`·바 관련 6행을 1b로** | [`ISSUE-006`](ISSUE-006.md) 80조합 → 약 30조합, IDOR 방어 이월. `member`는 남는다 (`FR-USER-001`·`004`가 1a P0) |
| **법률 검토 시점** | **개발 착수를 막지 않는다. 오픈 전 1회** | `NFR-L-05`는 **정식 오픈 차단이지 개발 차단이 아니다** (SPEC-04 §9.3) |

### 법률 검토 — 1a는 규제 접점 없이 완주한다

[ADR-0004](../decisions/ADR-0004-age-gate.md)가 지목한 유일한 접점은 **주류 광고 규제**이고, 그 대상은 **브랜디드 콘텐츠**다.

| 접점 | Phase 1a 실태 |
|---|---|
| `article.is_sponsored` | CONTENT는 **Phase 2** |
| `signature` 등급 브랜디드 콘텐츠 | PARTNER는 **1b** |
| `ingredient_brand.is_sponsored` | 컬럼은 1a에 있으나 — **프로토타입 `data.ts`에 브랜드 정보가 없어 시드가 0건**, 기본값 `false` |

→ **`is_sponsored`를 켜지 않으면 1a 내내 접점이 0이다.** [`ISSUE-008`](ISSUE-008.md)·[`ISSUE-023`](ISSUE-023.md) DoD에 "1a에서 켜지 않는다"를 박아 뒀다.

**검토가 필요해지는 트리거 3개** (날짜가 아니다):

1. `is_sponsored = true` 인 주류 브랜드를 **처음 넣을 때**
2. **정식 오픈** — `NFR-L-05` + `NFR-L-04`(처방침·약관 문안, SPEC-08 §9 미정)
3. **브랜디드 콘텐츠 착수** (Phase 2)

검토 항목은 SPEC-08 §8의 3건 — ①전면 성인 인증 요구 여부 ②브랜디드 콘텐츠 주류광고 기준 ③`audit_log` 보존의 파기 예외 적법성. **③은 실사용자가 붙는 오픈 후에 걸린다** (`ISSUE-014`·`029`·`031`이 이미 그렇게 구현).

**뒤집혔을 때**: 코드 손실은 작다(FE 4개 — 038·039·044·046). **가설 손실이 크다** — SPEC-01 §8.1의 "유기 검색 유입이 실제로 발생"을 검증할 수 없게 된다 (ADR-0004 "되돌리는 조건").

> ⚠️ **`tasting_note`는 개발이 아니라 콘텐츠 작업이다.** `GATE-COCKTAIL-01`이 발행을 막고 `PRIN-P03`이 AI 생성을 금지한다.
> 24종 이관에 24건, 100종 목표면 **100건**이 사람 손이다. **46개 이슈가 끝나도 이게 없으면 발행이 0건**이고 SPEC-01 §8.1의 성공 판정을 못 받는다.

## 미결

[`docs/prd/GAPS.md`](../prd/GAPS.md) — 17건 중 대부분 해결. **Phase 1a에 걸리는 것은 아래 셋.**

| 갭 | 상태 | 무엇 | 막고 있는 이슈 |
|---|---|---|---|
| **G-07 하단** | 미결 | **호스팅 · 이미지 저장소** — 문서로 풀리지 않는 유일한 것 | 000(배포) · 002(`media_asset`) · 005(**쿠키 도메인** — SPEC-07 §1.2가 인증 방식으로 호스팅에 제약을 걸었다) |
| **G-16 잔여** | 해결됨 + 결정 1 | `.btn-primary` 대비 **3.76:1** (AA 미달). `packages/ui`는 ADR-0001상 수정 금지라 제품 결정 필요 (SPEC-04 §2.4의 3안) | 046 |
| G-09 | 진행 | SCREENS 02~06 미작성 | **Phase 1b** (바 도메인). 1a는 SCREENS-00·01·06으로 충분 |
| G-17 | 미결 | 사업 결정 10건 | Phase 1a 범위 밖 |

**BLOCKED 이슈는 여기 G-번호를 명시한다.** 새 갭을 발견하면 `GAPS.md`에 추가한다 (SPEC-00 §4).
