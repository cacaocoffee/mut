-- ISSUE-012 — 당도 · 별칭 (SPEC-06 §3.1, FR-COCKTAIL-007·009)
--
-- sweetness · aliases 컬럼과 GIN 인덱스는 **V009 에 이미 있다.**
-- 여기서는 별칭 요소의 공백을 막는 제약만 더한다.

-- 배열 요소 중 공백만 있는 것이 있는가.
--
-- CHECK 에는 서브쿼리를 쓸 수 없다. IMMUTABLE 함수로 빼면 쓸 수 있고,
-- 그러면 SPEC-06 §4 서두("DB 로 강제할 수 있는 것은 DB 에서 한다")를 지킬 수 있다 —
-- 앱 검증만 두면 배치·마이그레이션이 우회한다 (PRIN-T05).
CREATE OR REPLACE FUNCTION has_blank_element(arr TEXT[]) RETURNS BOOLEAN
    LANGUAGE sql IMMUTABLE PARALLEL SAFE AS
$$
    SELECT EXISTS (SELECT 1 FROM unnest(arr) AS e WHERE e !~ '\S')
$$;

COMMENT ON FUNCTION has_blank_element(TEXT[]) IS
    'ISSUE-012 — 배열에 공백만 있는 요소가 있는가. CHECK 에서 쓰려고 IMMUTABLE 로 뺐다.';

-- 빈 별칭은 검색에 걸리지도 않고 화면에 빈 칩을 만든다.
ALTER TABLE cocktail
    ADD CONSTRAINT ck_cocktail__aliases_nonblank CHECK (NOT has_blank_element(aliases));

-- 재료 별칭도 같은 규칙이다 (이슈 008 에서 만든 컬럼).
ALTER TABLE ingredient
    ADD CONSTRAINT ck_ingredient__aliases_nonblank CHECK (NOT has_blank_element(aliases));
