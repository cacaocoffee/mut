-- G-40 — 저장소를 kcocktail 에서 mut 로 개명할 때 DB 롤 이름만 남았다.
-- 적용된 마이그레이션은 불변이므로(CONVENTIONS §4) V001 을 고치지 않고
-- 여기서 이름만 바꾼다. 권한과 멤버십은 이름을 따라온다.
--
-- 새로 만드는 DB 도 순서대로 적용하면 같은 곳에 도착한다:
-- V001 이 옛 이름으로 만들고, 이 파일이 새 이름으로 바꾼다.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'kcocktail_app') THEN
        ALTER ROLE kcocktail_app RENAME TO mut_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'kcocktail_migrate') THEN
        ALTER ROLE kcocktail_migrate RENAME TO mut_migrate;
    END IF;
END $$;
