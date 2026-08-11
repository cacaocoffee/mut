-- ISSUE-013 — 발행 (SPEC-02 §2.3, FR-COCKTAIL-010~013)
--
-- 테이블을 만들지 않는다. audit_log 는 이슈 014 다.
-- 게이트는 전부 앱 강제다 (SPEC-06 §4.3) — 조건부이고 여러 테이블을 참조해
-- CHECK 로 표현할 수 없다.

-- 발행 이력 조회 (어드민 목록 · NFR-D-02 의 일 1회 전수 스캔).
CREATE INDEX ix_cocktail__status_published_at ON cocktail (status, published_at DESC);

COMMENT ON INDEX ix_cocktail__status_published_at IS
    'NFR-D-02 — 게이트를 우회한 published 를 찾는 전수 스캔이 이 인덱스를 탄다.';
