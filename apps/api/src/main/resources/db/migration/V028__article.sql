-- ISSUE — 아티클을 코드에서 DB 로 옮긴다 (ADR-0011 · SPEC-06 §3.6 개정).
--
-- ## 스펙 §3.6 과 다르다 — 실제 라이브 구조를 따른다
--
-- 원래 스펙은 `type`(interview·guide·trend·photo_essay)·`body`·`cover_media_id`·
-- `sponsor_bar_id` 를 적었는데, 그중 미디어 테이블과 바(Phase 1b)는 아직 없고,
-- ADR-0010 으로 앞당겨 라이브가 된 143편은 다른 모양이다. 그 모양을 정본으로 삼는다:
--   · `category`(cocktail·bar·spirits) — 주제 축. 스펙의 형식 축(type)은 접는다 (G-49)
--   · `body`(JSONB) — 문단·소제목·인용·사진 블록 배열
--   · `hero`(정적 경로) — 미디어 테이블이 없어 문자열로 둔다
--   · `is_sponsored`(BOOLEAN) — 바가 없어 sponsor_bar_id 대신
--   · `dek`·`source_url` — 카드 요약과 이관 출처 (스펙에 없던 실제 필요)

CREATE TABLE article (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- 발행 후 불변 (PRIN-D02). 공개 경로가 된다.
    slug            VARCHAR(120) NOT NULL,
    category        VARCHAR(12)  NOT NULL,
    title           TEXT         NOT NULL,
    dek             TEXT         NOT NULL,

    -- 대표 사진. 미디어 테이블이 생기면 FK 로 바꾼다 (지금은 /articles/{slug}/… 경로).
    hero            TEXT         NOT NULL,
    -- 블로그 이관 출처. 직접 쓴 글은 없을 수 있다.
    source_url      TEXT,

    -- INV-CONTENT-02 — true 면 라벨을 끌 수 없다. 앱 레벨 강제 (NFR-L-02).
    is_sponsored    BOOLEAN      NOT NULL DEFAULT false,

    -- 본문 블록 배열. 종류는 paragraph·heading·quote·figure 넷 (types.ts ArticleBlock).
    -- 구조가 화면마다 바뀌지 않고 통째로 읽고 쓰므로 JSONB 한 컬럼이 조인보다 단순하다.
    body            JSONB        NOT NULL DEFAULT '[]',

    status          VARCHAR(12)  NOT NULL DEFAULT 'draft',
    published_at    TIMESTAMPTZ,

    CONSTRAINT uq_article__slug UNIQUE (slug),
    CONSTRAINT ck_article__category CHECK (category IN ('cocktail', 'bar', 'spirits')),
    CONSTRAINT ck_article__status CHECK (status IN ('draft', 'published', 'archived'))
);

CREATE TRIGGER article_set_updated_at BEFORE UPDATE ON article
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 발행분 목록·카테고리 조회. 최신 발행순이 기본이다.
CREATE INDEX ix_article__status_published ON article (status, published_at DESC);

-- ── 관련 칵테일 (SPEC-06 §3.6 article_related_cocktail) ──────────────────────
--
-- 상세의 "이 글의 칵테일" 링크. 배열이 아니라 조인인 이유는 cocktail(id) 로 FK 를
-- 걸어 없는 칵테일을 가리키지 못하게 하기 위해서다. position 으로 노출 순서를 정한다.
CREATE TABLE article_related_cocktail (
    article_id      BIGINT       NOT NULL REFERENCES article (id) ON DELETE CASCADE,
    cocktail_id     BIGINT       NOT NULL REFERENCES cocktail (id),
    position        SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (article_id, cocktail_id)
);

CREATE INDEX ix_article_related_cocktail__cocktail ON article_related_cocktail (cocktail_id);

-- ── SPEC-06 §4.1 · PRIN-D05 — 물리 삭제 금지 ────────────────────────────────
--
-- 칵테일과 같다. 삭제가 아니라 상태 전이(archived)다. SchemaLint 의 PROTECTED_TABLES
-- 가 article 을 감시한다. 관련 칵테일 조인은 편집 때 갈아끼우므로 DELETE 를 남긴다.
REVOKE DELETE ON article FROM mut_app;
