# 이슈 작업 규약

> 멀티 세션 AI 개발의 전제: **각 세션은 자기 이슈 하나와 [SPEC-00](../spec/SPEC-00_개발원칙.md)만 읽고도 작업할 수 있어야 한다.**
> 이 문서는 그 작업이 서로를 밟지 않게 하는 규칙이다. 이슈를 집기 전에 한 번 읽는다.

---

## 0. 이슈는 GitHub 에 있다

**정본은 [GitHub Issues](https://github.com/cacaocoffee/k-cocktail-archive/issues) 다.** 저장소 안의 `ISSUE-*.md` 는 이관 후 지웠다 —
상태를 두 곳에 적으면 반드시 어긋나기 때문이다. [`INDEX.md`](INDEX.md) 는 웨이브 편성·의존 DAG·결합점만 남긴 **지도**다.

```bash
gh issue list  --label wave-0 --state open       # 착수 가능한 것 찾기
gh issue view  15                                 # 이슈 본문 (= 근거·RED·GREEN·DoD)
gh issue view  15 --json body --jq .body > /tmp/issue.md
```

**번호 대응은 ISSUE-050(#52)까지만 `+2` 다.** `ISSUE-A00` 만 `#1`.
초기 일괄 등록 이후에는 PR 이 같은 번호대를 먹으므로 규칙이 성립하지 않는다 —
`ISSUE-051` 은 `#53` 이 아니라 **`#69`** 다. 새 이슈는 **제목의 `[NNN]` 으로 찾는다.**

```bash
gh issue list --search "in:title [051]"
```
진행률은 [마일스톤](https://github.com/cacaocoffee/k-cocktail-archive/milestones)이 웨이브별로 보여준다.

---

## 1. 작업 흐름

```
GitHub Issues 에서 이슈 선택      열려 있고 + 의존 이슈가 전부 닫힘
        ↓
gh issue edit N --add-label status:in-progress    ← 중복 착수 방지. 제일 먼저
        ↓
브랜치 생성  feat/ISSUE-012-publish-gate
        ↓
RED   이슈의 "RED" 목록을 테스트로 옮긴다 → 실패 확인
        ↓
GREEN 최소 구현 → 통과
        ↓
PR 열기  본문에 "Closes #14"  → CI 초록 → 머지 (이슈가 자동으로 닫힌다)
```

**중복 착수 방지가 최우선이다.** 이슈를 집는 순간 `status:in-progress` 라벨부터 붙인다.

| 상태 | 표현 |
|---|---|
| 착수 가능 | 열려 있고 라벨 없음 |
| 작업 중 | `status:in-progress` — **집지 않는다** |
| 리뷰 대기 | `status:review` (PR 열림) |
| 완료 | **닫힘** — 머지가 닫는다 |
| 막힘 | `blocked` + 본문에 G-번호 |

---

## 2. 이 저장소가 특이한 점

### 2.1 그린필드가 아니다

`apps/web`에 **동작하는 프로토타입**이 있다 (SPEC-01 §6). 칵테일 24종이 TS 정적 배열에 있고 3개 화면이 돈다.

| 지금 | 이후 | 담당 이슈 |
|---|---|---|
| `packages/domain/src/data.ts` 24종 | Postgres 시드 (`R__seed_*.sql`) | 034 |
| `packages/domain/src/types.ts` | **OpenAPI 생성 타입** (`PRIN-T02`) | 035 |
| `packages/domain/src/validate.ts` | Kotlin 이관 — 발행 게이트는 서버 강제 (`PRIN-T05`) | 012 |
| `packages/domain/src/search.ts` 필터 | 클라이언트 유지, 패싯 카운트만 서버 응답 | 038 |
| `packages/ui` | **그대로 유지** | — |

**프로토타입을 지우고 새로 쓰지 않는다.** 위 표의 경로대로 대체한다.

### 2.2 언어가 둘이다

`apps/api`(Kotlin) + `apps/web`(TypeScript). `PRIN-T02`가 이 저장소의 가장 중요한 규칙이다.

- **OpenAPI 스펙이 단일 진실 공급원.** Spring이 생성하고 프론트 TS 타입은 거기서 뽑는다
- **손으로 쓴 TS DTO를 두지 않는다.** 생성물은 커밋하되 손으로 고치지 않는다
- 분류 축 enum(`BaseSpirit`·`StyleKey`·`FlavorKey`·`SweetLevel`·`Technique`)의 **정본은 Kotlin**
- **계약이 깨지면 빌드가 깨져야 한다.** 런타임에 발견하지 않는다

### 2.3 npm workspaces는 `apps/api`를 모른다

SPEC-05 §2: `npm workspaces`는 `apps/web`과 `packages/*`만 관리하고 `apps/api`는 Gradle이 독립 관리한다. 같은 저장소에 나란히 둘 뿐이다.

---

## 3. TDD 규약

### 3.1 RED 는 "의도한 이유로" 실패해야 한다

컴파일 에러는 RED가 아니다. 시그니처를 먼저 만들어(본문은 `TODO()`) **단언 실패**로 떨어지는 것을 확인한 뒤 구현한다.

이슈의 RED 목록은 그대로 테스트 함수 이름이 된다. 항목을 줄이지 않는다.

### 3.2 테스트 층위

| 무엇 | 층위 | 이유 |
|---|---|---|
| 불변식(`INV-*`), 발행 게이트(`GATE-*`), 도수 계산, 패싯 카운트 | **단위** (DB 없음) | 전수 검증이 가능해야 한다 |
| DB 제약(`CHECK`·부분 유니크·복합 FK), `REVOKE DELETE` | **Testcontainers** (PG16) | 앱이 아니라 DB가 막는 것들 |
| 권한 매트릭스, IDOR 404, CSRF | **통합** | SPEC-08 §2 표가 곧 테스트 데이터 |
| API 계약(Problem Details·`violations`·페이징) | `@WebMvcTest` 또는 통합 | SPEC-07 §1이 정본 |
| 화면 | Playwright 또는 RTL | SCREENS 문서가 정본 |

### 3.3 반드시 테스트로 고정할 것 (전 이슈 공통)

이슈별 RED에 없더라도, 해당 이슈가 테이블·엔드포인트를 만들면 항상 테스트한다.

- **`draft`·`archived` 리소스는 공개 API에서 404** (SPEC-07 §5 — 존재를 흘리지 않는다)
- **공개 응답에 내부 `id`가 없다** (`slug`만 — SPEC-07 §1.1)
- **공개 응답에 `abv_calculated`/`abv_override` 구분이 없다** (표시값 `abv` 하나 — SPEC-07 §5)
- 상태 변경 요청에 **CSRF 토큰 요구** (`/events` 제외 — SPEC-08 §4.3)

### 3.4 `npm run check`의 서버판

현재 `packages/domain/check.ts`가 코퍼스 불변식을 강제한다 (`R-C-1`·`R-C-3`·`R-F1.2-1`).
**이것을 없애지 않는다.** SPEC-06 §4.3이 "앱 강제 항목은 배치 검증으로 이중 확인한다"고 했고, `NFR-D-01`이 배포 차단 조건이다. 이슈 013이 서버판을 만든다.

---

## 4. 병렬 충돌 방지 — 네 가지 규칙

| 충돌원 | 규칙 |
|---|---|
| **Flyway 마이그레이션** | 이슈 번호 = 마이그레이션 번호. `V012__publish_gate.sql`. 여러 개면 `V012_1`, `V012_2`. **즉흥으로 붙이지 않는다** — frontmatter의 `migration:` 이 예약 번호 |
| **OpenAPI 생성물** | `packages/domain/src/generated/**` 은 **누구도 손으로 고치지 않는다.** 생성 스크립트만 건드린다 (이슈 004 소유) |
| **모듈 경계** | `owns:` 밖은 읽기만. 타 모듈은 공개 인터페이스(`XxxFacade`)로만 호출 (`PRIN-T03`). 리포지토리·엔티티 직접 참조는 경계 테스트가 막는다 |
| **`packages/ui`** | 파일마다 다르다 — 아래 표 (`ADR-0005`). 한 줄로 요약하면 **`styles.css`는 절차를 밟을 때만, `app.css`는 일반 코드와 같다** |

#### `packages/ui` 세 층 ([ADR-0005](../decisions/ADR-0005-ui-package-scope.md))

| 무엇 | 성격 | 고칠 수 있나 |
|---|---|---|
| `docs/design/source/**` | 시안 원본 (Claude Design 내보내기) | **아니오.** 읽기 전용 |
| `packages/ui/styles.css` | 시안 정본 — 토큰 · 컴포넌트 클래스 | **GAPS 등재 → ADR → 되돌리는 조건**, 3단을 다 밟을 때만 |
| `packages/ui/app.css` | 우리가 쓴 화면 레이아웃 · 반응형 층 | **예.** 일반 코드와 같다 |

`app.css`가 시안 밖인 근거는 ADR-0001 결정 2다 — 시안에 없는 모션 값을 거기 두면서
"시안 정본을 원본과 어긋나게 두지 않기 위해서"라고 적었다.

대비 문제(`NFR-A`)를 `styles.css`에서 발견하면 여전히 GAPS로 간다 (예: `.btn-primary` 3.76:1 → [이슈 050](https://github.com/cacaocoffee/k-cocktail-archive/issues/52)).
**절차가 열렸다는 뜻이지 프리패스가 아니다.**

### 4.1 `PRIN-P02` — 만들면 안 되는 것

**노출 규칙(부스팅 한도·홈 슬롯 비율)에 DB 컬럼도 API 엔드포인트도 어드민 입력란도 만들지 않는다.**
Phase 1a에는 PARTNER가 없지만, 관련 코드를 스치는 이슈는 이 금지를 테스트로 남긴다 (이슈 027).

---

## 5. 브랜치 · 커밋

- **브랜치**: `feat/ISSUE-012-publish-gate` — 이슈 하나에 브랜치 하나
- **커밋**: `feat(cocktail): 발행 게이트 6종 서버 강제 (FR-COCKTAIL-010, GATE-COCKTAIL-01~06)`
  - 타입: `feat` `fix` `test` `refactor` `docs` `chore`
  - 스코프: 도메인 코드 소문자 (`cocktail`·`ingredient`·`search`·`user`·`admin`·`web`·`api`)
  - **FR ID 또는 R-ID 없는 기능 커밋은 거부**. `chore`·`docs`는 예외
- **TDD 커밋 분리 권장**: `test(...)` RED → `feat(...)` GREEN
- Claude/Anthropic 어트리뷰션 트레일러·푸터는 넣지 않는다

### 5.1 PR

`main` 에 직접 커밋하지 않는다. 이슈 하나 = 브랜치 하나 = PR 하나.

```bash
gh pr create --title "feat(cocktail): 발행 게이트 6종 (ISSUE-013)" --body "$(cat <<'EOF'
Closes #15

## RED → GREEN
| RED | 결과 |
|---|---|
| 1. … | ✅ |

## 확인
- `./gradlew check` 초록
- `npm run verify` 초록

## DoD 미달 항목
(없으면 "없음". 있으면 무엇을 왜 남겼는지)
EOF
)"
```

- **`Closes #N` 을 반드시 넣는다** — 머지가 이슈를 닫는다. 손으로 닫지 않는다
- **CI 가 초록이 아니면 머지하지 않는다.** `apps/api/**` 는 `api.yml`, 프론트는 각자의 워크플로
- 머지는 `--squash` 하지 않는다 — TDD 의 RED→GREEN 커밋 분리가 기록으로 남아야 한다

---

## 5.5 미결은 [`DECISIONS.md`](DECISIONS.md) 한 곳에 있다

이슈 47개를 쓰며 나온 미결 **171건**을 셋으로 나눠 놨다. **이슈 본문에서 판단하지 않는다.**

| 표시 | 뜻 | 세션이 할 일 |
|---|---|---|
| 이슈에 **"보수적으로 X"** | [DECISIONS §1](DECISIONS.md) 확정 | **멈추지 않고 그대로 구현** |
| 이슈에 **⚖️** | [DECISIONS §2](DECISIONS.md) 대기 | **`BLOCKED`.** D-번호를 `INDEX.md`에 적고 보고 |
| DECISIONS §3 | 착수 후 판단 | 만들며 정하고 **§1로 옮겨 기록** |

**새 미결이 나오면 이슈가 아니라 `DECISIONS.md`에 적는다.** 이슈에 흩뿌리면 다시 171건이 된다.

## 6. 막혔을 때 — `GAPS.md`에 올린다

**SPEC-00 §4가 이 저장소의 규약이다.**

> 원칙은 지키라고 있지만, 지킬 수 없는 상황이 온다. 그때는:
> 1. 어긴다는 사실을 `GAPS.md`에 올린다
> 2. 왜 어겼는지 ADR로 남긴다
> 3. 되돌리는 조건을 함께 적는다
>
> **조용히 어기지 않는다.** 그게 유일한 금지 사항이다.

별도의 `OPEN-ISSUES.md`를 만들지 않는다 — [`docs/prd/GAPS.md`](../prd/GAPS.md)가 이미 그 자리다.

| 상황 | 할 일 |
|---|---|
| 스펙에 답이 없다 (추측이 필요하다) | **구현 중단.** `GAPS.md` 등재 + 보고 |
| 스펙 두 곳이 충돌한다 | SPEC-00이 최상위 (§9). 그 아래끼리면 `GAPS.md` |
| 원칙을 어겨야 한다 | GAPS 등재 → **ADR 작성** → 되돌리는 조건 명시 |
| `packages/ui/styles.css` 를 고쳐야 한다 | GAPS 등재 → ADR → 되돌리는 조건. 3단을 다 밟는다 (§4). 예: `NFR-A` §2.4 `.btn-primary` |
| `packages/ui/app.css` 를 고쳐야 한다 | 그냥 고친다. 시안 정본이 아니다 ([ADR-0005](../decisions/ADR-0005-ui-package-scope.md)) |

현재 미결 2건은 [`GAPS.md`](../prd/GAPS.md) 참조 — **호스팅·이미지 저장소(G-07)** 와 **사업 결정(G-17)**. 둘 다 문서로 풀 수 없다.

---

## 7. 이슈 본문 형식

GitHub 이슈 본문은 아래 순서로 돼 있다. **새 이슈도 이 형식을 따른다.**

```markdown
| | |          ← 메타 표: wave · layer · domain · 의존(#번호) · migration · FR/R/INV/NFR
<details>owns</details>   ← 이 이슈만 만지는 경로

## 근거          ← 인용. 스펙을 다시 열지 않아도 되게
## RED           ← 먼저 실패시킬 테스트 (번호 = 테스트 함수)
## GREEN         ← 구현 범위와 경계 (하지 말 것 포함)
## DoD           ← 체크박스
```

메타는 라벨로도 붙는다 — `wave-N` · `layer:*` · `domain:*` · `status:*` · `blocked`.
**`migration:` 은 예약된 Flyway 번호다** (§4). 즉흥으로 붙이지 않는다.

---

## 8. 세션 시작 시 확인 (재개·컴팩션 후 필수)

```bash
git status --short --branch      # 어느 브랜치인가, 잔여 변경이 있는가
gh issue view <N>                # 내 이슈가 아직 status:in-progress 인가
gh pr status                     # 내 PR 이 이미 열려 있는가
```

컴팩션이나 세션 재개 후 **바로 편집하지 않는다.** 브랜치와 이슈 상태를 먼저 확인한다.

---

## 9. 검증 명령

```bash
# 프론트 (현재)
npm run check      # 코퍼스 불변식 — R-C-1 · R-C-3 · R-F1.2-1
npm run verify     # check → lint → build
npm run dev        # localhost:3000

# 백엔드 (이슈 000 이후)
cd apps/api
./gradlew test          # 단위 + 통합 (Testcontainers PG16)
./gradlew boundaryTest  # 모듈 경계 (PRIN-T03)
./gradlew check         # 전체
```
