# SPEC-07 — API 명세

| | |
|---|---|
| 버전 | v1.0 |
| 최종 수정 | 2026-08-06 |
| 상위 문서 | [SPEC-05 아키텍처](./SPEC-05_아키텍처.md) · [SPEC-06 ERD](./SPEC-06_데이터모델_ERD.md) |

> **이 문서는 계약의 설명이지 계약 자체가 아니다.**
> 정본은 Spring이 생성하는 OpenAPI 스펙이고, 프론트 TS 타입은 거기서 뽑는다 (`PRIN-T02`).
> 여기와 OpenAPI가 다르면 OpenAPI가 맞다. 이 문서는 **왜 그런 모양인지**를 남긴다.

---

## 1. REST 규약

### 1.1 기본

| 항목 | 규칙 |
|---|---|
| 베이스 | `/api/v1` |
| 형식 | `application/json; charset=utf-8` |
| 명명 | 경로는 `kebab-case` 복수형, 필드는 `camelCase` |
| 공개 식별자 | **`slug`** — 공개 리소스는 `id`를 노출하지 않는다 (`PRIN-D02`) |
| 내부 식별자 | 어드민·파트너 API만 `id` 사용 |
| 시각 | ISO 8601 UTC (`2026-08-06T12:00:00Z`) |

### 1.2 인증 — httpOnly 쿠키 세션

Spring Security 세션을 `httpOnly` · `Secure` · `SameSite=Lax` 쿠키로 담는다.
토큰을 JS에서 읽을 수 없어 XSS로 탈취되지 않고, 제휴 등급 강등·계정 차단이 **즉시** 반영된다.

> ⚠ **호스팅 제약** — 쿠키를 공유하려면 프론트와 API가 같은 상위 도메인이어야 한다.
> 예: `www.example.kr` / `api.example.kr`. 이 제약을 [G-07](../prd/GAPS.md#g-07) 호스팅 결정에 반영한다.

Next.js 서버 컴포넌트에서 API를 호출할 때는 **들어온 쿠키를 그대로 전달**한다.

상태 변경 요청(`POST`·`PATCH`·`DELETE`)은 CSRF 토큰을 요구한다.
`GET /api/v1/auth/csrf`가 발급하고 `X-CSRF-Token` 헤더로 보낸다.

Phase 3 앱 전환 시에는 별도 토큰 방식을 병행한다. **지금 선제적으로 만들지 않는다.**

### 1.3 권한

| 표기 | 의미 |
|---|---|
| — | 공개 |
| 🔒 | 로그인 필요 |
| 🔒 `editor` | 에디터 이상 |
| 🔒 `partner_owner` | 자기 바에 한정 |
| 🔒 `admin` | 관리자 |

스코프 상세는 [SPEC-08 §2 권한 매트릭스](./SPEC-08_보안_권한_개인정보.md)에 있다.

`partner_owner`는 **경로의 `barId`를 신뢰하지 않는다** — `bar_owner` 테이블로 소유를 검증하고
실패 시 `403`이 아니라 **`404`** 로 응답한다. `403`이면 그 바의 존재가 새어나간다 (SPEC-08 §3.2).

### 1.4 에러 — RFC 9457 Problem Details

`application/problem+json`.

```json
{
  "type": "https://api.example.kr/problems/validation-failed",
  "title": "요청을 처리할 수 없습니다",
  "status": 422,
  "detail": "향과 맛 서술이 비어 있어 발행할 수 없습니다.",
  "instance": "/api/v1/admin/cocktails/12/publish",
  "violations": [
    { "code": "GATE-COCKTAIL-01", "field": "tastingNote", "message": "향과 맛 서술은 발행 필수입니다." },
    { "code": "GATE-COCKTAIL-05", "field": "story",       "message": "클래식으로 분류된 항목은 관련 이야기가 필요합니다." }
  ]
}
```

**`violations`는 항상 배열이고 실패한 항목을 전부 담는다.** `FR-ADMIN-003`이
"하나씩 고치게 하지 않는다"고 요구한 지점이다. 첫 실패에서 멈추지 않는다.

`code`는 [SPEC-02](./SPEC-02_도메인모델.md)의 `INV-` / `GATE-` ID를 그대로 쓴다.
클라이언트가 문구가 아니라 코드로 분기할 수 있어야 한다.

| 상태 | 쓰는 곳 |
|---|---|
| `400` | 문법적으로 잘못된 요청 |
| `401` | 미인증 |
| `403` | 권한 없음 |
| `404` | 없음. **비공개 리소스도 404** — 존재 여부를 흘리지 않는다 |
| `409` | 상태 충돌 (이미 발행됨 등) |
| `422` | 도메인 규칙 위반 — `violations` 포함 |
| `429` | 레이트 리밋 |

### 1.5 목록 · 페이지네이션

```
GET /api/v1/cocktails?page=0&size=24&sort=abv,asc
```

```json
{
  "items": [ … ],
  "page": { "number": 0, "size": 24, "totalElements": 137, "totalPages": 6 }
}
```

Phase 1 규모(칵테일 500 · 바 300)에서는 offset으로 충분하다.
커서 페이지네이션은 목록이 수천 건을 넘거나 실시간 삽입이 잦아질 때 도입한다.

### 1.6 캐싱

공개 조회 API는 `ETag`와 `Cache-Control: public, max-age=60, stale-while-revalidate=600`를 붙인다.
SSG 빌드가 같은 엔드포인트를 반복 호출하므로 실효가 크다.

### 1.7 멱등성

`PRIN-T07`. 재시도가 전제인 요청은 `Idempotency-Key` 헤더를 요구한다.
같은 키로 온 요청은 첫 결과를 그대로 돌려준다.

대상: 이벤트 수집 · 쿠폰 사용(P3) · 알림 발송.

---

## 2. API 카탈로그

### 2.1 COCKTAIL

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| `GET` | `/cocktails` | — | 목록 · 필터. 발행분만 |
| `GET` | `/cocktails/facets` | — | 축별 결과 개수 (`R-F2.1-2`) |
| `GET` | `/cocktails/{slug}` | — | 상세 |
| `GET` | `/cocktails/{slug}/related` | — | 배리에이션 (`R-C-3`) |
| **`GET`** | **`/cocktails/{slug}/bars`** | — | **이 칵테일을 마실 수 있는 바** ★ |
| `GET` | `/cocktails/{slug}/recipes` | — | 레시피 버전 목록 |
| `GET` | `/categories` | — | 3축 슬러그 전체. 사이트맵·`generateStaticParams`용 |
| `POST` | `/admin/cocktails` | 🔒 `editor` | 생성 |
| `PATCH` | `/admin/cocktails/{id}` | 🔒 `editor` | 수정 |
| **`POST`** | **`/admin/cocktails/{id}/publish`** | 🔒 `editor` | **발행 게이트 검사** |
| `POST` | `/admin/cocktails/{id}/unpublish` | 🔒 `editor` | 회수 |
| `PUT` | `/admin/cocktails/{id}/recipes/{rid}` | 🔒 `editor` | 레시피 교체 |

### 2.2 INGREDIENT

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| `GET` | `/ingredients` | — | 재료 사전 목록 |
| `GET` | `/ingredients/{slug}` | — | 상세 · 국내 유통 · 대체재 |
| `GET` | `/ingredients/{slug}/cocktails` | — | 이 재료를 쓰는 칵테일 (`R-F1.3-1`) |
| `POST` | `/admin/ingredients` | 🔒 `editor` | 생성 — 승인 대기 상태 |
| `POST` | `/admin/ingredients/{id}/approve` | 🔒 `admin` | 승인 (`FR-ADMIN-007`) |

### 2.3 BAR

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| `GET` | `/bars` | — | 목록 · 상권/스타일/영업중 필터 |
| `GET` | `/bars/{slug}` | — | 상세 |
| `GET` | `/bars/{slug}/menu` | — | 메뉴. `source`별 구분 표기 |
| **`GET`** | **`/bars/{slug}/cocktails`** | — | **이 바의 시그니처 레시피** ★ 역방향 훅 |
| `POST` | `/admin/bars` | 🔒 `editor` | 생성 |
| `PATCH` | `/admin/bars/{id}` | 🔒 `editor` | 수정 |
| `POST` | `/admin/bars/{id}/verify-hours` | 🔒 `editor` | `hours_verified_at` 갱신 |
| `PUT` | `/admin/bars/{id}/menu` | 🔒 `editor` | **시그니처만** (`source=editor`) |
| `PUT` | `/partner/bars/{id}/menu` | 🔒 `partner_owner` | 전체 메뉴판 (`source=partner`) |

어드민과 파트너의 메뉴 편집 경로를 나눈 것이
[ADR-0003](../decisions/ADR-0003-graph-source-and-abv-bands.md)의 입력 주체 분리를 API로 표현한 것이다.
`PUT /admin/bars/{id}/menu`는 `is_signature=false` 항목을 거부한다.

### 2.4 SEARCH

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| `GET` | `/search?q=` | — | 통합 검색 — **타입별 그룹핑** (`R-F5-1`) |
| `GET` | `/search/suggest?q=` | — | 자동완성. 초성·별칭 매칭 |

### 2.5 USER

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| `GET` | `/auth/csrf` | — | CSRF 토큰 |
| `GET` | `/auth/{provider}/authorize` | — | 카카오·네이버·애플 |
| `GET` | `/auth/{provider}/callback` | — | 세션 쿠키 발급 |
| `POST` | `/auth/logout` | 🔒 | |
| `GET` | `/me` | 🔒 | 프로필 · 역할 |
| `GET` | `/me/bookmarks` | 🔒 | |
| `POST` | `/me/bookmarks` | 🔒 | `{targetType, targetSlug, collectionId?}` |
| `DELETE` | `/me/bookmarks/{id}` | 🔒 | |
| `POST` | `/me/collections` | 🔒 | |
| `GET` | `/collections/{shareToken}` | — | 공유 링크 (`R-F5-2`) |

성인 인증 엔드포인트는 없다 ([ADR-0004](../decisions/ADR-0004-age-gate.md)).

### 2.6 PARTNER

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| `PATCH` | `/partner/bars/{id}` | 🔒 `partner_owner` | 정보 직접 관리 (`verified` 이상) |
| `GET` | `/partner/bars/{id}/stats` | 🔒 `partner_owner` | 조회·저장·외부 액션 (`R-F4.3-1`) |
| **`GET`** | **`/partner/bars/{id}/inbound-cocktails`** | 🔒 `partner_owner` | **유입 칵테일 랭킹** (`R-F4.3-2`) |
| `POST` | `/partner/bars/{id}/edit-requests` | 🔒 `partner_owner` | 수정 요청 (`listed` 등급) |
| `POST` | `/admin/bars/{id}/tier` | 🔒 `admin` | 등급 변경 — 감사 로그 |

**노출 규칙을 조정하는 엔드포인트를 만들지 않는다** (`PRIN-P02` · `FR-ADMIN-006`).
부스팅 한도·홈 슬롯 비율은 API 표면에 존재하지 않는다.

### 2.7 ADMIN

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| `GET` | `/admin/tasks` | 🔒 `editor` | 검증 태스크 큐 (`FR-ADMIN-004`) |
| `POST` | `/admin/tasks/{id}/resolve` | 🔒 `editor` | |
| `GET` | `/admin/audit-logs` | 🔒 `admin` | 이력 조회 (`PRIN-T08`) |
| `POST` | `/admin/media` | 🔒 `editor` | 이미지 업로드 |

### 2.8 ANALYTICS

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| `POST` | `/events` | — | 이벤트 수집. 배열 배치. `Idempotency-Key` 필수 |

### 2.9 Phase 2 — 경로만 확보

| 경로 | 설명 |
|---|---|
| `GET` `PUT` `/me/stock` | 내 술장 재료 |
| `GET` `/me/stock/makeable` | 지금 만들 수 있는 것 (`R-F2.2-1`) |
| `GET` `/me/stock/one-away` | **재료 1개만 더 있으면** (`R-F2.2-2`) |
| `GET` `/articles` `/articles/{slug}` | 스토리 |
| `GET` `/curations` `/curations/{slug}` | 큐레이션 리스트 |

---

## 3. 핵심 엔드포인트 상세

### 3.1 `GET /cocktails` — 목록 · 필터

```
GET /api/v1/cocktails
  ?base=gin,vodka        기주 (OR)
  &style=sour            스타일 (OR)
  &method=shake          메이킹 방법 (OR)
  &sweet=semi_dry        당도 (단일)
  &abv=mid,high          도수 구간 (OR) — ADR-0003
  &flavor=citrus,herbal  향·맛 (AND)
  &q=네그로니
  &page=0&size=24
```

**향·맛만 AND인 것이 의도다.** "시트러스 **그리고** 허브"를 원하지 "시트러스 또는 허브"가 아니다.
나머지 축은 OR — "진 **또는** 보드카"가 자연스럽다.

색인하지 않는다. 응답에 `X-Robots-Tag: noindex`를 붙인다 (`R-F2.1-1`).

### 3.2 `GET /cocktails/facets` — 패싯 카운트

`R-F2.1-2`. 현재 필터를 그대로 받아 **각 값을 선택했을 때의 결과 수**를 돌려준다.

```json
{
  "base":   { "gin": 5, "vodka": 4, "whisky": 5, "korean": 3, "non-alcoholic": 1 },
  "style":  { "highball": 8, "sour": 7, "spirit-forward": 6, "tiki": 1, "creamy": 2 },
  "abv":    { "na": 1, "low": 2, "mid": 8, "high": 13 },
  "flavor": { "citrus": 9, "sour": 7, "floral": 0, … },
  "sweet":  { "dry": 3, "semi_dry": 9, "semi_sweet": 10, "sweet": 1 }
}
```

축마다 계산 방식이 다르다.

| 축 | 계산 |
|---|---|
| 기주 · 스타일 · 메이킹 · 당도 · 도수 | **같은 축의 현재 선택을 무시**하고 그 값만 골랐을 때의 수 |
| 향·맛 | AND라서 **현재 선택에 이 태그를 더했을 때**의 수 |

향·맛만 다른 이유는 조합 불가능한 태그가 즉시 0으로 떨어져야 하기 때문이다 (`FR-SEARCH-009`).

**0인 값도 응답에 포함한다.** 클라이언트가 비활성 처리하려면 존재를 알아야 한다.
다만 **코퍼스에 아예 없는 값은 제외한다** — `floral`처럼 항목이 0건이면 필터에 띄우지 않는다
([ADR-0002](../decisions/ADR-0002-taxonomy.md) §5).

### 3.3 `GET /cocktails/{slug}/bars` ★ 핵심 훅

`PRIN-P01`의 그래프가 실제로 흐르는 지점.

```json
{
  "items": [
    {
      "slug": "bar-cham",
      "nameKo": "바 참",
      "district": "을지로",
      "menuItemName": "네그로니",
      "isSignature": true,
      "source": "editor",
      "partnerTier": "listed",
      "reservationType": "walk_in_ok"
    }
  ]
}
```

- `source`를 노출하는 이유는 **에디터 취재분과 파트너 입력분의 신뢰 성격이 다르기 때문**이다.
  화면에서 구분 표기할지는 SCREENS에서 정한다.
- 정렬에 파트너 부스팅을 적용하되 **상위 3개 중 1개까지만** (`INV-PARTNER-01`).
  이 제약은 응답을 만드는 서비스 안에 상수로 있고, 요청 파라미터로 바꿀 수 없다.

### 3.4 `POST /admin/cocktails/{id}/publish` — 발행 게이트

`GATE-COCKTAIL-01~06`을 **전부 검사한 뒤** 결과를 한 번에 돌려준다 (`FR-ADMIN-003`).

성공 `200`:

```json
{ "slug": "negroni", "status": "published", "publishedAt": "2026-08-06T12:00:00Z" }
```

실패 `422`: §1.4의 `violations` 배열. **첫 실패에서 멈추지 않는다.**

부수효과 — 성공 시에만:

1. `audit_log`에 `publish` 기록 (`PRIN-T08`)
2. `search_document` 동기화 (초성 분해 포함)
3. **프론트 on-demand 재생성 호출** (§4)
4. `slug` 확정 — 이후 변경 불가 (`INV-COCKTAIL-05`)

이미 `published`면 `409`.

### 3.5 `GET /partner/bars/{id}/inbound-cocktails`

`R-F4.3-2`. 파트너 갱신을 설득하는 데이터라 **제휴 상품의 핵심 근거**다.

```json
{
  "period": { "from": "2026-07-01", "to": "2026-07-31" },
  "items": [
    { "cocktailSlug": "negroni", "nameKo": "네그로니", "inboundCount": 142 },
    { "cocktailSlug": "boulevardier", "nameKo": "불바디에", "inboundCount": 87 }
  ]
}
```

`analytics_event`의 `cross_nav`에서 `to_type='bar' AND to_id={id}`를 집계한다.
**외부 분석 도구만 썼다면 이 응답을 만들 수 없다** (SPEC-06 §3.8).

---

## 4. 재생성 훅 — API → 프론트

방향이 반대인 유일한 호출이다. 백엔드가 프론트를 부른다.

```
POST {FRONTEND_URL}/api/revalidate
X-Revalidate-Secret: <공유 시크릿>

{ "paths": ["/cocktails/negroni", "/cocktails/base/gin"] }
```

에디터가 발행하고 반영을 기다리지 않아야 한다 (`FR-COCKTAIL-016` · `PRIN-T04`).
실패해도 발행 트랜잭션을 되돌리지 않는다 — ISR 주기가 결국 따라잡는다. 실패는 로그로 남긴다.

---

## 5. 공개 API의 노출 범위

SSG 빌드와 브라우저가 **같은 엔드포인트**를 쓴다. 별도의 내부 전용 조회 API를 두지 않는다 —
두 벌이 되면 반드시 어긋난다.

따라서 공개 응답에 다음을 담지 않는다.

- 내부 `id` (공개 리소스는 `slug`만)
- `draft` · `archived` 상태의 리소스 (**404로 응답한다**)
- `abv_calculated` / `abv_override` 구분 — 표시값 `abv` 하나만
- 파트너 계약 조건(`since` · `until`), 통계 원본
- 에디터 노트의 미발행 초안

---

## 6. 미정

| 항목 | 갭 |
|---|---|
| 도메인 구성 (쿠키 공유 전제) | [G-07](../prd/GAPS.md#g-07) — **인증 방식이 호스팅에 건 제약** |
| ~~레이트 리밋 임계값~~ | ✅ [SPEC-08 §6](./SPEC-08_보안_권한_개인정보.md) |
| 이미지 업로드 방식 (직접 vs presigned) | 저장소 백엔드에 종속 |
