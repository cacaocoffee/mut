---
id: ISSUE-031
title: 북마크 · 컬렉션 · 공유 링크 (API만)
domain: USER
layer: api
wave: 6
status: TODO
depends_on: [ISSUE-030, ISSUE-009]
fr: [FR-USER-004]
r: [R-F5-2]
inv: []
nfr: []
migration: V031
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/user/bookmark/**
  - apps/api/src/main/resources/db/migration/V031__*.sql
---

## ⚠️ 범위 — API만 (G-18, 2026-08-07)

**화면은 Phase 1b로 넘긴다.** `SCREENS-04 마이·저장`이 미작성이고([G-09](../prd/GAPS.md#g-09)), 명세 없이 만들면 다시 만든다.

[G-18](../prd/GAPS.md#g-18)에서 발견: TRACE-00 §4가 "Phase 1a 화면 명세가 닫혔다"고 했으나 **`FR-COCKTAIL-027`·`FR-USER-004`가 미작성 SCREENS-04에 걸려 있다.**

`FR-USER-004`의 요구("컬렉션으로 묶어 저장하고 공유 링크를 생성")는 **API로 충족된다.**
저장 버튼·컬렉션 화면은 [`ISSUE-038`](ISSUE-038.md) 상세 화면의 액션 블록에 최소 형태로만 두고(`FR-COCKTAIL-027`), 전용 화면은 SCREENS-04 작성 후.

## 근거

**`FR-USER-004`**: 칵테일과 바를 **컬렉션으로 묶어 저장**하고 **공유 링크를 생성**한다 (`R-F5-2`)

**SPEC-02 §7**: `Bookmark` — `cocktail` · `bar` · `article`을 **컬렉션으로 묶고 공유 링크 생성**

**SPEC-06 §3.5 `bookmark_collection` · `bookmark`**

| `bookmark` 컬럼 | 타입 | |
|---|---|---|
| `user_id` | `BIGINT` | FK |
| `collection_id` | `BIGINT` | FK, **NULL이면 기본 컬렉션** |
| `target_type` | `VARCHAR(12)` | CHECK — `cocktail`·`bar`·`article` |
| `target_id` | `BIGINT` | |
| | | **UNIQUE (`user_id`, `target_type`, `target_id`)** |

> **다형 참조라 FK를 걸 수 없다.** **타입별 테이블로 쪼개지 않는 이유**는 컬렉션이 **세 종류를 섞어 담아야** 하기 때문이다(`R-F5-2`). **참조 무결성은 앱이 책임진다.**

**SPEC-07 §2.5**

| 메서드 | 경로 | 권한 |
|---|---|---|
| `GET` | `/me/bookmarks` | 🔒 |
| `POST` | `/me/bookmarks` | 🔒 `{targetType, targetSlug, collectionId?}` |
| `DELETE` | `/me/bookmarks/{id}` | 🔒 |
| `POST` | `/me/collections` | 🔒 |
| `GET` | `/collections/{shareToken}` | — **공유 링크** (`R-F5-2`) |

> `POST /me/bookmarks`가 **`targetSlug`** 를 받는다 — 공개 식별자가 slug이기 때문 (SPEC-07 §1.1)

**SPEC-08 §2**: 북마크·컬렉션 = 전 로그인 역할 `◐`(자기 것만). **비로그인 ―**
**SPEC-08 §5.3 탈퇴**: 북마크·컬렉션 **즉시 삭제 (CASCADE)**

**`FR-USER-005`** (이슈 044): OG 태그 최적화 — **카카오톡 공유 시 카드형 미리보기** (`R-F5-5`)

**Phase 1a 범위**: `bar`·`article`이 없다. `target_type`은 **3종 전부 정의**하되 실제로는 `cocktail`만 저장된다

## RED

### 북마크 (`FR-USER-004`)

1. `로그인_사용자가_북마크를_추가한다`
2. `비로그인은_401`
3. `targetSlug로_추가한다` — id가 아니라 slug (SPEC-07 §2.5)
4. `없는_slug는_404`
5. `발행되지_않은_대상은_404` — draft 북마크 불가
6. `중복_북마크는_거부되거나_멱등이다` ⚖️ — UNIQUE 제약. **보수적으로 멱등 200** + GAPS
7. `target_type_3종만_허용` — `cocktail`·`bar`·`article`
8. `Phase_1a에는_cocktail만_실제로_추가된다` — bar·article은 대상이 없어 404
9. `북마크를_삭제한다`
10. `타인의_북마크는_삭제할_수_없다` — 404 (SPEC-08 §2 `◐`, 이슈 006 RED 20)
11. `타인의_북마크_목록을_조회할_수_없다`

### 컬렉션 (`R-F5-2`)

12. `컬렉션을_생성한다`
13. `북마크를_컬렉션에_담는다`
14. `collection_id가_null이면_기본_컬렉션이다` (SPEC-06 §3.5)
15. `한_컬렉션에_여러_타입이_섞인다` — **`R-F5-2`의 요구**. 타입별로 쪼개지 않은 이유
16. `타인의_컬렉션에_담을_수_없다`
17. `컬렉션_이름이_필수다`

### 공유 링크 (`FR-USER-004`, `R-F5-2`)

18. `컬렉션에_공유_토큰이_발급된다`
19. `공유_링크는_비로그인도_조회_가능하다` — SPEC-07 §2.5 `GET /collections/{shareToken}` 권한 `—`
20. `공유_토큰이_추측_불가능하다` — 순차 id가 아니라 랜덤
21. `잘못된_토큰은_404`
22. `공유_응답에_소유자_개인정보가_없다` — 표시명 정도 ⚖️ + GAPS
23. `공유_응답에_내부_id가_없다`
24. `공유_토큰을_재발급하거나_해제할_수_있는가` ⚖️ — SPEC에 없다. **보수적으로 제공 안 함** + GAPS
25. `공유된_컬렉션에_미발행_항목이_있으면_제외된다`

### 참조 무결성 (SPEC-06 §3.5 — 앱 책임)

26. `대상이_archived되면_북마크가_어떻게_되는가` ⚖️ — FK가 없어 dangling이 생긴다. **보수적으로 조회 시 필터링**(행은 유지) + GAPS
27. `조회시_존재하지_않는_대상은_제외된다`
28. `앱이_참조_무결성을_검증한다` — 추가 시점

### 탈퇴 (SPEC-08 §5.3)

29. `탈퇴시_북마크가_CASCADE_삭제된다` — **이슈 005 RED 24의 `@Disabled` 해제**
30. `탈퇴시_컬렉션도_삭제된다`

### 규약

31. `응답에_내부_id가_없다` ⚖️ — `DELETE /me/bookmarks/{id}`는 id를 쓴다(SPEC-07 §2.5). **본인 리소스라 허용** + GAPS
32. `캐시_헤더가_없다` — 개인 데이터

## GREEN

### `V031__bookmark.sql`

```sql
CREATE TABLE bookmark_collection (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES "user" ON DELETE CASCADE,   -- SPEC-08 §5.3
  name VARCHAR(60) NOT NULL,
  share_token VARCHAR(64) UNIQUE,                                 -- 추측 불가 (RED 20)
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE bookmark (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES "user" ON DELETE CASCADE,
  collection_id BIGINT REFERENCES bookmark_collection ON DELETE SET NULL,  -- NULL = 기본
  target_type VARCHAR(12) NOT NULL CHECK (target_type IN ('cocktail','bar','article')),
  target_id BIGINT NOT NULL,                                      -- FK 없음 (다형 참조)
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, target_type, target_id)                        -- SPEC-06 §3.5
);
CREATE INDEX ON bookmark (user_id, collection_id);
```

**`target_id`에 FK가 없다** — SPEC-06 §3.5가 명시한 의도적 선택이다. 주석으로 근거를 남긴다. 무결성은 앱이 (RED 26~28).

### 공유 토큰 (RED 20)

```kotlin
// 순차 id 노출 금지 — 남의 컬렉션을 훑을 수 없어야 한다
val shareToken = Base64Url.encode(SecureRandom().generateSeed(24))
```

### 다형 참조 조회 (RED 27)

```kotlin
// target_type 별로 묶어 벌크 조회 후 병합. N+1 금지
val bySlug = cocktailFacade.findPublishedByIds(cocktailIds)
// 존재하지 않거나 미발행이면 결과에서 제외 (RED 25·27)
```

**모듈 경계**: `user` 모듈이 `cocktail` 테이블을 직접 조인하지 않는다. `CocktailFacade` 경유 (`PRIN-T03`).

### 탈퇴 CASCADE (RED 29·30)

`ON DELETE CASCADE`가 DB 레벨에서 처리한다. **이슈 005의 `@Disabled` 테스트를 해제**하는 것이 이 이슈의 DoD 항목이다.

**하지 말 것**:
- OG 태그 — 이슈 044
- 북마크 UI — Phase 1a 화면 목록에 명시 없음 ⚖️ (SCREENS-04 마이·저장이 미작성 — G-09). **GAPS 등재**
- 내 술장 — Phase 2

## DoD

- [ ] RED 32항 전부 통과
- [ ] 한 컬렉션에 **3종 혼합** 가능 (RED 15 — `R-F5-2`, 타입별 분리 안 한 이유)
- [ ] 공유 토큰 추측 불가 (RED 20)
- [ ] **이슈 005 RED 24의 `@Disabled` 해제** (탈퇴 CASCADE)
- [ ] `target_id` FK 부재 근거 주석 (SPEC-06 §3.5)
- [ ] `CocktailFacade` 경유 (모듈 경계)
- [ ] **화면을 만들지 않는다** — SCREENS-04 미작성 (G-09·G-18). 최소 저장 버튼은 이슈 038
- [ ] ⚖️ 5건(중복 처리·소유자 노출·토큰 해제·dangling 참조·id 노출) `GAPS.md` 등재
- [ ] 커밋: `feat(user): 북마크·컬렉션·공유 링크 (FR-USER-004, R-F5-2)`
