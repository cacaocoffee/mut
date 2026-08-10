package kr.kcocktail.architecture

import kr.kcocktail.support.PostgresSupport

/**
 * 일부러 SPEC-06 §1 을 어긴 스키마 (ISSUE-002).
 *
 * `public` 에는 아직 테이블이 하나도 없어서 린트가 "위반 0건"을 돌려주는데,
 * 그것만으로는 **린트가 살아 있는지 증명되지 않는다.** SQL 에 오타가 나도 0건이다.
 * 여기 스키마가 규칙마다 하나씩 걸려 주는 것이 그 증명이다.
 *
 * `good_table` 은 반대 방향 증명이다 — 린트가 아무거나 다 잡는 것이 아님을 보인다.
 *
 * 규칙을 추가하면 **그것을 어기는 테이블도 함께** 넣는다.
 */
object LintFixtures {

    const val SCHEMA = "lint_fixture"

    fun create() = PostgresSupport.migrateConnection().use { conn ->
        conn.createStatement().use { it.execute(DDL) }
    }

    private val DDL = """
        DROP SCHEMA IF EXISTS $SCHEMA CASCADE;
        CREATE SCHEMA $SCHEMA;

        -- 규칙 위반: 테이블명 복수형 · 공통 컬럼 없음 · id 가 identity 아님(bigserial)
        CREATE TABLE $SCHEMA.cocktails (
            id      BIGSERIAL PRIMARY KEY,
            name    TEXT NOT NULL
        );

        -- 규칙 위반: updated_at 있는데 트리거 없음 · 불리언 접두 없음
        --            시각/날짜 접미 없음 · timestamptz 아님
        CREATE TABLE $SCHEMA.bad_column (
            id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
            sponsored   BOOLEAN NOT NULL DEFAULT false,
            published   TIMESTAMP,
            expiry      DATE
        );

        -- 규칙 위반: 네이티브 ENUM (SPEC-06 §1.3 은 VARCHAR + CHECK)
        CREATE TYPE $SCHEMA.bad_enum AS ENUM ('a', 'b');

        -- 전부 지킨다. 린트가 무차별로 잡지 않는다는 반대 증명.
        CREATE TABLE $SCHEMA.good_table (
            id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
            is_signature  BOOLEAN NOT NULL DEFAULT false,
            published_at  TIMESTAMPTZ,
            released_on   DATE
        );
        CREATE TRIGGER good_table_set_updated_at BEFORE UPDATE ON $SCHEMA.good_table
            FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

        -- SPEC-06 §4.1 보호 테이블 흉내. REVOKE 가 실제로 막는지 본다.
        CREATE TABLE $SCHEMA.protected_row (
            id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
        );
        CREATE TRIGGER protected_row_set_updated_at BEFORE UPDATE ON $SCHEMA.protected_row
            FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

        -- REVOKE 를 빠뜨린 쪽. 대조군이다.
        CREATE TABLE $SCHEMA.unprotected_row (
            id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
        );
        CREATE TRIGGER unprotected_row_set_updated_at BEFORE UPDATE ON $SCHEMA.unprotected_row
            FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

        INSERT INTO $SCHEMA.protected_row DEFAULT VALUES;
        INSERT INTO $SCHEMA.unprotected_row DEFAULT VALUES;

        -- 앱 역할에 런타임과 같은 권한을 준 뒤, 보호 테이블에서만 DELETE 를 회수한다.
        GRANT USAGE ON SCHEMA $SCHEMA TO kcocktail_app;
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA $SCHEMA TO kcocktail_app;
        REVOKE DELETE ON $SCHEMA.protected_row FROM kcocktail_app;
    """.trimIndent()
}
