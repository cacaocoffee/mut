---
id: ISSUE-030
title: 소셜 로그인 3종 (OAuth PKCE)
domain: USER
layer: api
wave: 6
status: TODO
depends_on: [ISSUE-005, ISSUE-007]
fr: [FR-USER-001]
r: [R-F5-3]
inv: []
nfr: [NFR-SEC-01]
migration: —
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/user/oauth/**
---

## 근거

**`FR-USER-001`**: **카카오 · 네이버 · 애플** 소셜 로그인을 제공한다. **이메일 가입은 후순위** (`R-F5-3`)

**SPEC-08 §4.2 소셜 로그인** — OAuth 2.0 **Authorization Code + PKCE**

```
GET  /auth/{provider}/authorize   → state 발급, provider로 리다이렉트
GET  /auth/{provider}/callback    → state 검증 → 세션 발급
```

- **`state`는 1회용, 10분 만료.** 재사용 시도는 **거부하고 로그한다**
- provider가 이메일을 주지 않을 수 있다(**애플 비공개 릴레이**). **이메일을 필수로 만들지 않는다**
- 동일인 판정은 **`(provider, provider_uid)`** 다. **이메일로 계정을 병합하지 않는다** — 이메일은 provider마다 다를 수 있고 변경될 수 있다

**SPEC-07 §2.5**

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` | `/auth/csrf` | CSRF 토큰 |
| `GET` | `/auth/{provider}/authorize` | 카카오·네이버·애플 |
| `GET` | `/auth/{provider}/callback` | 세션 쿠키 발급 |
| `POST` | `/auth/logout` | 🔒 |
| `GET` | `/me` | 🔒 프로필 · 역할 |

**SPEC-08 §6 레이트 리밋**: `/auth/*/callback` = **10 req/min** (IP)

**`PRIN-T06`** — 외부 연동은 어댑터로 격리한다. 소셜 로그인(카카오·네이버·애플)을 **인터페이스 뒤에 두고 도메인이 벤더를 모르게** 한다. **벤더는 바뀐다**

**SPEC-05 §7**: `SocialAuthProvider` — 카카오 · 네이버 · 애플. **이메일 가입은 후순위**

**SPEC-06 §3.5**: `user(provider, provider_uid UNIQUE, display_name, email NULL 허용)`
**SPEC-08 §5.1 수집 항목**

| 항목 | 필수 | 왜 |
|---|---|---|
| `provider` + `provider_uid` | ✅ | 동일인 판정 |
| `display_name` | ✅ | 화면 표시 |
| `email` | — | 공지. **없어도 가입 가능** |

**수집하지 않는 것** — 생년월일(ADR-0004), 전화번호, 주소, 결제 정보

**ADR-0004**: 성인 인증 엔드포인트는 없다 (SPEC-07 §2.5)

## RED

### PKCE (SPEC-08 §4.2)

1. `authorize가_code_challenge를_보낸다` — PKCE
2. `callback이_code_verifier로_교환한다`
3. `code_verifier가_세션에_바인딩된다`
4. `PKCE_없는_흐름이_없다` — 순수 Authorization Code 경로 부재

### state (SPEC-08 §4.2)

5. `authorize가_state를_발급한다`
6. `callback이_state를_검증한다`
7. `state가_1회용이다` — 재사용 시 거부
8. `state_재사용_시도가_로그에_남는다` (SPEC-08 §4.2 "거부하고 로그한다")
9. `state가_10분_만료다`
10. `만료된_state는_거부된다`
11. `state_불일치는_거부된다`

### provider 3종 (`FR-USER-001`)

12. `kakao_로그인이_동작한다`
13. `naver_로그인이_동작한다`
14. `apple_로그인이_동작한다`
15. `그_외_provider는_404거나_400이다`
16. `provider별_어댑터가_인터페이스_뒤에_있다` (`PRIN-T06`) — 도메인이 벤더를 모른다

### 동일인 판정 (SPEC-08 §4.2)

17. `provider와_provider_uid_조합으로_식별한다`
18. `같은_조합_재로그인시_같은_계정이다`
19. `이메일이_같아도_provider가_다르면_다른_계정이다` — **병합하지 않는다**
20. `이메일_변경이_계정_식별에_영향을_주지_않는다`

### 이메일 선택 (SPEC-08 §4.2·§5.1)

21. `이메일_없이_가입이_완료된다` — 애플 비공개 릴레이
22. `이메일이_null인_계정이_정상_동작한다`
23. `이메일을_필수로_요구하지_않는다`

### 최초 로그인

24. `최초_로그인시_user_행이_생성된다`
25. `기본_역할이_member다` **결정** — SPEC에 명시 없음. `member` 부여
26. `display_name이_provider에서_온다`
27. `display_name이_없으면_어떻게_되는가` **결정** — NOT NULL이다. **provider 기본값 또는 생성 실패**

### 세션 (이슈 005 연계)

28. `callback_성공시_httpOnly_세션_쿠키가_발급된다`
29. `member_세션은_30일_rolling이다`
30. `logout이_세션을_무효화한다`

### 수집 금지 (ADR-0004, SPEC-08 §5.1)

31. `생년월일을_요청하지_않는다` — OAuth scope에 없음
32. `전화번호를_요청하지_않는다`
33. `성인인증_엔드포인트가_없다` (SPEC-07 §2.5)
34. `수집한_항목이_SPEC_08_§5_1_목록_내다` — scope 전수 대조

### 레이트 리밋 (SPEC-08 §6)

35. `callback이_10rpm으로_제한된다`

### 보안

36. `redirect_uri가_화이트리스트다` — 오픈 리다이렉트 방지
37. `provider_토큰이_저장되지_않는다` **결정** — 갱신 토큰 보관 여부. **저장 안 함**(필요 없다 — 세션이 우리 것)
38. `provider_응답이_로그에_남지_않는다` — 개인정보

## GREEN

### `user/oauth` — 어댑터 격리 (`PRIN-T06`)

```kotlin
interface SocialAuthProvider {                  // SPEC-05 §7
    val name: String                            // kakao · naver · apple
    fun authorizeUrl(state: String, codeChallenge: String): String
    fun exchange(code: String, codeVerifier: String): SocialProfile
}
data class SocialProfile(val providerUid: String, val displayName: String?, val email: String?)
```

**도메인이 `SocialProfile`만 안다.** 카카오 응답 JSON 구조가 `user` 도메인에 새지 않는다.

```kotlin
@Component class KakaoAuthProvider : SocialAuthProvider
@Component class NaverAuthProvider : SocialAuthProvider
@Component class AppleAuthProvider : SocialAuthProvider    // 비공개 릴레이 이메일 주의
```

### state·PKCE 저장

세션에 담는다(이슈 005의 Spring Session JDBC). 별도 테이블을 만들지 않는다 — 10분 만료라 세션 수명 안이다.

```kotlin
// 1회용 (RED 7): 검증 즉시 세션에서 제거
val stored = session.getAttribute(STATE_KEY) ?: reject()
session.removeAttribute(STATE_KEY)
```

### 동일인 판정 (RED 17~20)

```kotlin
// SPEC-08 §4.2 — 이메일로 병합하지 않는다
userRepository.findByProviderAndProviderUid(provider, profile.providerUid)
    ?: createUser(profile)
```

**이메일 조회로 기존 계정을 찾는 코드를 쓰지 않는다.** 편의상 넣고 싶어지는 지점이고, SPEC-08 §4.2가 명시적으로 금지했다.

### 애플 비공개 릴레이 (RED 21·22)

애플은 이메일을 주지 않거나 `@privaterelay.appleid.com` 을 준다. **`email`이 null이어도 전 경로가 동작해야 한다** — DB는 이미 nullable (이슈 005 RED 4).

### 수집 scope 최소화 (RED 31~34)

각 provider의 OAuth scope를 **SPEC-08 §5.1 목록으로 제한**한다. `birthday`·`phone_number` scope를 요청하지 않는다 — 요청하면 받게 되고, 받으면 저장하고 싶어진다.

**하지 말 것**:
- 이메일 가입 — 후순위 (`R-F5-3`)
- 회원 탈퇴 실제 구현 — 이슈 005 RED 23~26의 `@Disabled` 해제는 북마크(031)·이벤트(034) 이후
- 프로필 편집 — Phase 1a 범위 밖 **결정**

## DoD

- [ ] RED 38항 전부 통과
- [ ] **PKCE + 1회용 state 10분** (RED 1~11 — SPEC-08 §4.2)
- [ ] **이메일로 계정 병합하지 않음** (RED 19 — 코드 부재)
- [ ] 이메일 없이 가입 완료 (RED 21 — 애플)
- [ ] scope가 SPEC-08 §5.1 목록 내 (RED 34)
- [ ] `SocialAuthProvider` 어댑터로 벤더 격리 (RED 16 — `PRIN-T06`)
- [ ] 미결은 [`DECISIONS.md`](DECISIONS.md) §1 확정분을 따른다 — **이슈에서 판단하지 않는다**
- [ ] 커밋: `feat(user): 소셜 로그인 3종 OAuth PKCE (FR-USER-001, SPEC-08 §4.2, PRIN-T06)`
