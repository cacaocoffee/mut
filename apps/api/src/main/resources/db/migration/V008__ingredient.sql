-- ISSUE-008 — 재료 마스터 (SPEC-06 §3.2, FR-INGREDIENT-001·003·004·006)
--
-- PRIN-D01 — 재료는 참조지 문자열이 아니다.
--   문자열로 저장하면 역검색(내 술장)과 바 연결이 전부 불가능해진다.
--   나중에 정규화하면 마이그레이션 비용이 크다.
--
-- PRIN-P05 — 국내 기준으로 정규화한다.
--   이 서비스가 해외 DB 의 번역판이 아닌 이유는 domestic_availability 하나다.

CREATE TABLE ingredient (
    id                     BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- 공개 식별자. 발행 후 불변이다 (PRIN-D02).
    slug                   VARCHAR(120) NOT NULL,
    name_ko                VARCHAR(120) NOT NULL,
    name_en                VARCHAR(120) NOT NULL,

    -- SPEC-06 §1.4 의 배열 예외. 검색 전용이고 무결성 대상이 아니다.
    aliases                TEXT[]       NOT NULL DEFAULT '{}',

    category               VARCHAR(16)  NOT NULL,
    abv                    NUMERIC(4,1),
    description            TEXT,

    domestic_availability  VARCHAR(16)  NOT NULL,
    substitute_note        TEXT,
    price_band             VARCHAR(12),

    -- FR-ADMIN-007 · DECISIONS §1.1 — 에디터가 요청하고 admin 이 승인한다.
    -- 기본이 false 라서 새 재료는 draft 에만 쓸 수 있고 발행에서 막힌다.
    is_approved            BOOLEAN      NOT NULL DEFAULT false,

    CONSTRAINT uq_ingredient__slug UNIQUE (slug),

    -- FR-INGREDIENT-006 — 카테고리 7종
    CONSTRAINT ck_ingredient__category CHECK (category IN
        ('spirit', 'liqueur', 'bitters', 'syrup', 'juice', 'garnish', 'mixer')),

    -- PRIN-P05 — 국내 유통 4종
    CONSTRAINT ck_ingredient__availability CHECK (domestic_availability IN
        ('common', 'specialty', 'import_only', 'unavailable')),

    -- INV-INGREDIENT-01 (R-F1.3-2) — 미유통이면 대체재 또는 자가제조 안내가 필수다.
    --
    -- SPEC-06 §4.3 은 이것을 "앱 강제"로 분류했다. 조건부라 CHECK 로 못 쓴다고 본 것인데,
    -- 실제로는 쓸 수 있다. §4 서두가 "DB 로 강제할 수 있는 것은 DB 에서 한다"고 했으므로
    -- **양쪽 다** 건다 — 앱이 막고 DB 가 다시 막는다 (PRIN-T05).
    --
    -- 공백만 있는 문자열은 없는 것으로 친다. 있으나 마나 한 안내는 안내가 아니다.
    --
    -- trim() 을 쓰지 않는다 — Postgres 의 trim 은 기본이 **공백만** 지워서
    -- 탭·개행으로 채운 값이 통과한다. 코틀린 isNullOrBlank() 는 둘 다 잡으므로
    -- trim 으로 두면 앱이 DB 보다 엄격해지고, 배치가 DB 를 직접 칠 때 어긋난다.
    -- `~ '\S'` 는 "공백 아닌 문자가 하나라도 있는가"다.
    --
    -- IS NOT NULL 을 빼면 안 된다. NULL ~ '\S' 는 NULL 이고 **CHECK 는 NULL 을 통과로 친다** —
    -- 안내를 아예 안 적은 경우가 그대로 새어 나간다 (3값 논리).
    --
    -- 문서와 다른 지점이라 GAPS G-24 에 근거를 남겼다.
    CONSTRAINT ck_ingredient__substitute CHECK (
        domestic_availability NOT IN ('import_only', 'unavailable')
        OR (substitute_note IS NOT NULL AND substitute_note ~ '\S')
    )
);

CREATE TRIGGER ingredient_set_updated_at BEFORE UPDATE ON ingredient
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 별칭 검색 (FR-INGREDIENT-005, 이슈 023 이 쓴다).
CREATE INDEX ix_ingredient__aliases ON ingredient USING GIN (aliases);

-- 재료 사전이 카테고리별로 승인분만 훑는다.
CREATE INDEX ix_ingredient__category_approved ON ingredient (category, is_approved);

COMMENT ON COLUMN ingredient.domestic_availability IS
    'PRIN-P05 — 이 컬럼이 해외 DB 번역판과 다른 유일한 지점이다.';

-- ── 브랜드 (INV-INGREDIENT-02 · FR-INGREDIENT-004) ──────────────────────────
--
-- 특정 브랜드를 언급할 때 광고성 여부를 시스템상 구분해 표기한다.
--
-- ⚠️ Phase 1a 에서 is_sponsored 를 켜지 않는다.
--    켜는 순간 ADR-0004 가 지목한 주류 광고 규제 접점이 생기고
--    NFR-L-05(주류광고 자문)가 선행돼야 한다. 컬럼은 만들되 데이터는 전부 false 다.
CREATE TABLE ingredient_brand (
    id             BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    ingredient_id  BIGINT      NOT NULL REFERENCES ingredient (id) ON DELETE CASCADE,
    name           VARCHAR(80) NOT NULL,
    purchase_url   TEXT,

    -- NOT NULL 이 요점이다. "정해지지 않음"이라는 상태가 있으면
    -- 라벨을 붙일지 말지 결정할 수 없고, 그 순간 공정위 의무를 어길 여지가 생긴다.
    is_sponsored   BOOLEAN     NOT NULL DEFAULT false,

    CONSTRAINT uq_ingredient_brand__name UNIQUE (ingredient_id, name)
);

CREATE TRIGGER ingredient_brand_set_updated_at BEFORE UPDATE ON ingredient_brand
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX ix_ingredient_brand__ingredient ON ingredient_brand (ingredient_id);

COMMENT ON COLUMN ingredient_brand.is_sponsored IS
    'INV-INGREDIENT-02. Phase 1a 에서 true 로 켜지 않는다 — NFR-L-05 선행.';
