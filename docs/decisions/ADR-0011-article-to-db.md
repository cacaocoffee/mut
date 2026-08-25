# ADR-0011 — 아티클을 코드에서 DB 로 옮기고 어드민에서 편집한다

2026-08-25 · 채택

## 맥락

아티클 143편은 지금 `packages/domain/src/articles/*.ts` 코드 파일로 있다 (ADR-0010 이
읽기 화면만 앞당겼다). 그래서 새 글·수정은 **코드 커밋 → 배포**로만 되고, 운영자가
사이트에서 직접 쓸 수 없다. DB 로 옮기고 어드민 편집을 붙인다 — 칵테일과 같은 구조로.

## 스펙(SPEC-06 §3.6)과 어긋난 것을 먼저 정한다 (G-49)

스펙의 `article` 테이블은 아직 없는 것들에 얽혀 있고, 라이브가 된 143편은 다른 모양이다.
**라이브 구조를 정본으로 삼고 스펙을 갱신한다** (코드가 아니라 문서를 먼저 고친다):

| 스펙 §3.6 | 채택 | 왜 |
|---|---|---|
| `type` (interview·guide·trend·photo_essay) | `category` (cocktail·bar·spirits) | 주제 축이 라이브고 운영자가 쓰는 것. 형식 축은 접는다 (G-49 해소) |
| `body` (텍스트) | `body` (JSONB 블록) | 문단·소제목·인용·사진 구조를 그대로 담는다 |
| `cover_media_id` (미디어 FK) | `hero` (경로 문자열) | 미디어 테이블이 없다. 생기면 FK 로 바꾼다 |
| `sponsor_bar_id` (바 FK) | `is_sponsored` (불리언) | 바는 Phase 1b. 라벨 강제(NFR-L-02)에는 불리언으로 충분 |
| — | `dek`·`source_url` 추가 | 카드 요약·이관 출처. 실제 필요인데 스펙에 없었다 |

`status`·`published_at`·`slug`·`is_sponsored`·상태 전이(draft→published→archived)는
칵테일과 **같은 규약**을 쓴다 — 어드민·발행 게이트·감사 로그가 한 벌로 돈다.

## 결정

1. **DB** — `article`(body JSONB) + `article_related_cocktail` 조인 테이블 (V028).
   물리 삭제 금지(PRIN-D05), 삭제는 archived 전이. 칵테일 삭제(초안 버리기, ADR 없음)와 같다.
2. **시드** — 코드의 143편을 DB 로 이관한다 (repeatable 시드). 이관 뒤 코드의 `ARTICLES`
   배열은 걷어내고 화면은 API 에서 읽는다.
3. **API** — 공개(`GET /articles`·`/articles/{slug}`) + 어드민(목록·저장·발행·삭제).
   칵테일 어드민 컨트롤러·전이 서비스를 본보기로 한다.
4. **어드민 편집** — 전체 블록 편집기(문단·소제목·인용·사진을 줄 단위로 추가·삭제·순서변경).
5. **검색·계측** — 발행 시 `search_document` 동기화(V017 이 이미 article 을 허용), `article_view` 는 이미 있다.

## 되돌리기

블록 편집기가 크다. 단계로 나눈다 — DB·시드·API 가 서면 화면은 이미 그 데이터를 읽으므로,
편집기는 마지막에 붙인다. 각 단계가 독립 PR 이라 중간에서 멈춰도 사이트는 돈다.

## 남는 것

- 미디어 테이블이 생기면 `hero` → `cover_media_id`.
- 큐레이션 리스트(SPEC-06 §3.6 후반)는 이 ADR 밖이다 — 여전히 Phase 2.
