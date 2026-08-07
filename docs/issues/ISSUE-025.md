---
id: ISSUE-025
title: 어드민 CRUD + violations 전부 반환
domain: ADMIN
layer: api
wave: 5
status: TODO
depends_on: [ISSUE-013, ISSUE-006]
fr: [FR-ADMIN-001, FR-ADMIN-002, FR-ADMIN-003]
r: []
inv: []
nfr: [NFR-O-01]
migration: —
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/admin/content/**
---

## 근거

**`FR-ADMIN-001`** (P0): **에디터가 개발자 없이 콘텐츠를 발행할 수 있어야 한다** (PRD 12장)
**`FR-ADMIN-002`**: 발행 워크플로는 `draft → published → archived`이며 **되돌릴 수 있다** (SPEC-02 §8.1)
**`FR-ADMIN-003`**: 발행 게이트 실패 시 실패한 항목을 **전부 한 번에** 보여준다. **하나씩 고치게 하지 않는다**

**`NFR-O-01`**: 에디터가 개발자 없이 발행 — **신규 1건을 어드민만으로 완료** (인수 시나리오)

**SPEC-07 §2.1 어드민 경로**

| 메서드 | 경로 | 권한 |
|---|---|---|
| `POST` | `/admin/cocktails` | 🔒 `editor` |
| `PATCH` | `/admin/cocktails/{id}` | 🔒 `editor` |
| **`POST`** | **`/admin/cocktails/{id}/publish`** | 🔒 `editor` |
| `POST` | `/admin/cocktails/{id}/unpublish` | 🔒 `editor` |
| `PUT` | `/admin/cocktails/{id}/recipes/{rid}` | 🔒 `editor` |

**SPEC-07 §1.1**: **내부 식별자는 어드민·파트너 API만 `id` 사용** — 공개 API와 다르다

**SPEC-07 §3.4 `POST /admin/cocktails/{id}/publish`**
- `GATE-COCKTAIL-01~06`을 **전부 검사한 뒤** 결과를 한 번에 (`FR-ADMIN-003`)
- 성공 `200`: `{ "slug": "negroni", "status": "published", "publishedAt": "..." }`
- 실패 `422`: §1.4의 `violations` 배열. **첫 실패에서 멈추지 않는다**
- 이미 `published`면 `409`
- 부수효과(성공 시에만): `audit_log` · `search_document` 동기화 · **재생성 호출** · `slug` 확정

**SPEC-08 §2**: 칵테일 생성/수정·발행/회수 = `editor` ○, `admin` ○, 그 외 —
**SPEC-08 §4.1**: `editor`/`admin` 세션 **8시간 절대** — 발행 권한이 곧 콘텐츠 신뢰

**SCREENS-06 어드민** (G-15 해소) — 발행 조건 패널 · 입력 강제가 화면 정본

## RED

### 권한 (SPEC-08 §2)

1. `editor만_생성_가능` — `member`·비로그인 403
2. `admin도_생성_가능`
3. `partner_owner는_불가`
4. `editor만_발행_가능`
5. `editor만_회수_가능`
6. `미인증은_401이고_403이_아니다`

### 어드민 식별자 (SPEC-07 §1.1)

7. `어드민_경로는_id를_쓴다` — 공개는 slug, 어드민은 id
8. `어드민_응답에_id가_포함된다`
9. `어드민_응답에_status가_포함된다` — 공개와 달리 필요
10. `어드민_응답에_abv_calculated와_override가_구분돼_있다` — 에디터가 오버라이드 여부를 알아야 한다 (이슈 011 RED 30)

### draft 조회 (SPEC-08 §2)

11. `editor는_draft를_조회할_수_있다` — 어드민 경로에서
12. `member는_어드민_경로에_접근할_수_없다`
13. `공개_경로에서는_여전히_draft가_404다` — 이슈 020 RED 3과 정합

### violations 전부 (`FR-ADMIN-003`, SPEC-07 §3.4) — 요체

14. `게이트_1개_실패시_violations_1건`
15. `게이트_2개_실패시_violations_2건`
16. `게이트_6개_전부_실패시_violations_6건`
17. `첫_실패에서_멈추지_않는다`
18. `각_violation에_GATE_ID와_field와_message가_있다`
19. `violations_순서가_결정론적이다`
20. `422로_응답한다` — 400이 아니다

### 발행 (SPEC-07 §3.4)

21. `게이트_통과시_200과_slug_status_publishedAt`
22. `이미_published면_409`
23. `발행_성공시_audit_log가_남는다` (이슈 014)
24. `발행_성공시_search_document가_동기화된다` (이슈 017)
25. `발행_성공시_재생성_훅이_호출된다` (이슈 015)
26. `발행_실패시_부수효과가_전혀_없다` — "성공 시에만"
27. `재생성_훅_실패가_발행을_롤백시키지_않는다` (`NFR-R-03`)

### 회수·전이 (`FR-ADMIN-002`)

28. `published에서_draft로_되돌린다`
29. `회수시_게이트를_검사하지_않는다`
30. `회수도_audit_log에_남는다`
31. `archived로_보관할_수_있다`
32. `archived에서_draft로_복원할_수_있다`

### 우회 불가 (`PRIN-T05`)

33. `PATCH로_status를_바꿀_수_없다` — 발행은 전용 엔드포인트만 (이슈 013 RED 30)
34. `PATCH로_slug를_바꿀_수_없다` — 최초 발행 후 (이슈 014 RED 2)
35. `PATCH로_published_at을_바꿀_수_없다`

### 개발자 없이 발행 (`NFR-O-01`)

36. `신규_칵테일_1건을_API만으로_발행_완료할_수_있다` — **인수 시나리오**: 생성 → 레시피 등록 → 게이트 통과 → 발행. 마이그레이션·배포 없이

### 캐싱

37. `어드민_응답에_캐시_헤더가_없다` (이슈 003 RED 25)

## GREEN

### `admin/content`

```kotlin
@RestController
@RequestMapping("/api/v1/admin/cocktails")
@PreAuthorize("hasAnyRole('EDITOR','ADMIN')")     // SPEC-08 §2
class AdminCocktailController(
    private val publishService: CocktailPublishService,   // 이슈 013
)
```

**게이트 로직을 여기 쓰지 않는다.** 이슈 013의 `PublishGate`를 서비스가 호출하고, 컨트롤러는 예외 → 422 변환만 (이슈 003의 `@RestControllerAdvice`).

### 부수효과 순서 (RED 23~27)

```
[트랜잭션]  게이트 → status/published_at 저장 → audit_log
[커밋]
[커밋 후]   search_document 동기화 → 재생성 훅
```

⚖️ 이슈 017 RED 18에서 "동기화 실패 시 롤백?"을 보수적으로 **같은 트랜잭션**으로 정했다. 그러면 위 순서는:

```
[트랜잭션]  게이트 → 저장 → audit_log → search_document 동기화
[커밋 후]   재생성 훅 (실패해도 무시 — NFR-R-03)
```

**이슈 017의 결정을 따른다.** 두 이슈가 어긋나면 안 되므로 착수 시 확인한다.

### 어드민 DTO (RED 8~10)

```kotlin
data class AdminCocktailDetail(
    val id: Long,                       // 어드민만 (SPEC-07 §1.1)
    val slug: String,
    val status: String,
    val abvCalculated: BigDecimal?,     // 공개와 달리 구분해 노출
    val abvOverride: BigDecimal?,
    val abv: BigDecimal?,
    ...
)
```

**공개 DTO를 재사용하지 않는다.** 필드가 다르고, 재사용하면 언젠가 공개에 `id`가 샌다 (SPEC-07 §5).

### 인수 시나리오 (RED 36 — `NFR-O-01`)

```
POST /admin/cocktails               → id
PUT  /admin/cocktails/{id}/recipes  → 표준 레시피
POST /admin/cocktails/{id}/publish  → 200
GET  /cocktails/{slug}              → 200 (공개 확인)
```

**이 4단계가 통과하면 `NFR-O-01`이 서버 측에서 충족된다.** 화면은 이슈 045.

**하지 말 것**:
- 어드민 UI — 이슈 045
- 재료 승인 — 이슈 026
- 검증 태스크 — 이슈 028
- 감사 조회 — 이슈 029
- 미디어 업로드 — 이슈 045 (G-07 저장소 미정)

## DoD

- [ ] RED 37항 전부 통과
- [ ] **`violations` 전부 반환** (RED 14~19 — `FR-ADMIN-003`, SPEC-07 §3.4)
- [ ] 발행 실패 시 부수효과 0 (RED 26)
- [ ] `PATCH`로 status·slug·published_at 변경 불가 (RED 33~35)
- [ ] **인수 시나리오 통과** (RED 36 — `NFR-O-01`)
- [ ] 어드민 DTO가 공개 DTO와 분리
- [ ] 커밋: `feat(admin): 어드민 CRUD·발행 (FR-ADMIN-001·002·003, NFR-O-01)`
