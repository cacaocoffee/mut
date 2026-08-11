-- ISSUE-010 — 레시피 · 재료 · 스텝 (SPEC-06 §3.1, FR-COCKTAIL-003·004·005)
--
-- PRIN-D03 — Cocktail 과 Recipe 를 분리한다.
--   칵테일 하나에 **에디터 표준 1개 + 제휴 바 버전 n개**가 공존해야 한다 (R-F1.1-7).
--   그게 파트너 상품의 핵심이고, 나중에 분리하면 마이그레이션 비용이 크다.
--
-- PRIN-D01 — 재료는 참조지 문자열이 아니다.
--   프리텍스트 컬럼을 두지 않는다. **있으면 반드시 쓰인다.**

CREATE TABLE recipe (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 애그리게이트 경계 = 트랜잭션 경계 (SPEC-02 §1). 칵테일 없는 레시피는 없다.
    cocktail_id     BIGINT      NOT NULL REFERENCES cocktail (id) ON DELETE CASCADE,

    version_type    VARCHAR(16) NOT NULL,

    -- Phase 1b 에 bar 테이블이 생기면 FK 를 건다. 지금 걸 대상이 없다.
    author_bar_id   BIGINT,
    author_user_id  BIGINT      REFERENCES "user" (id) ON DELETE SET NULL,

    serving_count   SMALLINT    NOT NULL DEFAULT 1,
    note            TEXT,

    CONSTRAINT ck_recipe__version_type CHECK (version_type IN ('standard', 'bar_signature', 'user')),

    CONSTRAINT ck_recipe__serving_count CHECK (serving_count BETWEEN 1 AND 8),

    -- SPEC-06 §3.1 이 "bar_signature 일 때만"·"user 일 때만"이라고 적었다.
    -- 명시된 제약은 아니지만 **DB 로 표현 가능하므로 건다** (§4 서두).
    -- 표준 레시피에 작성자가 붙으면 그것은 이미 표준이 아니다.
    CONSTRAINT ck_recipe__author CHECK (
        (version_type = 'standard' AND author_bar_id IS NULL AND author_user_id IS NULL)
        OR (version_type = 'bar_signature' AND author_bar_id IS NOT NULL)
        OR (version_type = 'user' AND author_user_id IS NOT NULL)
    )
);

CREATE TRIGGER recipe_set_updated_at BEFORE UPDATE ON recipe
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- INV-COCKTAIL-07 — 표준 레시피는 칵테일당 **정확히 1개**.
--
-- 부분 유니크 인덱스다. WHERE 절이 없으면 bar_signature 도 하나로 묶여
-- PRIN-D03 의 "제휴 바 버전 n개"가 불가능해진다.
CREATE UNIQUE INDEX uq_recipe__standard
    ON recipe (cocktail_id) WHERE version_type = 'standard';

CREATE INDEX ix_recipe__cocktail ON recipe (cocktail_id, version_type);

COMMENT ON TABLE recipe IS
    'PRIN-D03 — 표준 1개 + 바 시그니처 n개. 역방향 유입이 여기서 생긴다.';

-- ── 스텝 ────────────────────────────────────────────────────────────────────

CREATE TABLE recipe_step (
    recipe_id      BIGINT      NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
    step_no        SMALLINT    NOT NULL,
    text           TEXT        NOT NULL,

    -- 툴팁 용어 키 (FR-COCKTAIL-022, P1). 선택이다.
    technique_ref  VARCHAR(40),

    CONSTRAINT pk_recipe_step PRIMARY KEY (recipe_id, step_no),

    CONSTRAINT ck_recipe_step__step_no CHECK (step_no >= 1),

    -- 공백만 있는 스텝은 없는 것과 같다. trim 은 공백만 지우므로 정규식을 쓴다
    -- (ingredient.substitute_note 에서 겪은 것과 같은 함정).
    CONSTRAINT ck_recipe_step__text CHECK (text ~ '\S')
);

-- ── 재료 (PRIN-D01) ─────────────────────────────────────────────────────────

CREATE TABLE recipe_ingredient (
    recipe_id                 BIGINT       NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,

    -- 마스터 참조. **프리텍스트 금지** (R-F1.1-1 · NFR-D-03).
    -- 문자열로 두면 역검색(내 술장)과 바 연결이 전부 불가능해진다.
    ingredient_id             BIGINT       NOT NULL REFERENCES ingredient (id),

    position                  SMALLINT     NOT NULL,

    amount                    NUMERIC(6,2),
    unit                      VARCHAR(12),

    -- `1조각`처럼 배수 계산에서 제외하는 표기 (FR-COCKTAIL-019).
    -- 잔 수를 2배로 해도 "1조각"은 "2조각"이 아니다.
    amount_label              VARCHAR(40),

    role                      VARCHAR(16),
    is_optional               BOOLEAN      NOT NULL DEFAULT false,

    substitute_ingredient_id  BIGINT       REFERENCES ingredient (id),
    substitute_note           TEXT,

    -- R-F2.2-5 — 역검색 판정 대상인가. 기본값은 재료 카테고리에서 오고
    -- (IngredientFacade.defaultCountsForStock) 레시피가 덮어쓸 수 있다.
    -- 가니시가 그 칵테일의 정체성인 경우가 있다.
    counts_for_stock          BOOLEAN      NOT NULL DEFAULT true,

    CONSTRAINT pk_recipe_ingredient PRIMARY KEY (recipe_id, position),

    CONSTRAINT ck_recipe_ingredient__position CHECK (position >= 1),

    CONSTRAINT ck_recipe_ingredient__amount CHECK (amount IS NULL OR amount >= 0),

    CONSTRAINT ck_recipe_ingredient__unit CHECK (
        unit IS NULL OR unit IN ('ml', 'dash', 'barspoon', 'piece', 'top_up')),

    CONSTRAINT ck_recipe_ingredient__role CHECK (
        role IS NULL OR role IN ('base', 'modifier', 'sweetener', 'citrus', 'garnish')),

    -- 자기 자신을 대체재로 두면 대체 안내가 무의미해진다.
    CONSTRAINT ck_recipe_ingredient__self_substitute CHECK (
        substitute_ingredient_id IS NULL OR substitute_ingredient_id <> ingredient_id)
);

-- SPEC-06 §5 — 역검색 · 재료 사전("이 재료를 쓰는 칵테일").
CREATE INDEX ix_recipe_ingredient__ingredient ON recipe_ingredient (ingredient_id);

COMMENT ON COLUMN recipe_ingredient.amount_label IS
    'FR-COCKTAIL-019 — 이 값이 있으면 잔 수 배수 계산에서 제외한다.';
