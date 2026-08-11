-- ISSUE-009 — 칵테일과 분류 3축 (SPEC-06 §3.1·§4.2, FR-COCKTAIL-001·002·008)
--
-- PRIN-P06 — 카테고리와 필터는 다른 것이다.
--   카테고리(기주·스타일·메이킹)는 경로가 되고 색인한다. 모든 칵테일이 반드시 하나씩 갖는다.
--   필터(당도·도수·향)는 쿼리스트링이고 색인하지 않는다.
--
-- 배열이 아니라 조인 테이블인 이유는 셋이다 (SPEC-06 §1.4).
--   · 카테고리 페이지 조회가 단순 조인이 된다
--   · 패싯 카운트가 GROUP BY 한 방으로 끝난다. 배열이면 unnest 를 거친다
--   · style_primary ∈ styles 를 **복합 FK 로 DB 가 강제**할 수 있다  ← 가장 큰 이유

CREATE TABLE cocktail (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- 발행 후 불변 (INV-COCKTAIL-05 · PRIN-D02). 상태 전이는 이슈 014 가 강제한다.
    slug            VARCHAR(120) NOT NULL,
    name_ko         VARCHAR(120) NOT NULL,
    name_en         VARCHAR(120) NOT NULL,
    aliases         TEXT[]       NOT NULL DEFAULT '{}',
    summary         TEXT         NOT NULL,

    -- ── 분류 3축. 전부 NOT NULL (INV-COCKTAIL-01 · R-C-1) ──────────────────
    base_spirit     VARCHAR(24)  NOT NULL,
    style_primary   VARCHAR(24)  NOT NULL,
    method          VARCHAR(12)  NOT NULL,

    -- ── 필터 축 ────────────────────────────────────────────────────────────
    sweetness       VARCHAR(12)  NOT NULL,

    abv_calculated  NUMERIC(4,1),
    abv_override    NUMERIC(4,1),
    -- 표시값 하나로 고정한다. 조회·필터가 매번 COALESCE 를 쓰면 인덱스가 안 붙는다.
    -- 공개 응답에도 이 컬럼만 나간다 (SPEC-07 §5) — 계산인지 수동인지는 내부 사정이다.
    abv             NUMERIC(4,1) GENERATED ALWAYS AS (COALESCE(abv_override, abv_calculated)) STORED,

    glass_type      VARCHAR(40)  NOT NULL,
    prep_time_min   SMALLINT,

    tasting_note    TEXT,        -- 발행 시 필수 (GATE-COCKTAIL-01, 이슈 013)
    story           TEXT,        -- 클래식이면 발행 시 필수 (GATE-COCKTAIL-05)
    is_classic      BOOLEAN      NOT NULL DEFAULT false,

    origin_year     VARCHAR(80),
    origin_place    VARCHAR(80),
    origin_creator  VARCHAR(80),

    status          VARCHAR(12)  NOT NULL DEFAULT 'draft',
    published_at    TIMESTAMPTZ,

    -- 5축 표시 전용 배열. 고정 길이라 조인 테이블로 쪼개지 않는다 (SPEC-06 §1.4 예외).
    flavor_profile  SMALLINT[5],

    CONSTRAINT uq_cocktail__slug UNIQUE (slug),

    -- ADR-0002 확정 슬러그. soju → korean, 데킬라 → agave.
    CONSTRAINT ck_cocktail__base_spirit CHECK (base_spirit IN (
        'gin', 'vodka', 'whisky', 'rum', 'agave',
        'brandy', 'liqueur', 'wine', 'korean', 'non-alcoholic')),

    -- 슬러그다. 이슈 본문의 SQL 은 PascalCase('Build')였으나 G-23 이 슬러그로 확정했다 —
    -- SPEC-06 §3.1 의 이 컬럼이 슬러그를 저장하고, 계약(OpenAPI)도 슬러그다.
    -- types.ts 의 PascalCase 는 프로토타입 산물이고 이슈 037 이 전환한다.
    CONSTRAINT ck_cocktail__method CHECK (method IN ('build', 'shake', 'stir', 'blend', 'etc')),

    CONSTRAINT ck_cocktail__sweetness CHECK (sweetness IN ('dry', 'semi_dry', 'semi_sweet', 'sweet')),

    CONSTRAINT ck_cocktail__status CHECK (status IN ('draft', 'published', 'archived')),

    -- INV-COCKTAIL-06 — 무알콜 ⟺ abv = 0. **양방향이다.**
    --
    -- ⚠️ abv 가 생성 컬럼이라 abv_calculated·abv_override 가 둘 다 NULL 이면 abv 도 NULL 이고,
    --    CHECK 는 (x) = (NULL) → NULL 이라 **통과한다.**
    --    draft 단계에서는 도수가 아직 없을 수 있으므로 의도된 동작이지만,
    --    발행 게이트(이슈 013)가 다시 확인해야 한다. 여기서 NOT NULL 로 막으면
    --    레시피를 다 쓰기 전에는 칵테일 행을 만들 수조차 없다.
    CONSTRAINT ck_cocktail__non_alcoholic CHECK ((base_spirit = 'non-alcoholic') = (abv = 0))
);

CREATE TRIGGER cocktail_set_updated_at BEFORE UPDATE ON cocktail
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 카테고리 페이지 (SPEC-06 §5).
CREATE INDEX ix_cocktail__status_base ON cocktail (status, base_spirit);
CREATE INDEX ix_cocktail__aliases ON cocktail USING GIN (aliases);

COMMENT ON COLUMN cocktail.abv IS
    'SPEC-07 §5 — 공개 응답에는 이 값 하나만 나간다. 계산인지 수동인지는 내부 사정이다.';

-- ── 축 2 · 스타일 (복수) ────────────────────────────────────────────────────

CREATE TABLE cocktail_style (
    cocktail_id BIGINT      NOT NULL REFERENCES cocktail (id) ON DELETE CASCADE,
    style       VARCHAR(24) NOT NULL,

    -- 복합 FK 의 참조 대상이다 (SPEC-06 §4.2).
    --
    -- §4.2 는 PK 와 별도로 `UNIQUE (cocktail_id, style)` 을 걸었지만 같은 컬럼이라 불필요하다 —
    -- **PK 자체가 유효한 FK 참조 대상**이다. 둘 다 걸면 Postgres 가 중복 인덱스를 PK 로 합치고,
    -- 그 결과 PK 이름이 `uq_` 로 남아 읽는 사람을 헷갈리게 한다.
    CONSTRAINT pk_cocktail_style PRIMARY KEY (cocktail_id, style),

    CONSTRAINT ck_cocktail_style__style CHECK (style IN (
        'highball', 'sour', 'spirit-forward', 'spritz', 'tiki',
        'creamy', 'hot', 'frozen', 'shot'))
);

CREATE INDEX ix_cocktail_style__style ON cocktail_style (style, cocktail_id);

-- ── 향·맛 태그 (필터 축 — 카테고리가 아니다) ─────────────────────────────────

CREATE TABLE cocktail_aroma_tag (
    cocktail_id BIGINT      NOT NULL REFERENCES cocktail (id) ON DELETE CASCADE,
    aroma_tag   VARCHAR(24) NOT NULL,

    PRIMARY KEY (cocktail_id, aroma_tag),

    CONSTRAINT ck_cocktail_aroma_tag__tag CHECK (aroma_tag IN (
        'citrus', 'sour', 'fruity', 'floral', 'herbal',
        'spicy', 'smoky', 'bitter', 'nutty', 'creamy'))
);

CREATE INDEX ix_cocktail_aroma_tag__tag ON cocktail_aroma_tag (aroma_tag, cocktail_id);

-- ── INV-COCKTAIL-03 — style_primary ∈ styles ────────────────────────────────
--
-- 조인 테이블을 택한 가장 큰 이유가 이것이다. 배열이었으면 앱에서만 막을 수 있었다.
--
-- DEFERRABLE INITIALLY DEFERRED 가 필수다. 칵테일 행과 스타일 행이 같은 트랜잭션에서
-- 삽입되는데, 즉시 검사하면 칵테일을 넣는 순간 아직 없는 스타일 행을 가리키게 된다.
-- 커밋 시점에 한 번 보므로 순서를 신경 쓰지 않아도 된다.
ALTER TABLE cocktail
    ADD CONSTRAINT fk_cocktail__style_primary
    FOREIGN KEY (id, style_primary) REFERENCES cocktail_style (cocktail_id, style)
    DEFERRABLE INITIALLY DEFERRED;

-- ── SPEC-06 §4.1 · PRIN-D05 — 물리 삭제 금지 ────────────────────────────────
--
-- 삭제가 아니라 상태 전이다 (archived). 앱 역할에서 권한 자체를 회수해
-- 실수로도 지울 수 없게 한다 (PRIN-T05).
-- SchemaLint 의 PROTECTED_TABLES 가 이것을 감시한다.
REVOKE DELETE ON cocktail FROM kcocktail_app;
