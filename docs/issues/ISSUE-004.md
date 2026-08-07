---
id: ISSUE-004
title: OpenAPI → TS 타입 생성 파이프라인
domain: —
layer: contract
wave: 0
status: TODO
depends_on: [ISSUE-003]
fr: []
r: []
inv: []
nfr: []
migration: —
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/common/openapi/**
  - packages/domain/src/generated/**
  - packages/domain/package.json
  - scripts/generate-types.*
---

## 근거

**`PRIN-T02` — 계약이 정본이다 (Contract-First).** 이 저장소에서 가장 중요한 규칙이다.

> **언어가 둘이라 분류 축과 DTO가 두 곳에 존재한다. 손으로 양쪽을 맞추면 반드시 어긋난다.**
>
> - **OpenAPI 스펙이 단일 진실 공급원이다.** Spring이 생성하고, 프론트의 TS 타입은 거기서 뽑는다
> - 손으로 쓴 TS DTO를 두지 않는다. **생성물은 커밋하되 손으로 고치지 않는다**
> - 분류 축 enum(`BaseSpirit` · `StyleKey` · `FlavorKey` · `SweetLevel` · `Technique`)의 정본은 **Kotlin 쪽**이다.
>   현재 `packages/domain/src/types.ts`는 프로토타입 산물이며, API 연동 시점에 생성물로 대체한다
> - **계약이 깨지면 빌드가 깨져야 한다. 런타임에 발견하지 않는다**

**SPEC-07 서두**: "이 문서는 계약의 설명이지 계약 자체가 아니다. 정본은 Spring이 생성하는 OpenAPI 스펙이고, 프론트 TS 타입은 거기서 뽑는다. **여기와 OpenAPI가 다르면 OpenAPI가 맞다.**"

**SPEC-01 §6**: `packages/domain/src/types.ts` → OpenAPI 생성 타입으로 **대체**

### 현재 상태 (프로토타입)

`packages/domain/src/types.ts`가 손으로 쓴 정본이다. 특히 주의할 지점:

```ts
export type BaseSpirit = "진" | "보드카" | "위스키" | ... ;   // 한국어 값
export const BASE_SLUGS: Record<BaseSpirit, string> = { 진: "gin", ... };
```

**한국어 리터럴이 타입 값이고 슬러그는 별도 맵**이다. SPEC-06 §3.1은 `base_spirit VARCHAR(24)`에 슬러그(`gin`·`vodka`·`korean`·`non-alcoholic`)를 넣는다. **Kotlin enum이 정본이 되면 값은 슬러그로 통일되고 한국어는 표시용 레이블로 내려간다.**

이 전환이 이슈 037의 실제 작업이고, 이 이슈는 그 **파이프라인**을 만든다.

## RED

### 생성 (`PRIN-T02`)

1. `Gradle_태스크로_openapi_json이_생성된다` — `./gradlew generateOpenApiDocs` 상당
2. `생성된_스펙이_저장소에_커밋된다` — `apps/api/openapi.json` 또는 합의된 경로
3. `npm_스크립트로_TS_타입이_생성된다` — `npm run generate:types`
4. `생성물이_packages_domain_src_generated에_들어간다`
5. `생성물에_손대지_말라는_헤더가_붙는다` — `/* AUTO-GENERATED — DO NOT EDIT */`

### 계약 드리프트 감지 (`PRIN-T02` "계약이 깨지면 빌드가 깨져야 한다")

6. `Kotlin_DTO를_바꾸면_생성된_TS가_바뀐다` — 필드 추가 → 재생성 → diff 발생
7. `생성물이_최신이_아니면_CI가_실패한다` — 재생성 후 `git diff --exit-code`. **이게 이 이슈의 핵심**
8. `손으로_수정한_생성물은_CI에서_되돌려진다` — 7과 같은 장치
9. `TS_컴파일이_생성_타입으로_통과한다`

### enum 정본 (`PRIN-T02`)

10. `분류축_5종_enum이_Kotlin에_정의돼_있다` — `BaseSpirit`·`StyleKey`·`FlavorKey`·`SweetLevel`·`Technique`
11. `생성된_TS에_같은_5종이_나온다`
12. `Kotlin_enum_값이_슬러그다` — `GIN("gin")` 형태. 한국어 레이블은 별도 필드
13. `enum에_값을_추가하면_TS에도_반영된다`
14. `ADR_0002의_슬러그_확정값과_일치한다` — `korean`(not `soju`) · `non-alcoholic` · `agave` 등 전수 대조

### 프로토타입과의 정합 (전환 준비)

15. `기존_types_ts의_BASE_SLUGS와_Kotlin_enum_슬러그가_일치한다` — 불일치 시 실패. **전환(037) 전에 어긋남을 먼저 잡는다**
16. `StyleKey_9종이_일치한다` — `highball`·`sour`·`spirit-forward`·`spritz`·`tiki`·`creamy`·`hot`·`frozen`·`shot`
17. `FlavorKey_10종이_일치한다`
18. `Technique_5종이_일치한다` — `Build`·`Shake`·`Stir`·`Blend`·`Etc`

## GREEN

### 백엔드 — springdoc-openapi

```kotlin
// build.gradle.kts
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui")
// + org.springdoc.openapi-gradle-plugin 으로 빌드 시 JSON 추출
```

### 프론트 — openapi-typescript

```json
// package.json
"scripts": {
  "generate:types": "openapi-typescript apps/api/openapi.json -o packages/domain/src/generated/api.ts"
}
```

`openapi-typescript`가 타입만 뽑는다(런타임 클라이언트 없음). 클라이언트가 필요하면 `openapi-fetch`를 별도로 — **지금은 타입만**.

### 분류 축 enum (Kotlin이 정본)

```kotlin
enum class BaseSpirit(val slug: String, val labelKo: String) {
    GIN("gin", "진"),
    VODKA("vodka", "보드카"),
    ...
    KOREAN("korean", "전통주"),          // ADR-0002: soju → korean
    NON_ALCOHOLIC("non-alcoholic", "무알콜");
}
```

**슬러그가 값이고 한국어는 레이블이다.** 프로토타입과 반대다 — 그래서 RED 15~18이 전환 전에 어긋남을 잡는다.

### CI 드리프트 게이트 (RED 7 — 가장 중요)

```yaml
- run: ./gradlew generateOpenApiDocs
- run: npm run generate:types
- run: git diff --exit-code   # 생성물이 최신이 아니면 실패
```

이게 없으면 `PRIN-T02`가 문서상 규칙으로만 남는다. **계약 드리프트는 조용히 쌓이다가 런타임에 터진다.**

### 생성물 커밋 정책

`PRIN-T02`가 "생성물은 커밋하되 손으로 고치지 않는다"고 했다. `.gitignore`에 넣지 않는다 — 프론트 개발자가 백엔드 빌드 없이 타입을 볼 수 있어야 한다.

**하지 말 것**:
- `types.ts` 실제 교체 — 이슈 037 (엔드포인트가 아직 없어 생성할 DTO가 없다)
- 런타임 API 클라이언트 — 타입만

## DoD

- [ ] RED 18항 전부 통과
- [ ] **CI 드리프트 게이트 동작** (RED 7) — 생성물이 낡으면 빌드 실패
- [ ] Kotlin enum 5종이 ADR-0002 확정 슬러그와 일치 (RED 14)
- [ ] 프로토타입 `types.ts`와의 불일치가 0건이거나, 있으면 `GAPS.md` 등재
- [ ] 생성물에 `DO NOT EDIT` 헤더
- [ ] 커밋: `feat(contract): OpenAPI → TS 타입 생성 파이프라인 (PRIN-T02)`
