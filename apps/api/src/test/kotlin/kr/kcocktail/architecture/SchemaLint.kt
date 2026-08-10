package kr.kcocktail.architecture

import java.sql.Connection

/**
 * SPEC-06 §1 규약을 **실제 스키마**에 대고 검사한다 (ISSUE-002).
 *
 * 규칙을 테스트가 아니라 여기 두는 이유: 같은 규칙을 두 스키마에 돌려야 하기 때문이다.
 * 하나는 진짜 `public`(위반 0건이어야 한다), 하나는 일부러 어긴 픽스처
 * (규칙이 살아 있음을 증명한다). ISSUE-001 의 모듈 경계와 같은 구조다.
 *
 * ## 전부 모아서 보고한다
 *
 * 첫 위반에서 멈추면 세션이 "고치고 돌리고"를 반복한다. 각 규칙은 위반을 **전부** 담은
 * 리스트를 돌려주고, 테스트가 그것을 한 번에 보여준다.
 */
object SchemaLint {

    /** Flyway 가 자기 형식으로 쓰는 테이블이다. 우리 규약의 대상이 아니다. */
    private const val FLYWAY_HISTORY = "flyway_schema_history"

    /**
     * 단수형인데 `s` 로 끝나는 이름들. 규칙 4 의 예외다.
     *
     * 억제가 쌓이는 것이 보이도록 별도 파일이 아니라 여기에 둔다 —
     * 늘어나기 시작하면 규칙이 아니라 명명 규약을 다시 봐야 한다는 신호다.
     */
    private val SINGULAR_ENDING_IN_S = setOf("glass", "status", "press")

    /**
     * SPEC-06 §4.1 · `INV-BAR-03` — 물리 삭제 금지 대상.
     *
     * **이 상수가 목록의 정본이다.** 넷 다 Phase 1a 에 아직 없다.
     * 각 테이블을 만드는 이슈가 같은 마이그레이션에서 `REVOKE DELETE` 를 함께 넣고,
     * [protectedTablesWithDeleteGrant] 가 빠뜨린 것을 잡는다.
     */
    val PROTECTED_TABLES = listOf("cocktail", "bar", "article", "curation_list")

    /** 앱이 붙는 역할. 권한 검사의 주체다. */
    private const val APP_ROLE = "kcocktail_app"

    // ── 규칙 ────────────────────────────────────────────────────────────────

    /** SPEC-06 §1.2 — 모든 실체 테이블에 `id` · `created_at` · `updated_at`. */
    fun missingCommonColumns(conn: Connection, schema: String): List<String> = conn.query(
        """
        SELECT t.table_name || ' 에 ' || required.name || ' 없음'
        FROM information_schema.tables t
        CROSS JOIN (VALUES ('id'), ('created_at'), ('updated_at')) AS required(name)
        WHERE t.table_schema = ? AND t.table_type = 'BASE TABLE' AND t.table_name <> ?
          AND NOT EXISTS (
              SELECT 1 FROM information_schema.columns c
              WHERE c.table_schema = t.table_schema AND c.table_name = t.table_name
                AND c.column_name = required.name)
        ORDER BY 1
        """,
        schema, FLYWAY_HISTORY,
    )

    /** SPEC-06 §1.2 — `id BIGINT GENERATED ALWAYS AS IDENTITY`. `serial` 은 시퀀스를 남긴다. */
    fun idNotAlwaysIdentity(conn: Connection, schema: String): List<String> = conn.query(
        """
        SELECT table_name || '.id 가 GENERATED ALWAYS AS IDENTITY 아님 (identity=' ||
               is_identity || ', generation=' || coalesce(identity_generation, '-') || ')'
        FROM information_schema.columns
        WHERE table_schema = ? AND column_name = 'id' AND table_name <> ?
          AND (is_identity <> 'YES' OR identity_generation <> 'ALWAYS')
        ORDER BY 1
        """,
        schema, FLYWAY_HISTORY,
    )

    /**
     * SPEC-06 §1.2 — `updated_at` 은 **트리거로** 갱신한다.
     *
     * JPA `@PreUpdate` 로 대신하면 벌크 `UPDATE` 와 마이그레이션이 그것을 건너뛴다.
     */
    fun missingUpdatedAtTrigger(conn: Connection, schema: String): List<String> = conn.query(
        """
        SELECT t.table_name || ' 에 set_updated_at 트리거 없음'
        FROM information_schema.tables t
        WHERE t.table_schema = ? AND t.table_type = 'BASE TABLE' AND t.table_name <> ?
          AND EXISTS (
              SELECT 1 FROM information_schema.columns c
              WHERE c.table_schema = t.table_schema AND c.table_name = t.table_name
                AND c.column_name = 'updated_at')
          AND NOT EXISTS (
              SELECT 1
              FROM pg_trigger tg
              JOIN pg_class cl ON cl.oid = tg.tgrelid
              JOIN pg_namespace n ON n.oid = cl.relnamespace
              JOIN pg_proc p ON p.oid = tg.tgfoid
              WHERE n.nspname = t.table_schema AND cl.relname = t.table_name
                AND p.proname = 'set_updated_at' AND NOT tg.tgisinternal)
        ORDER BY 1
        """,
        schema, FLYWAY_HISTORY,
    )

    /** SPEC-06 §1.1 — 테이블은 `snake_case` **단수형**. */
    fun badTableNames(conn: Connection, schema: String): List<String> = conn.query(
        """
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = ? AND table_type = 'BASE TABLE' AND table_name <> ?
          AND (table_name !~ '^[a-z][a-z0-9_]*$' OR table_name LIKE '%s')
        ORDER BY 1
        """,
        schema, FLYWAY_HISTORY,
    ).filterNot { it in SINGULAR_ENDING_IN_S }
        .map { "$it — snake_case 단수형이 아니다" }

    /** SPEC-06 §1.1 — 불리언은 `is_` / `has_` 접두. */
    fun badBooleanNames(conn: Connection, schema: String): List<String> = conn.query(
        """
        SELECT table_name || '.' || column_name || ' — 불리언인데 is_/has_ 접두가 아니다'
        FROM information_schema.columns
        WHERE table_schema = ? AND table_name <> ? AND data_type = 'boolean'
          AND column_name !~ '^(is|has)_'
        ORDER BY 1
        """,
        schema, FLYWAY_HISTORY,
    )

    /** SPEC-06 §1.1 — 시각은 `_at` 접미, 날짜는 `_on` 접미. */
    fun badTemporalNames(conn: Connection, schema: String): List<String> = conn.query(
        """
        SELECT table_name || '.' || column_name || ' (' || data_type || ') — ' ||
               CASE WHEN data_type = 'date' THEN '_on 접미가 아니다' ELSE '_at 접미가 아니다' END
        FROM information_schema.columns
        WHERE table_schema = ? AND table_name <> ?
          AND ((data_type LIKE 'timestamp%' AND column_name !~ '_at$')
            OR (data_type = 'date' AND column_name !~ '_on$'))
        ORDER BY 1
        """,
        schema, FLYWAY_HISTORY,
    )

    /**
     * SPEC-06 §1.3 — enum 은 네이티브 `ENUM` 이 아니라 `VARCHAR` + `CHECK`.
     *
     * 네이티브는 값 추가는 쉽지만 **삭제와 순서 변경이 사실상 불가능하다.**
     * 분류 3축은 앞으로 늘어난다.
     */
    fun nativeEnumTypes(conn: Connection, schema: String): List<String> = conn.query(
        """
        SELECT t.typname || ' — 네이티브 ENUM. VARCHAR + CHECK 로 (SPEC-06 §1.3)'
        FROM pg_type t
        JOIN pg_namespace n ON n.oid = t.typnamespace
        WHERE n.nspname = ? AND t.typtype = 'e'
        ORDER BY 1
        """,
        schema,
    )

    /** SPEC-06 §1.2 — 시각은 `TIMESTAMPTZ`. 시간대 없는 `timestamp` 는 서버 로케일에 끌려간다. */
    fun naiveTimestamps(conn: Connection, schema: String): List<String> = conn.query(
        """
        SELECT table_name || '.' || column_name || ' — timestamptz 가 아니다'
        FROM information_schema.columns
        WHERE table_schema = ? AND table_name <> ?
          AND data_type = 'timestamp without time zone'
        ORDER BY 1
        """,
        schema, FLYWAY_HISTORY,
    )

    /**
     * SPEC-06 §4.1 — 보호 테이블에 앱 역할의 `DELETE` 가 남아 있으면 위반.
     *
     * 존재하는 것만 검사한다 ([PROTECTED_TABLES] 넷 다 아직 없다).
     * `PRIN-T05` — 불변식은 앱이 아니라 서버(여기서는 DB)가 강제한다.
     */
    fun protectedTablesWithDeleteGrant(
        conn: Connection,
        schema: String,
        tables: List<String> = PROTECTED_TABLES,
    ): List<String> = tables.flatMap { table ->
        // 이름이 아니라 OID 로 묻는다. has_table_privilege 는 없는 테이블 **이름**을 받으면
        // WHERE 로 걸러지기 전에 예외를 던진다 — 존재 여부를 조인이 보장하게 한다.
        // (넷 다 Phase 1a 에 아직 없으므로 이 경로를 매번 탄다)
        conn.query(
            """
            SELECT c.relname || ' 에 $APP_ROLE 의 DELETE 권한이 남아 있다 (SPEC-06 §4.1)'
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = ? AND c.relname = ? AND c.relkind = 'r'
              AND has_table_privilege('$APP_ROLE', c.oid, 'DELETE')
            """,
            schema, table,
        )
    }

    /** 위 규칙 전부. 이름 → 위반 목록. 통과하면 빈 맵이다. */
    fun runAll(conn: Connection, schema: String): Map<String, List<String>> = mapOf(
        "공통 컬럼" to missingCommonColumns(conn, schema),
        "id identity" to idNotAlwaysIdentity(conn, schema),
        "updated_at 트리거" to missingUpdatedAtTrigger(conn, schema),
        "테이블 명명" to badTableNames(conn, schema),
        "불리언 명명" to badBooleanNames(conn, schema),
        "시각·날짜 명명" to badTemporalNames(conn, schema),
        "네이티브 ENUM" to nativeEnumTypes(conn, schema),
        "timestamptz" to naiveTimestamps(conn, schema),
        "보호 테이블 DELETE" to protectedTablesWithDeleteGrant(conn, schema),
    ).filterValues { it.isNotEmpty() }
}

private fun Connection.query(sql: String, vararg params: Any): List<String> =
    prepareStatement(sql.trimIndent()).use { st ->
        params.forEachIndexed { i, p -> st.setObject(i + 1, p) }
        st.executeQuery().use { rs ->
            buildList { while (rs.next()) add(rs.getString(1)) }
        }
    }
