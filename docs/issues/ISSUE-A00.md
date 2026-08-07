---
id: ISSUE-A00
title: 스펙 정합 — SPEC-06 테이블 2건 보강 · 문서 충돌 2건 해소
domain: —
layer: docs
wave: 0
status: TODO
depends_on: []
fr: []
r: []
inv: []
nfr: [NFR-S-07]
migration: —
owns:
  - docs/spec/SPEC-06_데이터모델_ERD.md
  - docs/spec/SPEC-03_기능요구사항.md
  - docs/TRACE-00_추적매트릭스.md
---

> **이 이슈는 코드를 쓰지 않는다.** [G-18](../prd/GAPS.md#g-18)·[G-19](../prd/GAPS.md#g-19)·[G-20](../prd/GAPS.md#g-20)을 문서에서 해소한다.
> **ISSUE-000보다 먼저** 한다 — 나중에 하면 이미 만든 이슈 여러 개를 되돌려야 한다.

## 근거

**CLAUDE.md**: "어긋난 걸 발견하면 코드를 몰래 맞추지 말고 `GAPS.md`에 올린다"
**SPEC-00 §4**: 원칙을 어길 때는 ①GAPS 등재 ②ADR ③되돌리는 조건. **조용히 어기지 않는다**

Phase 1a 이슈 47개를 작성하며 스펙을 전수 대조한 결과 4건이 나왔다. 전부 `GAPS.md` D등급에 등재돼 있다.

---

## 할 일

### 1. SPEC-06에 `verification_task` 추가 (G-19 — **P0가 걸려 있다**)

`FR-ADMIN-004`(검증 태스크 큐)는 **P0**인데 저장소가 ERD에 없다.
TRACE-00 §3.6은 ERD 칸에 `bar(hours_verified_at)`를 적었지만 **그건 발생 조건이지 큐가 아니다.**
SPEC-06 §4.3이 "위반 건을 관리자 태스크로 올린다"고 한 것도 저장소를 전제한다.

**제안 스키마** (이슈 016이 만들 것):

```sql
verification_task
  task_type    VARCHAR(32)  -- invariant_violation(1a) · hours_expired(1b) · instagram_signal(1b)
  entity_type  entity_id
  code         VARCHAR(40)  -- INV-/GATE- ID
  detail       JSONB
  status       VARCHAR(12) CHECK ('open','resolved','dismissed')
  detected_at  resolved_at  resolved_by
  UNIQUE (task_type, entity_type, entity_id, code)   -- 멱등 (PRIN-T07)
```

`FR-BAR-004`(90일 검증)·인스타 폐업 신호(SPEC-05 §8)도 **같은 테이블**을 쓴다 — `task_type`으로 구분.

### 2. SPEC-06에 `category_intro` 추가 (G-19)

`FR-COCKTAIL-031`(P1) + **`NFR-S-07`(발행 차단)** 이 요구하는데 없다. 카테고리는 테이블이 아니라 enum이라 문구를 둘 곳이 필요하다.

```sql
category_intro
  axis  VARCHAR(8) CHECK ('base','style','method')
  slug  VARCHAR(24)
  intro TEXT
  PRIMARY KEY (axis, slug)
```

### 3. `FR-ADMIN-007` 문구 정정 (G-20 ①)

`FR-ADMIN-007`은 "**에디터** 승인 단계"라 하고 SPEC-07 §2.2·SPEC-08 §2는 **`admin`** 이라 한다.

SPEC-07 §1.3이 "스코프 상세는 SPEC-08"이라 **명시적으로 위임**했고 SPEC-08 §2.2의 권한 분리 논리와도 맞으므로 **SPEC-08이 정본**이다.

→ **SPEC-03의 `FR-ADMIN-007` 문구를 고친다**: "재료 마스터 신규 추가는 에디터가 요청하고 **관리자가 승인**한다"

### 4. `FR-COCKTAIL-031` vs `NFR-S-07` 우선순위 정렬 (G-20 ②)

`FR-COCKTAIL-031`은 **P1**인데 `NFR-S-07`은 **"발행 차단"**(배포 차단급)이다. **P1 기능이 P0 발행을 막는 구조다.**

**둘 중 하나를 고른다** — 제품 결정:

| 안 | 내용 |
|---|---|
| 가 | `FR-COCKTAIL-031`을 **P0로 올린다** — 소개 문구가 없으면 카테고리 페이지는 색인 가치가 없다는 `PRIN-T04` 논리 |
| **나** | **`NFR-S-07`의 "발행 차단"을 "경고"로 내린다** — P1과 정합. 1a에서 문구 없이도 발행된다 |

**나안 권장.** 1a의 목표는 칵테일 100종 발행이지 카테고리 문구가 아니다. 다만 SEO 관점의 반론이 있을 수 있어 **결정이 필요하다.**

### 5. TRACE-00 §4·G3 정정 (G-18)

> **SCREENS-06 완료로 Phase 1a의 화면 명세가 닫혔다.**
> | G3 · 화면 명세 | ◐ **Phase 1a 완료** (00·01·06) |

**사실과 다르다.** `FR-COCKTAIL-027`·`FR-USER-004`(→SCREENS-04) · `FR-SEARCH-004`(→SCREENS-03)가 **1a P0**인데 두 화면이 미작성이다.

→ 결론과 G3 판정을 정정하고, **채택한 대응을 함께 적는다**:
- 저장·컬렉션 → **API만 1a**, 화면은 1b ([이슈 031](ISSUE-031.md))
- 파인더 → 프로토타입 화면이 있으므로 **SCREENS-03을 "기존 화면 문서화"로** ([이슈 041](ISSUE-041.md))

### 6. TRACE-00 §3.5 `FR-USER-002` ERD 칸 보강 (G-19 부수)

`article.is_sponsored` 하나만 적혀 있어 **1a에 경로가 없어 보인다**(article은 Phase 2).
FR 본문이 "`R-F1.3-3`의 광고성 구분 표기와 같은 지점에서 처리한다"고 했으므로 **`ingredient_brand.is_sponsored`(1a)** 를 병기한다.

### 7. `batch_run` 재검토 (G-21)

SPEC-05 §8은 배치를 정의하나 **실행 이력을 요구하지 않는다.** 이슈 016이 도입했다.

- **필요하다면** SPEC-05 §8 또는 SPEC-06에 근거를 추가한다
- **불필요하다면** 이슈 016에서 뺀다 — 요구 없는 테이블을 만들지 않는다

⚖️ `NFR-D-01`이 "배포 차단"이라 배치 실행 여부를 알아야 한다는 논리는 성립한다. **판단 필요.**

---

## 검증

1. `grep -c verification_task docs/spec/SPEC-06*` → 1 이상
2. `grep -c category_intro docs/spec/SPEC-06*` → 1 이상
3. `FR-ADMIN-007` 문구에 "관리자"가 있다
4. `NFR-S-07` 또는 `FR-COCKTAIL-031`의 우선순위가 정렬됐다
5. TRACE-00 §4·G3에 "1a 완료" 문장이 없거나, 대응이 함께 적혀 있다
6. TRACE-00 `FR-USER-002` 행에 `ingredient_brand` 가 있다
7. `batch_run` 이 근거를 얻었거나 이슈 016에서 빠졌다

## DoD

- [ ] 위 7항 전부
- [ ] `GAPS.md` G-18~G-21에 **해소 표시** + 무엇을 어떻게 고쳤는지
- [ ] **4번·7번은 제품 결정** — 결정 없이 닫지 않는다
- [ ] 영향받는 이슈(016·022·026·031·039·041)의 ⚖️ 항목 정리
- [ ] 커밋: `docs(spec): SPEC-06 테이블 2건 보강·문서 충돌 2건 해소 (G-18~G-21)`
