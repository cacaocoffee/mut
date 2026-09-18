-- ISSUE #195 — 재료 ↔ 유통 제품 매핑 (GAPS G-41 · FR-INGREDIENT-001~003 · INV-INGREDIENT-01)
--
-- ## 두 단위를 잇는다
--
-- `ingredient`(드라이 진) 와 `distributed_product`(고든스 런던 드라이진 700ml · ○○수입) 는
-- 단위가 다르다. 이 표가 "이 제품은 이 재료다" 를 한 줄씩 적는다.
--
-- ## 자동은 제안까지, 확정은 사람이
--
-- 제품명 표기가 제각각이라("캄파리"·"깜빠리"·"CAMPARI BITTER 25%") 자동 매칭은 틀린다.
-- 배치는 `suggested` 까지만 쓰고, 어드민이 `approved`·`rejected` 로 바꾼다.
-- `ingredient.domestic_availability` 도 배치가 직접 바꾸지 않는다 — 승인된 매치를 세어
-- **제안**만 하고 어드민이 확정한다. `ck_ingredient__substitute` 가 있어서 배치가 UNAVAILABLE 로
-- 내리면 대체재 없는 행에서 터진다 (DECISIONS §1.1 의 "미승인은 발행에서 막는다" 와 같은 결).

-- ── 재료: 브랜드 검색어 ────────────────────────────────────────────────────
--
-- 별칭(aliases)은 "럼"·"소다" 처럼 넓어서 제품명 검색에 쓰면 엉뚱한 것이 잡힌다.
-- 제품명에서 이 재료를 찾을 때 쓰는 말만 따로 둔다 — 캄파리 → {campari, 캄파리, 깜빠리}.
ALTER TABLE ingredient ADD COLUMN brand_keywords TEXT[] NOT NULL DEFAULT '{}';

COMMENT ON COLUMN ingredient.brand_keywords IS
    '#195 — 유통 제품명에서 이 재료를 찾는 검색어. aliases 와 달리 좁게 적는다.';

-- ── 매핑 ────────────────────────────────────────────────────────────────────
CREATE TABLE ingredient_product_match (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    ingredient_id    BIGINT       NOT NULL REFERENCES ingredient (id),
    -- 제품이 지워지면 매핑도 지운다. 제품 시드는 지우지 않고 덧쓴다(R__seed_04 · ON CONFLICT DO UPDATE)
    -- 라서 승인한 매핑이 시드 재실행에 사라지지 않는다.
    product_id       BIGINT       NOT NULL REFERENCES distributed_product (id) ON DELETE CASCADE,

    -- suggested → approved | rejected. 되돌리기는 rejected → approved 만 (어드민 화면).
    status           VARCHAR(12)  NOT NULL DEFAULT 'suggested',
    -- 어느 검색어가 잡았나. 어드민이 "왜 이게 떴지" 를 볼 수 있어야 한다.
    matched_keyword  TEXT         NOT NULL,
    -- 0~100. 브랜드 검색어 80 · 별칭 50 · 이름이 통째로 같으면 100.
    confidence       SMALLINT     NOT NULL,
    matched_by       VARCHAR(16)  NOT NULL DEFAULT 'batch',

    CONSTRAINT uq_ingredient_product_match__pair UNIQUE (ingredient_id, product_id),
    CONSTRAINT ck_ingredient_product_match__status CHECK (status IN ('suggested', 'approved', 'rejected')),
    CONSTRAINT ck_ingredient_product_match__matched_by CHECK (matched_by IN ('batch', 'admin')),
    CONSTRAINT ck_ingredient_product_match__confidence CHECK (confidence BETWEEN 0 AND 100)
);

CREATE TRIGGER ingredient_product_match_set_updated_at BEFORE UPDATE ON ingredient_product_match
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 어드민 재료 상세가 "이 재료의 제안·승인" 을 읽는 순서.
CREATE INDEX ix_ingredient_product_match__ingredient_status
    ON ingredient_product_match (ingredient_id, status);

-- 제품명 부분일치 검색. 배치가 검색어마다 한 번씩 훑는다.
CREATE INDEX ix_distributed_product__name_ko_lower ON distributed_product (lower(name_ko) text_pattern_ops);

COMMENT ON TABLE ingredient_product_match IS
    '#195 — 재료와 식약처 신고 제품의 대응. 배치는 suggested 까지, 승인은 어드민.';
COMMENT ON CONSTRAINT uq_ingredient_product_match__pair ON ingredient_product_match IS
    'PRIN-T07 — 배치 멱등. 같은 쌍은 몇 번을 돌려도 한 줄이다.';
