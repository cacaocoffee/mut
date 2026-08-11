-- ISSUE-011 — 도수 구간 필터 인덱스 (SPEC-06 §5)
--
-- abv_calculated · abv_override · abv 컬럼은 **V009 에 이미 있다.**
-- abv 가 생성 컬럼이라 테이블 생성 시점에 정의돼야 했고, ck_cocktail_na 가 그것을 참조한다.
-- 여기서는 인덱스만 만든다.
--
-- ADR-0003 의 도수 4구간(na · low · mid · high)이 이 인덱스를 탄다 (이슈 018·019).
-- 표시값 abv 가 생성 컬럼이라 인덱스가 붙는다 — 매 쿼리에서 COALESCE 를 쓰면 못 붙는다.
CREATE INDEX ix_cocktail__status_abv ON cocktail (status, abv);

COMMENT ON INDEX ix_cocktail__status_abv IS
    'SPEC-06 §5 — 도수 구간 필터. 공개 조회는 status 로 먼저 좁힌다.';
