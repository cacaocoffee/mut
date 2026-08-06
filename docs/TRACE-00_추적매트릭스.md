# TRACE-00 — 추적 매트릭스

| | |
|---|---|
| 버전 | v1.0 |
| 최종 수정 | 2026-08-06 |
| 대상 | PRD v1.2 · SPEC-00~08 |

요구사항이 **구현 경로를 갖는지** 확인하는 문서다.
`R-*`(PRD) → `FR-*`(SPEC-03) → ERD 테이블 → API 엔드포인트 → 화면.

**끊긴 지점이 곧 누락이다.** 이 문서의 목적은 그걸 드러내는 것이지 채워 넣는 게 아니다.

---

## 1. 요약

| 지표 | 값 |
|---|---|
| PRD 정의 요구사항 (`R-*`) | 55건 |
| FR로 전개됨 | **54건 (98%)** |
| FR 총계 | 108건 (P0 77 · P1 4 · P2/P3 27) |
| **P0 Phase 1a** | **56건** — COCKTAIL 27 · SEARCH 9 · ADMIN 8 · INGREDIENT 6 · USER 6 |
| **P0 Phase 1b** | **21건** — BAR 14 · PARTNER 7 (스펙 작성 완료, 착수 보류) |
| P0 중 ERD·API 경로 확보 | 60건 |
| P0 중 **화면 명세 대기** | 17건 → **6건** (SCREENS-00·01로 11건 해소) |
| 불변식(`INV-`/`GATE-`) 배치 | **25 / 25** |

### 1.1 FR이 없는 요구사항 — 1건

| `R-*` | 왜 없나 |
|---|---|
| `R-F5-4` 성인 인증 | **의도적 폐기.** [ADR-0004](./decisions/ADR-0004-age-gate.md)로 요구사항 자체를 내렸다. 누락이 아니다 |

---

## 2. 이 매트릭스를 만들면서 발견한 것

### 2.1 하류 문서가 FR ID를 인용하지 않는다

SPEC-05~08이 근거를 인용할 때 **`FR-*`이 아니라 PRD의 `R-*`을 직접** 쓰고 있었다.
내용은 반영돼 있는데 ID 체인이 끊긴다.

예 — `FR-SEARCH-002`(패싯 카운트)는 [SPEC-07 §3.2](./spec/SPEC-07_API명세.md)에
엔드포인트와 계산 방식까지 상세히 있지만, 그 문단은 `R-F2.1-2`만 인용한다.
자동 추적으로는 "FR-SEARCH-002 미구현"으로 보인다.

> **규칙 신설** — 하류 문서(SPEC-05 이후)는 근거를 인용할 때 **`FR-*`을 먼저 쓰고**
> 필요하면 `R-*`을 병기한다. `FR-*`이 공통 축이고 `R-*`은 그 출처다.
>
> 기존 문서를 일괄 수정하지는 않는다 — 이 매트릭스가 그 역할을 대신한다.

### 2.2 근거 `R-*`이 없는 FR이 38건

PRD 본문(6.1 상세 페이지 구성표, 8.1 바 상세표 등)에서 나왔지만 **번호가 붙지 않은 요구사항**들이다.
잔 수 환산 · 단위 토글 · 히어로 블록 구성 같은 것.

누락은 아니지만 **PRD를 고칠 때 무엇이 영향받는지 알 수 없다.** 3절 매트릭스에 출처를 절 번호로 기록했다.

### 2.3 스키마 누락 2건 (해결됨)

[SPEC-08](./spec/SPEC-08_보안_권한_개인정보.md)을 쓰다 발견해 [SPEC-06](./spec/SPEC-06_데이터모델_ERD.md)에 보강했다.

| 누락 | 없으면 |
|---|---|
| `bar_owner` | `partner_owner`가 "자기 바"를 판정할 수 없다 |
| `user_role` | 한 사람이 에디터이면서 관리자일 수 없다 |

---

## 3. 매트릭스 — P0

`—` 해당 없음 · `▲` 대기

### 3.1 COCKTAIL

| FR | 근거 | ERD | API | 화면 |
|---|---|---|---|---|
| 001 3축 필수 | `R-C-1` | `cocktail` NOT NULL | `POST /admin/cocktails` | ▲ 06 |
| 002 `style_primary ∈ styles` | `R-C-3` | 복합 FK | 〃 | ▲ 06 |
| 003 레시피 다중 버전 | `R-F1.1-7` | `recipe` 부분 유니크 | `PUT …/recipes/{rid}` | ▲ 06 |
| 004 재료 마스터 참조 | `R-F1.1-1` | `recipe_ingredient` FK | 〃 | ▲ 06 |
| 005 재료 속성 | PRD 11장 | `recipe_ingredient` | 〃 | ▲ 06 |
| 006 도수 자동 계산 | `R-F1.1-4` | `abv` 생성 컬럼 | 〃 | ▲ 06 |
| 007 당도 수동 4단계 | `R-F1.1-5` | `cocktail.sweetness` | 〃 | ▲ 06 |
| 008 향 태그 1~3 | `R-F1.2-1` | `cocktail_aroma_tag` | 〃 | ▲ 06 |
| 009 별칭 | `R-F2.1-3` | `aliases[]` · `search_document` | 〃 | ▲ 06 |
| **010 발행 게이트 6종** | `GATE-COCKTAIL-01~06` | 앱 강제 | **`POST …/publish`** ([§3.4](./spec/SPEC-07_API명세.md)) | ▲ 06 |
| 011 향과 맛 발행 필수 | `R-F1.1-2` | `tasting_note` | 〃 | ▲ 06 |
| 012 클래식 story 필수 | `R-F1.1-3` | `is_classic` | 〃 | ▲ 06 |
| 013 미유통 대체재 | `R-F1.3-2` | `substitute_note` | 〃 | ▲ 06 |
| 014 slug 불변 | `PRIN-D2` | UNIQUE | 〃 | ▲ 06 |
| 015 발행 감사 로그 | `PRIN-T8` | `audit_log` | 〃 | ▲ 06 |
| 016 on-demand 재생성 | `PRIN-T4` | — | [§4 재생성 훅](./spec/SPEC-07_API명세.md) | — |
| 017 상세 필수 블록 | PRD 6.1 | `cocktail` | `GET /cocktails/{slug}` | ▲ 01 |
| 018 3축 카테고리 링크 | `R-C-2` | — | `GET /categories` | ▲ 01 |
| 019 잔 수 환산 | PRD 6.1 | `recipe.serving_count` | — (클라이언트) | ▲ 01 |
| 020 ml/oz 토글 | PRD 6.1 | `recipe_ingredient.unit` | — (클라이언트) | ▲ 01 |
| 021 대체재 펼침 | `R-F1.3-2` | `substitute_note` | `GET /cocktails/{slug}` | ▲ 01 |
| **024 배리에이션 추천** | `R-C-3` | `style_primary` 인덱스 | `GET …/related` | ▲ 01 |
| **025 마실 수 있는 바** ★ | `PRIN-P1` | `bar_menu_item(cocktail_id)` | **`GET …/bars`** ([§3.3](./spec/SPEC-07_API명세.md)) | ▲ 01 |
| 026 Schema.org Recipe | `R-F1.1-6` | — | `GET /cocktails/{slug}` | ▲ 01 |
| 027 저장 · 공유 | PRD 6.1 | `bookmark` | `POST /me/bookmarks` | ▲ 04 |
| 028 과음 경고 | `R-F1.1-8` | — | — | ▲ 00 |
| 029 단일 축 카테고리 경로 | `R-C-2` | `cocktail(status,base_spirit)` 등 | `GET /categories` | ▲ 01 |
| 030 축 조합 경로 금지 | `R-C-2` | — | — | ▲ 00 |

### 3.2 INGREDIENT

| FR | 근거 | ERD | API | 화면 |
|---|---|---|---|---|
| 001 마스터 상한 · 승인제 | PRD 7.2 | `ingredient.is_approved` | `POST /admin/ingredients/{id}/approve` | ▲ 06 |
| 002 재료 상세 | `R-F1.3-1` | `ingredient` | `GET /ingredients/{slug}` | ▲ 01 |
| 003 미유통 대체재 필수 | `R-F1.3-2` | `substitute_note` | 〃 | ▲ 06 |
| 004 브랜드 광고성 구분 | `R-F1.3-3` | `ingredient_brand.is_sponsored` | 〃 | ▲ 01 |
| 005 별칭 | `R-F2.1-3` | `search_document` | `GET /search` | — |
| 006 카테고리 · `counts_for_stock` | `R-F2.2-5` | `recipe_ingredient` | — | ▲ 06 |

### 3.3 BAR  *(Phase 1b — 착수 보류)*

| FR | 근거 | ERD | API | 화면 |
|---|---|---|---|---|
| 001 미제휴도 등재 | `R-F3.1-5` | `bar` (계약 없이 존재) | `POST /admin/bars` | ▲ 02 |
| 002 기본 정보 | PRD 8.1 | `bar` | 〃 | ▲ 02 |
| 003 최종 확인일 노출 | `R-F3.1-2` | `hours_verified_at` | `GET /bars/{slug}` | ▲ 02 |
| 004 90일 검증 태스크 | `R-F3.1-2` | 인덱스 + 배치 | `GET /admin/tasks` | ▲ 06 |
| 005 예약 유형 · 딥링크 | `R-F3.1-3` | `reservation_type` | `GET /bars/{slug}` | ▲ 02 |
| 006 태그 | PRD 8.1 | `bar_style_tag` · `bar_mood_tag` | 〃 | ▲ 02 |
| 007 에디터 노트 | PRD 8.1 | `editor_note` | `PATCH /admin/bars/{id}` | ▲ 06 |
| 008 별점 금지 | `R-F3.1-4` | **컬럼 부재로 성립** | — | ▲ 02 |
| 009 카카오/네이버 지도 | `R-F3.1-1` | `lat` `lng` | — (어댑터) | ▲ 02 |
| 010 폐업은 상태 전이 | `PRIN-D5` | `REVOKE DELETE` | — | ▲ 06 |
| **012 시그니처는 에디터** | ADR-0003 | `bar_menu_item.source` CHECK | `PUT /admin/bars/{id}/menu` | ▲ 06 |
| **014 양방향 링크** ★ | `PRIN-P1` | `bar_menu_item.cocktail_id` | `GET /bars/{slug}/cocktails` | ▲ 02 |
| 017 상권 단위 필터 | `R-F3.2-1` | `bar(district,status)` | `GET /bars` | ▲ 02 |
| 018 지도/리스트 토글 | `R-F3.2-2` | — | 〃 | ▲ 02 |
| 019 정렬 기본 = 큐레이션 | `R-F3.2-3` | — | 〃 | ▲ 02 |

### 3.4 SEARCH

| FR | 근거 | ERD | API | 화면 |
|---|---|---|---|---|
| 001 필터 축 6종 | PRD 7.1 | 축별 인덱스 | `GET /cocktails` ([§3.1](./spec/SPEC-07_API명세.md)) | ▲ 01 |
| **002 패싯 카운트** | `R-F2.1-2` | 조인 테이블 `GROUP BY` | **`GET /cocktails/facets`** ([§3.2](./spec/SPEC-07_API명세.md)) | ▲ 01 |
| 003 도수 4구간 | ADR-0003 | `cocktail(status,abv)` | `GET /cocktails?abv=` | ▲ 01 |
| 004 파인더도 같은 구간 | ADR-0003 | 〃 | — (클라이언트) | ▲ 03 |
| 005 쿼리스트링 · noindex | `R-F2.1-1` | — | `X-Robots-Tag` | ▲ 01 |
| 006 한/영 별칭 | `R-F2.1-3` | `search_document.aliases` GIN | `GET /search` | ▲ 01 |
| 007 초성 검색 | `R-F2.1-4` | `chosung` + `pg_trgm` | `GET /search/suggest` | ▲ 01 |
| 008 타입별 그룹핑 | `R-F5-1` | `search_document.entity_type` | `GET /search` | ▲ 01 |
| 009 0건 즉시 비활성 | `R-F2.1-2` | — | `GET /cocktails/facets` | ▲ 01 |

### 3.5 USER

| FR | 근거 | ERD | API | 화면 |
|---|---|---|---|---|
| 001 소셜 로그인 | `R-F5-3` | `user(provider,provider_uid)` | `GET /auth/{provider}/*` ([SPEC-08 §4.2](./spec/SPEC-08_보안_권한_개인정보.md)) | ▲ 00 |
| 002 브랜디드 콘텐츠 고지 | ADR-0004 | `article.is_sponsored` | — | ▲ 00 |
| 003 과음 경고 유지 | `R-F1.1-8` | — | — | ▲ 00 |
| 004 저장 · 컬렉션 · 공유 | `R-F5-2` | `bookmark` · `bookmark_collection` | `POST /me/bookmarks` | ▲ 04 |
| 005 OG 태그 | `R-F5-5` | — | — | ▲ 00 |
| 006 위치 미저장 | `PRIN-D4` | **컬럼 부재로 성립** | [SPEC-08 §5.2](./spec/SPEC-08_보안_권한_개인정보.md) | — |

### 3.6 ADMIN

| FR | 근거 | ERD | API | 화면 |
|---|---|---|---|---|
| 001 개발자 없이 발행 | PRD 12장 | — | `/admin/*` 전체 | ▲ 06 |
| 002 발행 워크플로 | [SPEC-02 §8.1](./spec/SPEC-02_도메인모델.md) | `status` CHECK | `POST …/publish` `…/unpublish` | ▲ 06 |
| **003 실패 항목 전부 반환** | `FR-COCKTAIL-010` | — | **`violations[]`** ([§1.4](./spec/SPEC-07_API명세.md)) | ▲ 06 |
| 004 검증 태스크 큐 | `R-F3.1-2` | `bar(hours_verified_at)` | `GET /admin/tasks` | ▲ 06 |
| 005 감사 로그 조회 | `PRIN-T8` | `audit_log` | `GET /admin/audit-logs` | ▲ 06 |
| **006 노출 규칙 조정 불가** | `PRIN-P2` | **컬럼 없음** | **엔드포인트 없음** | **권한 없음** ([SPEC-08 §2](./spec/SPEC-08_보안_권한_개인정보.md)) |
| 007 재료 승인 단계 | `FR-INGREDIENT-001` | `is_approved` | `POST …/approve` | ▲ 06 |
| 008 재생성 트리거 | `PRIN-T4` | — | [§4](./spec/SPEC-07_API명세.md) | — |

### 3.7 PARTNER  *(Phase 1b — 착수 보류)*

| FR | 근거 | ERD | API | 화면 |
|---|---|---|---|---|
| 001 등급 · 자동 강등 | PRD 9.1 | `partner_contract` | `POST /admin/bars/{id}/tier` | ▲ 05 |
| 002 배지 일관 노출 | `R-F4.2-1` | `partner_contract.tier` | `GET /bars` | ▲ 02 |
| **003 부스팅 상위 3중 1** | `R-F4.2-2` | **저장 안 함** | 정렬 로직 상수 | — |
| **004 홈 슬롯 30%** | `R-F4.2-4` | **저장 안 함** | 〃 | — |
| **005 제휴 콘텐츠 라벨** | `R-F4.2-3` | `article.is_sponsored` | — | ▲ 02 |
| 006 배지 vs 라벨 색 분리 | `INV-PARTNER-04` | — | — | [SCREENS-00 §9](./screens/SCREENS-00_인덱스_공통규칙.md) |
| 007 `verified` 권한 | PRD 9.1 | `bar_owner` + `tier` | `PATCH /partner/bars/{id}` | ▲ 05 |
| 008 순위에 제휴 영향 없음 | `R-F3.3-3` | — | 랭킹 로직 | ▲ 02 |

---

## 4. 역방향 — 화면 명세 대기

**P0 77건 중 17건이 화면에서만 확인 가능하다.** ERD·API로는 검증할 수 없는 것들이다.

| 화면 | 대기 중인 P0 | 상태 |
|---|---|---|
| SCREENS-00 공통 | 과음 경고 · OG · 축 조합 경로 금지 · 소셜 로그인 | ✅ 해소 |
| SCREENS-01 칵테일 | 상세 블록 · 잔 수 환산 · 단위 토글 · 패싯 UI · 대체재 펼침 · 카테고리 · 재료 사전 | ✅ 해소 |
| SCREENS-02 바 | 최종 확인일 표시 · 별점 부재 · 지도 토글 · 배지 색 분리 | ⬜ *(1b)* |
| SCREENS-03 파인더 | 도수 구간 공유 | ⬜ |
| SCREENS-04 마이 | 저장 · 컬렉션 | ⬜ |
| SCREENS-05 파트너 | 대시보드 | ⬜ *(1b)* |
| SCREENS-06 어드민 | 발행 UI · 게이트 실패 표시 · 태스크 큐 | ✅ 해소 |

**17건 중 11건이 SCREENS-00·01로 해소됐다.**

남은 6건 중 **바·파트너 관련은 Phase 1b**라 지금 급하지 않다.
**SCREENS-06 완료로 Phase 1a의 화면 명세가 닫혔다.** 남은 것은 전부 Phase 1b(바·파트너)이거나
Phase 2(마이·파인더 확장)다.

---

## 5. 게이트

Phase 1 착수 판정 기준. 전부 통과해야 구현을 시작한다.

| 게이트 | 상태 |
|---|---|
| G0 · PRD `R-*` 전부 FR로 전개 | ✅ 54/55 (1건은 의도적 폐기) |
| G1 · 불변식 전부 강제 위치 지정 | ✅ 25/25 |
| G2 · P0 FR이 ERD·API 경로 확보 | ✅ 60/77 (나머지 17은 화면 전용) |
| G3 · 화면 명세 | ◐ **Phase 1a 완료** (00·01·06) · 1b용 02·05 및 03·04 미작성 |
| G4 · 계측 이벤트 정의 | ✅ [SPEC-10](./spec/SPEC-10_계측_이벤트.md) — 1a 7종 · 1b 3종 |
| G5 · NFR 측정 기준 | ✅ [SPEC-04](./spec/SPEC-04_비기능요구사항.md) — 릴리즈 게이트 포함 |
| G6 · 호스팅 · 이미지 저장소 | ⬜ 사업 결정 |
| G7 · 법률 검토 1회 | ⬜ [ADR-0004](./decisions/ADR-0004-age-gate.md)의 전제 확인 포함 |

**G0 · G1 · G2 · G4 · G5 통과.**

| 남은 게이트 | 성격 |
|---|---|
| G3 화면 명세 | Phase 1a는 완료(00·01·06). 나머지는 1b·Phase 2 |
| G6 호스팅 · 이미지 저장소 | **사업/인프라 결정** — 스펙으로 풀리지 않는다 |
| G7 법률 검토 | **외부 검토** — 같음 |

**문서로 닫을 수 있는 게이트는 전부 닫혔다.**
