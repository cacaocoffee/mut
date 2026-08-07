---
id: ISSUE-005
title: user · user_role + httpOnly 세션
domain: USER
layer: api
wave: 1
status: TODO
depends_on: [ISSUE-002, ISSUE-003]
fr: [FR-USER-001]
r: [R-F5-3]
inv: []
nfr: [NFR-SEC-01]
migration: V005
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/user/domain/**
  - apps/api/src/main/kotlin/kr/kcocktail/user/repository/**
  - apps/api/src/main/kotlin/kr/kcocktail/common/security/session/**
  - apps/api/src/main/resources/db/migration/V005__*.sql
---

## 근거

**SPEC-07 §1.2 인증 — httpOnly 쿠키 세션**

> Spring Security 세션을 `httpOnly` · `Secure` · `SameSite=Lax` 쿠키로 담는다.
> 토큰을 JS에서 읽을 수 없어 XSS로 탈취되지 않고, **제휴 등급 강등·계정 차단이 즉시 반영된다.**
>
> ⚠ **호스팅 제약** — 쿠키를 공유하려면 프론트와 API가 같은 상위 도메인이어야 한다.
> 예: `www.example.kr` / `api.example.kr`. 이 제약을 **G-07 호스팅 결정에 반영한다.**

**SPEC-08 §4.1 세션**

| 항목 | 값 |
|---|---|
| 저장 | 서버 세션 (Spring Session) |
| 쿠키 | `httpOnly` · `Secure` · `SameSite=Lax` |
| 수명 | 일반 **30일 rolling** · **`editor`/`admin` 8시간 절대** |
| 무효화 | 로그아웃 · 역할 변경 · 차단 시 즉시 |

> 어드민 세션을 짧게 두는 이유는 **발행 권한이 곧 콘텐츠 신뢰**이기 때문이다. 공용 PC에 남은 세션으로 아무나 발행할 수 있으면 안 된다.

**SPEC-06 §3.5**

```
user
  provider       VARCHAR(12)  CHECK — kakao · naver · apple
  provider_uid   VARCHAR(120) UNIQUE (provider, provider_uid)
  display_name   VARCHAR(60)  NOT NULL
  email          VARCHAR(255) NULL 허용 — 애플 비공개 릴레이 등
```

> **성인 인증 관련 테이블은 없다** (ADR-0004). **위치 정보를 저장하는 컬럼도 없다** (`PRIN-D04`).

**SPEC-06 §3.5 `user_role`** — 역할을 `user` 컬럼이 아니라 **별도 테이블**로 둔다.

> **팀 규모가 작아 한 사람이 에디터이면서 관리자인 경우가 실제로 생긴다** — 단일 컬럼이면 둘 중 하나를 포기해야 한다.

`(user_id, role)` PK. `role` CHECK — `member`·`editor`·`partner_owner`·`admin`. `granted_by`·`granted_at` 부여 이력.

**SPEC-08 §1**: 역할은 **누적되지 않는다.** `editor`가 `admin` 권한을 갖지 않는다.

**SPEC-08 §5.3 탈퇴·파기**

| 데이터 | 처리 |
|---|---|
| `user` 행 | 즉시 삭제 |
| 북마크·컬렉션·내 술장 | 즉시 삭제 (CASCADE) |
| `analytics_event.user_id` | **`NULL`로 익명화** — 행은 남긴다 |
| `audit_log.actor_user_id` | **유지** — 누가 발행했는지는 기록이다 |

## RED

### 스키마

1. `provider_3종만_허용` — `kakao`·`naver`·`apple` (CHECK)
2. `provider와_provider_uid_조합이_유일하다`
3. `display_name은_필수다`
4. `email은_NULL_허용이다` — 애플 비공개 릴레이 (SPEC-08 §4.2)
5. `성인인증_관련_컬럼이_없다` (ADR-0004) — `birth_date`·`age_verified` 등 부재 단언
6. `위치_좌표_컬럼이_없다` (`PRIN-D04`) — `lat`·`lng`·`location` 부재
7. `user_role_PK가_user_id_role_복합이다`
8. `역할_4종만_허용` — `member`·`editor`·`partner_owner`·`admin`
9. `한_사용자가_복수_역할을_가질_수_있다` (SPEC-06 §3.5)
10. `같은_역할_중복_부여는_거부된다`
11. `granted_by와_granted_at이_기록된다`

### 세션 (SPEC-08 §4.1)

12. `세션_쿠키에_httpOnly가_설정된다` (`NFR-SEC-01`)
13. `세션_쿠키에_Secure가_설정된다`
14. `세션_쿠키에_SameSite_Lax가_설정된다`
15. `일반_사용자_세션은_30일_rolling이다` — 활동 시 갱신
16. `editor_세션은_8시간_절대다` — 활동해도 연장되지 않음
17. `admin_세션은_8시간_절대다`
18. `역할이_editor와_member_둘_다면_8시간이_적용된다` — 짧은 쪽이 이긴다 ⚖️ (보수적 해석, GAPS 등재)
19. `로그아웃시_세션이_즉시_무효화된다`
20. `역할_변경시_세션이_즉시_무효화된다` (SPEC-08 §4.1)
21. `세션은_서버_저장이다` — 쿠키에 사용자 정보가 담기지 않음
22. `강등이_다음_요청부터_즉시_반영된다` (SPEC-08 §3.3 "세션에 캐시하지 않는다")

### 탈퇴 (SPEC-08 §5.3)

23. `탈퇴시_user_행이_삭제된다`
24. `탈퇴시_북마크가_CASCADE_삭제된다` — 북마크 테이블은 이슈 031, 여기서는 FK 설정만
25. `탈퇴해도_audit_log_actor_user_id는_유지된다` — 감사 테이블은 이슈 014
26. `탈퇴시_analytics_event_user_id는_NULL로_익명화된다` — 이벤트 테이블은 이슈 034

> RED 24~26은 대상 테이블이 아직 없다. **탈퇴 서비스의 인터페이스와 계약을 정의하고 `@Disabled` + 이슈 번호 주석**을 남긴다. 해당 이슈가 해제한다.

### 인증 상태

27. `미인증_요청은_401` (SPEC-07 §1.4)
28. `GET_me는_프로필과_역할을_반환한다` (SPEC-07 §2.5)
29. `me_응답에_내부_id가_노출되지_않는다` ⚖️ — SPEC-07 §1.1은 "공개 리소스"에 한정. `/me`는 본인 것이라 `id` 노출이 문제되지 않을 수 있다. 보수적으로 **제외** + GAPS

## GREEN

### `V005__user.sql`

```sql
CREATE TABLE "user" (                          -- user 는 예약어라 따옴표
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  provider VARCHAR(12) NOT NULL CHECK (provider IN ('kakao','naver','apple')),
  provider_uid VARCHAR(120) NOT NULL,
  display_name VARCHAR(60) NOT NULL,
  email VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (provider, provider_uid)
);
CREATE TABLE user_role (
  user_id BIGINT NOT NULL REFERENCES "user" ON DELETE CASCADE,
  role VARCHAR(16) NOT NULL CHECK (role IN ('member','editor','partner_owner','admin')),
  granted_by BIGINT REFERENCES "user",
  granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, role)                  -- SPEC-06 §5: 인증 시 역할 로드
);
```

`user` 가 Postgres 예약어라 따옴표가 필요하다. **테이블명을 `app_user` 로 바꾸지 않는다** — SPEC-06 §3.5가 `user`로 명시했고, 이름을 바꾸면 문서와 어긋난다.

### 세션 저장소

SPEC-08 §9: "세션 저장소 (Redis 도입 여부) — **Phase 1은 DB 세션으로 충분.** 인스턴스가 늘면 재검토"
→ **Spring Session JDBC**. Redis를 지금 들이지 않는다.

### 역할별 세션 수명 (RED 15~18)

```kotlin
// SPEC-08 §4.1 — editor/admin 은 8시간 절대, 그 외 30일 rolling
fun maxInactiveInterval(roles: Set<Role>): Duration =
    if (roles.any { it in setOf(EDITOR, ADMIN) }) ABSOLUTE_8H else ROLLING_30D
```

**8시간이 "절대"라는 것이 요점이다** — rolling이 아니라 발급 시각 기준. Spring Session의 `maxInactiveInterval`만으로는 표현되지 않으므로 세션 속성에 발급 시각을 넣고 필터에서 검사한다.

### ⚠️ G-07 — 쿠키 도메인

`Secure`·`SameSite=Lax`·도메인 설정은 환경변수로 뺀다. **호스팅이 미정**이고 SPEC-07 §1.2가 "프론트와 API가 같은 상위 도메인"을 요구한다. 로컬은 `localhost` 로 동작하되, **이 제약을 `apps/api/README.md`에 적는다.**

**하지 말 것**:
- OAuth 실제 연동 — 이슈 030
- 권한 매트릭스 판정 — 이슈 006
- CSRF — 이슈 007

## DoD

- [ ] RED 29항 통과 (24~26은 `@Disabled` + 이슈 번호)
- [ ] 성인 인증·위치 컬럼 부재 (RED 5·6 — ADR-0004, `PRIN-D04`)
- [ ] `editor`/`admin` 8시간 **절대** 만료 (RED 16·17)
- [ ] 쿠키 도메인이 환경변수, README에 G-07 제약 명시
- [ ] ⚖️ 2건(복수 역할 세션 수명·`/me` id 노출) `GAPS.md` 등재
- [ ] 커밋: `feat(user): user·user_role·httpOnly 세션 (FR-USER-001, SPEC-08 §4.1)`
