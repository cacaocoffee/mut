-- ISSUE-002 — 기반 마이그레이션 (SPEC-06 §1 · §5 · §6)
--
-- 테이블을 만들지 않는다. 확장 · 함수 · 역할까지다.
-- 도메인 테이블은 각자의 이슈가 자기 번호로 만든다 (V008 재료, V009 칵테일 …).
--
-- 이 파일은 적용된 뒤 수정하지 않는다 (SPEC-06 §6). 체크섬이 바뀌면 기동이 실패한다.

-- ── 확장 ────────────────────────────────────────────────────────────────────
-- SPEC-06 §5 — 초성 검색이 트라이그램에 의존한다. 색인을 만드는 이슈(017)보다
-- 먼저 있어야 하므로 마이그레이션 첫 단계에 둔다.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ── updated_at 자동 갱신 ────────────────────────────────────────────────────
-- SPEC-06 §1.2 는 updated_at 을 "트리거로 갱신"이라고 못박았다.
-- JPA @PreUpdate 로 하지 않는 이유: 벌크 UPDATE 와 마이그레이션이 그것을 건너뛴다.
-- 정본은 DB 다.
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION set_updated_at() IS
    'SPEC-06 §1.2 — 실체 테이블마다 BEFORE UPDATE 트리거로 건다. 테이블을 만드는 이슈의 책임이다.';

-- ── 역할 분리 ───────────────────────────────────────────────────────────────
-- DDL 을 쥔 역할과 런타임 역할을 나눈다. 나누지 않으면 SPEC-06 §4.1 의
-- "물리 삭제 금지"를 DB 가 강제할 수 없다 — 앱이 DROP 도 할 수 있는 권한으로 붙기 때문이다.
--
--   kcocktail_migrate  DDL. Flyway 전용
--   kcocktail_app      DML 만. 보호 테이블에는 DELETE 도 없다
--
-- 역할은 클러스터 단위라 IF NOT EXISTS 가 없다. DO 블록으로 멱등하게 만든다.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'kcocktail_migrate') THEN
        CREATE ROLE kcocktail_migrate NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'kcocktail_app') THEN
        CREATE ROLE kcocktail_app NOLOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO kcocktail_app, kcocktail_migrate;
GRANT CREATE ON SCHEMA public TO kcocktail_migrate;

-- 앞으로 만들어지는 테이블에 앱 역할의 DML 을 자동으로 열어 준다.
-- DELETE 를 기본에 포함하고 보호 테이블에서만 각자의 마이그레이션이 회수한다 —
-- 기본을 닫아 두면 새 테이블마다 GRANT 를 빠뜨려 런타임에 터진다.
--
-- FOR ROLE 을 붙이지 않는 것이 중요하다. 그 절은 **지정한 역할이 직접 만든** 객체에만 걸린다.
-- Flyway 가 붙는 계정과 어긋나면 권한이 하나도 안 붙고, 그 사실이 첫 INSERT 까지 안 보인다.
-- 생략하면 "이 마이그레이션을 돌린 계정"이 대상이 되어 테스트(슈퍼유저)와 운영이 같이 맞는다.
--
-- 전제: **Flyway 는 항상 같은 계정으로 붙는다.** 운영에서 그 계정을 kcocktail_migrate 의
-- 멤버로 두면 DDL 권한이 역할로 관리된다. 계정을 바꾸면 이 줄을 다시 실행해야 한다.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO kcocktail_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO kcocktail_app;

-- V001 이전에 만들어진 것이 있다면(빈 DB 가 아닌 경우) 함께 열어 준다.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO kcocktail_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO kcocktail_app;

-- ── 물리 삭제 금지 (SPEC-06 §4.1 · INV-BAR-03) ──────────────────────────────
--
-- 보호 대상은 cocktail · bar · article · curation_list 넷이다.
-- 넷 다 아직 없다 — 각 테이블을 만드는 이슈가 같은 마이그레이션 안에서
--
--     REVOKE DELETE ON <table> FROM kcocktail_app;
--
-- 을 함께 넣는다. 빠뜨리면 SchemaLintTest 의 "보호 테이블에 DELETE 권한 없음"이 잡는다.
-- 목록의 정본은 그 테스트의 PROTECTED_TABLES 상수다.
