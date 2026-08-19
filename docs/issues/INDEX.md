# 이슈 지도 — Phase 1a (칵테일 아카이브 단독)

> **이슈 본문과 진행 상태는 GitHub 에 있다** — [Issues](https://github.com/cacaocoffee/k-cocktail-archive/issues) · [웨이브별 진행률](https://github.com/cacaocoffee/k-cocktail-archive/milestones).
> 이 파일은 **지도**다. 웨이브 편성 · 의존 DAG · 트랙 간 결합점처럼 이슈 하나에 담기지 않는 것만 둔다.
> 상태(TODO/진행/완료)를 여기에 적지 않는다 — **두 곳에 적으면 반드시 어긋난다.**
>
> 착수 전 [`CONVENTIONS.md`](CONVENTIONS.md)와 [`DECISIONS.md`](DECISIONS.md)를 읽는다.
>
> **미결은 이슈가 아니라 [`DECISIONS.md`](DECISIONS.md)에 있다.** 171건을 §1 확정(그대로 구현) / §2 대기(`BLOCKED`) / §3 착수 후로 나눠 놨다.
> 이슈 본문의 "보수적으로 X"는 §1의 근거이지 판단 요청이 아니다 — **멈추지 말고 구현한다.**

범위 근거: [`SPEC-01 §4.1`](../spec/SPEC-01_시스템개요_범위.md) — **P0 56건 전수 커버 / 이슈 52개**.

| 도메인 | P0 | 비고 |
|---|---|---|
| `COCKTAIL` | 27 | `FR-COCKTAIL-025`(이 칵테일을 마실 수 있는 바)는 BAR 의존이라 **Phase 1b** |
| `SEARCH` | 9 | |
| `ADMIN` | 8 | |
| `INGREDIENT` | 6 | |
| `USER` | 6 | |

Phase 1b(BAR 14 · PARTNER 7)와 Phase 2 이후는 [`EPICS-1B-PHASE2.md`](EPICS-1B-PHASE2.md).

**RED 테스트 항목 1,388건 / 이슈당 중앙값 28.5.** 40건 초과 0건 — 한 세션 크기를 넘지 않는다.

---

## 이슈 번호 대응

GitHub 이슈 번호는 **`ISSUE-NNN` + 2** 다. `ISSUE-A00` 만 `#1`.

```
ISSUE-A00 → #1        ISSUE-000 → #2        ISSUE-013 → #15       ISSUE-050 → #52
```

이슈를 열 때: `gh issue view <번호> -R cacaocoffee/k-cocktail-archive`

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

## 웨이브 편성

병렬 열은 **동시에 돌릴 수 있는 세션 수**다.

| Wave | 무엇 | 이슈 | 병렬 | 마일스톤 |
|---|---|---|---|---|
| 0 | 스펙 정합 · 스캐폴딩 · 계약 | 6 | 1~2 | [Wave 0](https://github.com/cacaocoffee/k-cocktail-archive/milestone/1) |
| 1 | 인증·권한 | 3 | 2 | [Wave 1](https://github.com/cacaocoffee/k-cocktail-archive/milestone/2) |
| 2 | 도메인 코어 | 5 | 2 | [Wave 2](https://github.com/cacaocoffee/k-cocktail-archive/milestone/3) |
| 3 | 발행 | 5 | 2 | [Wave 3](https://github.com/cacaocoffee/k-cocktail-archive/milestone/4) |
| 4 | 조회 API | 7 | **4** | [Wave 4](https://github.com/cacaocoffee/k-cocktail-archive/milestone/5) |
| 5 | 어드민 API | 5 | 3 | [Wave 5](https://github.com/cacaocoffee/k-cocktail-archive/milestone/6) |
| 6 | USER | 4 | 3 | [Wave 6](https://github.com/cacaocoffee/k-cocktail-archive/milestone/7) |
| 7 | 계측 수집 | 1 | 1 | [Wave 7](https://github.com/cacaocoffee/k-cocktail-archive/milestone/8) |
| 8 | 프론트 전환 · 어드민 UI · 계측 심기 | 16 | **4** | [Wave 8](https://github.com/cacaocoffee/k-cocktail-archive/milestone/9) |

### Wave 0 — 기반 (직렬에 가까움)

| 이슈 | 제목 | layer | 의존 | 근거 |
|---|---|---|---|---|
| [#1](https://github.com/cacaocoffee/k-cocktail-archive/issues/1) A00 | 스펙 정합 — SPEC-06 보강·충돌 해소·`NFR-L-05` 분해 | docs | — | G-18~G-22 |
| [#2](https://github.com/cacaocoffee/k-cocktail-archive/issues/2) 000 | `apps/api` Gradle 스캐폴딩 (Kotlin·Spring Boot 3.x) | infra | A00 | `PRIN-T01` SPEC-05 §2 |
| [#3](https://github.com/cacaocoffee/k-cocktail-archive/issues/3) 001 | 모듈 경계 테스트 | infra | 000 | `PRIN-T03` |
| [#4](https://github.com/cacaocoffee/k-cocktail-archive/issues/4) 002 | Flyway 기반 + 공통 컬럼 규약 + `pg_trgm` | infra | 000 | SPEC-06 §1·§6 |
| [#5](https://github.com/cacaocoffee/k-cocktail-archive/issues/5) 003 | REST 규약 — Problem Details·`violations`·페이징·ETag·멱등 | api | 000 | SPEC-07 §1 |
| [#6](https://github.com/cacaocoffee/k-cocktail-archive/issues/6) 004 | **OpenAPI → TS 타입 생성 파이프라인** | contract | 003 | **`PRIN-T02`** |

> 004가 이 저장소에서 가장 중요한 이슈다. 계약이 깨지면 빌드가 깨져야 한다.

### Wave 1 — 인증·권한

| 이슈 | 제목 | 의존 | FR |
|---|---|---|---|
| [#7](https://github.com/cacaocoffee/k-cocktail-archive/issues/7) 005 | `user`·`user_role` + httpOnly 세션 | 002,003 | FR-USER-001 |
| [#8](https://github.com/cacaocoffee/k-cocktail-archive/issues/8) 006 | 권한 매트릭스 4역할 | 005 | SPEC-08 §2 |
| [#9](https://github.com/cacaocoffee/k-cocktail-archive/issues/9) 007 | CSRF + 레이트 리밋 | 005 | SPEC-08 §4.3·§6 |

### Wave 2 — 도메인 코어 (2세션)

| 이슈 | 제목 | 의존 | FR |
|---|---|---|---|
| [#10](https://github.com/cacaocoffee/k-cocktail-archive/issues/10) 008 | `ingredient` 마스터 + 불변식 | 002 | FR-INGREDIENT-001·003·004·006 |
| [#11](https://github.com/cacaocoffee/k-cocktail-archive/issues/11) 009 | `cocktail` + **3축 불변식** | 002 | FR-COCKTAIL-001·002·008 |
| [#12](https://github.com/cacaocoffee/k-cocktail-archive/issues/12) 010 | `recipe`·`recipe_ingredient`·`recipe_step` | 008,009 | FR-COCKTAIL-003·004·005 |
| [#13](https://github.com/cacaocoffee/k-cocktail-archive/issues/13) 011 | 도수 자동 계산 + 오버라이드 | 010 | FR-COCKTAIL-006 |
| [#14](https://github.com/cacaocoffee/k-cocktail-archive/issues/14) 012 | 당도·별칭 | 009 | FR-COCKTAIL-007·009 |

### Wave 3 — 발행

| 이슈 | 제목 | 의존 | FR |
|---|---|---|---|
| [#15](https://github.com/cacaocoffee/k-cocktail-archive/issues/15) 013 | **발행 게이트 6종** | 010,011,012 | FR-COCKTAIL-010~013 |
| [#16](https://github.com/cacaocoffee/k-cocktail-archive/issues/16) 014 | slug 불변 + 상태 전이 + 감사 로그 | 013 | FR-COCKTAIL-014·015 |
| [#17](https://github.com/cacaocoffee/k-cocktail-archive/issues/17) 015 | 재생성 훅 (API → 프론트) | 014 | FR-COCKTAIL-016, FR-ADMIN-008 |
| [#18](https://github.com/cacaocoffee/k-cocktail-archive/issues/18) 016 | 불변식 배치 검증 (`npm run check` 서버판) | 013 | NFR-D-01·D-02 |
| [#19](https://github.com/cacaocoffee/k-cocktail-archive/issues/19) 017 | `search_document` 동기화 + 초성 분해 | 014 | FR-SEARCH-006·007 |

### Wave 4 — 조회 API (4세션 병렬)

| 이슈 | 제목 | 의존 | FR |
|---|---|---|---|
| [#20](https://github.com/cacaocoffee/k-cocktail-archive/issues/20) 018 | `GET /cocktails` 목록·필터 | 013 | FR-SEARCH-001·003·005 |
| [#21](https://github.com/cacaocoffee/k-cocktail-archive/issues/21) 019 | **`GET /cocktails/facets` 패싯 카운트** | 018 | FR-SEARCH-002·009 |
| [#22](https://github.com/cacaocoffee/k-cocktail-archive/issues/22) 020 | `GET /cocktails/{slug}` 상세 | 013 | FR-COCKTAIL-017 |
| [#23](https://github.com/cacaocoffee/k-cocktail-archive/issues/23) 021 | `GET /cocktails/{slug}/related` 배리에이션 | 020 | FR-COCKTAIL-024 |
| [#24](https://github.com/cacaocoffee/k-cocktail-archive/issues/24) 022 | `GET /categories` 3축 카테고리 | 013 | FR-COCKTAIL-029·030 |
| [#25](https://github.com/cacaocoffee/k-cocktail-archive/issues/25) 023 | `GET /ingredients` 재료 사전 | 008 | FR-INGREDIENT-002·005 |
| [#26](https://github.com/cacaocoffee/k-cocktail-archive/issues/26) 024 | `GET /search` 통합 검색 | 017 | FR-SEARCH-008 |

### Wave 5 — 어드민 API (3세션)

| 이슈 | 제목 | 의존 | FR |
|---|---|---|---|
| [#27](https://github.com/cacaocoffee/k-cocktail-archive/issues/27) 025 | 어드민 CRUD + `violations` 전부 반환 | 013,006 | FR-ADMIN-001·002·003 |
| [#28](https://github.com/cacaocoffee/k-cocktail-archive/issues/28) 026 | 재료 승인 워크플로 | 008,006 | FR-ADMIN-007, FR-INGREDIENT-001 |
| [#29](https://github.com/cacaocoffee/k-cocktail-archive/issues/29) 027 | **노출 규칙 부재 검증** (`PRIN-P02`) | 025 | FR-ADMIN-006 |
| [#30](https://github.com/cacaocoffee/k-cocktail-archive/issues/30) 028 | 검증 태스크 큐 | 025 | FR-ADMIN-004 |
| [#31](https://github.com/cacaocoffee/k-cocktail-archive/issues/31) 029 | 감사 로그 조회 | 014,006 | FR-ADMIN-005 |

### Wave 6 — USER (3세션)

| 이슈 | 제목 | 의존 | FR |
|---|---|---|---|
| [#32](https://github.com/cacaocoffee/k-cocktail-archive/issues/32) 030 | 소셜 로그인 3종 (OAuth PKCE) | 005,007 | FR-USER-001 |
| [#33](https://github.com/cacaocoffee/k-cocktail-archive/issues/33) 031 | 북마크·컬렉션·공유 링크 | 030 | FR-USER-004 |
| [#34](https://github.com/cacaocoffee/k-cocktail-archive/issues/34) 032 | 광고 고지·과음 경고·미성년 문구 | 008 | FR-USER-002·003 |
| [#35](https://github.com/cacaocoffee/k-cocktail-archive/issues/35) 033 | 위치 미저장 검증 | 005 | FR-USER-006 |

### Wave 7 — 계측

| 이슈 | 제목 | 의존 | 근거 |
|---|---|---|---|
| [#36](https://github.com/cacaocoffee/k-cocktail-archive/issues/36) 034 | `POST /events` + `analytics_event` | 003,007 | SPEC-10 §7 |

> 이벤트를 **심는** 이슈(035)는 화면 뒤라 Wave 8에 있다. 수집 API(034)를 먼저 세우는 것이 SPEC-10 §9의 순서다 — **"받을 곳이 먼저"**.

### Wave 8 — 프론트 전환 (4세션 병렬)

> **API-First** (`PRIN-T04`·`PRIN-T02`): 각 화면 이슈는 의존 API 이슈가 닫힌 뒤 착수한다.

| 이슈 | 제목 | 의존 | 근거 |
|---|---|---|---|
| [#38](https://github.com/cacaocoffee/k-cocktail-archive/issues/38) 036 | **시드 이관** — `data.ts` 24종 → Postgres | 013 | SPEC-06 §6 |
| [#39](https://github.com/cacaocoffee/k-cocktail-archive/issues/39) 037 | **`types.ts` → OpenAPI 생성물 교체** | 004,036 | **`PRIN-T02`** |
| [#40](https://github.com/cacaocoffee/k-cocktail-archive/issues/40) 038 | 칵테일 상세 SSG+ISR 연동 | 020,037 | `NFR-S-01` |
| [#41](https://github.com/cacaocoffee/k-cocktail-archive/issues/41) 039 | 카테고리 페이지 SSG (**축 조합 0개**) | 022,037 | `NFR-S-02·S-03` |
| [#42](https://github.com/cacaocoffee/k-cocktail-archive/issues/42) 040 | 탐색·필터 화면 + 패싯 연동 | 019,037 | FR-SEARCH-002 |
| [#43](https://github.com/cacaocoffee/k-cocktail-archive/issues/43) 041 | 파인더 화면 (도수 구간 공유) | 040 | FR-SEARCH-004 |
| [#44](https://github.com/cacaocoffee/k-cocktail-archive/issues/44) 042 | 통합 검색 화면 | 024,037 | FR-SEARCH-008 |
| [#45](https://github.com/cacaocoffee/k-cocktail-archive/issues/45) 043 | 상세 인터랙션 — 잔 수·단위·대체재 | 038 | FR-COCKTAIL-019·020·021 |
| [#46](https://github.com/cacaocoffee/k-cocktail-archive/issues/46) 044 | Schema.org `Recipe` + OG 태그 | 038 | FR-COCKTAIL-026, FR-USER-005 |
| [#47](https://github.com/cacaocoffee/k-cocktail-archive/issues/47) 045 | 어드민 셸 — 접근 제어·**노출 규칙 부재** | 025,037 | FR-ADMIN-001·006, `PRIN-P02` |
| [#49](https://github.com/cacaocoffee/k-cocktail-archive/issues/49) 047 | 어드민 — 칵테일 편집·**발행 조건 패널** | 045 | FR-ADMIN-002·003, **`NFR-O-01`** |
| [#50](https://github.com/cacaocoffee/k-cocktail-archive/issues/50) 048 | 어드민 — 승인·태스크·감사 화면 | 045,026,028,029 | FR-ADMIN-004·005·007 |
| [#37](https://github.com/cacaocoffee/k-cocktail-archive/issues/37) 035 | 계측 기반 + `cocktail_view`·`search_miss` | 034,038,042 | SPEC-10 §9 **2단계** |
| [#51](https://github.com/cacaocoffee/k-cocktail-archive/issues/51) 049 | 계측 3~4단계 이벤트 5종 | 035,040,041,043 | SPEC-10 §9 **3~4단계** |
| [#48](https://github.com/cacaocoffee/k-cocktail-archive/issues/48) 046 | **CI 자동 게이트** — Lighthouse·axe·사이트맵 | 038,039,040,042,047 | SPEC-04 §9.1 |
| [#52](https://github.com/cacaocoffee/k-cocktail-archive/issues/52) 050 | 수동 체크리스트 + **`.btn-primary` 결정** | 046,048,049 | SPEC-04 §9.2·§9.3 · **G-16** |

> **Wave 8 분할** — 한 세션 크기를 넘던 셋을 쪼갰다.
> `045`(RED 44) → **045 셸 / 047 편집 / 048 관리화면**
> `035`(RED 42) → **035 기반+2단계 / 049 3~4단계** (SPEC-10 §9의 순서 그대로)
> `046`(RED 40) → **046 CI 자동 / 050 수동+결정**

---

## 의존 DAG (요약)

`ISSUE-NNN` 기준이다. GitHub 번호는 +2.

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
| **에디터** | **사용자 본인 + 주변인** | G-17의 "에디터 채용 형태"가 부분 해소. `tasting_note` 작성 담당 확보 — [`ISSUE-036`](https://github.com/cacaocoffee/k-cocktail-archive/issues/38)의 최대 리스크가 풀렸다 |
| **인증·권한 범위** | **`partner_owner`·바 관련 6행을 1b로** | [`ISSUE-006`](https://github.com/cacaocoffee/k-cocktail-archive/issues/8) 80조합 → 약 30조합, IDOR 방어 이월. `member`는 남는다 (`FR-USER-001`·`004`가 1a P0) |
| **법률 검토 시점** | **개발 착수를 막지 않는다. 오픈 전 1회** | `NFR-L-05`는 **정식 오픈 차단이지 개발 차단이 아니다** (SPEC-04 §9.3) |
| **저장소** | **`cacaocoffee/k-cocktail-archive` (private)** | 이슈·진행률·CI 가 GitHub 에 있다. 마크다운 이슈 파일은 이관 후 제거 |

### 법률 검토 — 1a는 규제 접점 없이 완주한다

[ADR-0004](../decisions/ADR-0004-age-gate.md)가 지목한 유일한 접점은 **주류 광고 규제**이고, 그 대상은 **브랜디드 콘텐츠**다.

| 접점 | Phase 1a 실태 |
|---|---|
| `article.is_sponsored` | CONTENT는 **Phase 2** |
| `signature` 등급 브랜디드 콘텐츠 | PARTNER는 **1b** |
| `ingredient_brand.is_sponsored` | 컬럼은 1a에 있으나 — **프로토타입 `data.ts`에 브랜드 정보가 없어 시드가 0건**, 기본값 `false` |

→ **`is_sponsored`를 켜지 않으면 1a 내내 접점이 0이다.** [`ISSUE-008`](https://github.com/cacaocoffee/k-cocktail-archive/issues/10)·[`ISSUE-023`](https://github.com/cacaocoffee/k-cocktail-archive/issues/25) DoD에 "1a에서 켜지 않는다"를 박아 뒀다.

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
| **G-16 잔여** | ✅ 해결 | `.btn-primary` 대비 3.76:1 → **흰 글자를 얹는 면만 `accent-700`**(6.41:1). 이슈 050 이 결정하고 [ADR-0006](../decisions/ADR-0006-btn-primary-contrast.md) 에 남겼다 | — |
| G-09 | 진행 | SCREENS 02~06 미작성 | **Phase 1b** (바 도메인). 1a는 SCREENS-00·01·06으로 충분 |
| G-17 | 미결 | 사업 결정 10건 | Phase 1a 범위 밖 |

**BLOCKED 이슈는 `blocked` 라벨을 붙이고 본문에 G-번호를 명시한다.** 새 갭을 발견하면 `GAPS.md`에 추가한다 (SPEC-00 §4).
