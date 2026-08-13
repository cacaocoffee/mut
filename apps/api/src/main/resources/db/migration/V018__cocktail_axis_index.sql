-- ISSUE-021 — 누락된 축 인덱스 (SPEC-06 §5)
--
-- §5 의 표는 셋을 요구한다.
--
--   cocktail(status, base_spirit)     ← V009 에 있다
--   cocktail(status, style_primary)   ← 없었다
--   cocktail(status, method)          ← 없었다
--
-- 뒤의 둘이 빠져 있었다. 배리에이션(FR-COCKTAIL-024)의 **1순위 기준이 style_primary** 라
-- 상세 페이지마다 이 조건으로 조회하는데, 인덱스가 없으면 500종에서 매번 순차 스캔이다.
-- SSG 빌드가 종수만큼 부르는 경로라 그대로 곱해진다.
--
-- 카테고리 페이지(이슈 022)도 같은 조건을 쓴다 — §5 가 "카테고리 페이지" 를 근거로 적은 것이
-- 그것이다. 즉 이 인덱스는 이 이슈만의 것이 아니라 **원래 있었어야 한다.**
--
-- 이슈 021 의 헤더는 `migration | —` 이지만 RED 18(인덱스를 탄다)이 부재를 잡았다.
-- 명세가 요구하는 것을 빠뜨린 쪽이 잘못이라 여기서 채운다.

CREATE INDEX ix_cocktail__status_style_primary ON cocktail (status, style_primary);
CREATE INDEX ix_cocktail__status_method ON cocktail (status, method);

COMMENT ON INDEX ix_cocktail__status_style_primary IS
    'SPEC-06 §5 — 카테고리 페이지 · 배리에이션 1순위 (R-C-3).';
COMMENT ON INDEX ix_cocktail__status_method IS
    'SPEC-06 §5 — 메이킹 축 카테고리 페이지.';
