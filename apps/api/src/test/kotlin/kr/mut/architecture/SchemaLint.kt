package kr.mut.architecture

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

    /**
     * **우리가 스키마를 정하지 않는 테이블.** SPEC-06 §1 규약의 대상이 아니다.
     *
     * 라이브러리의 계약이라 컬럼을 더하면 그쪽 쿼리가 깨진다. 억제가 쌓이는 것이 보이도록
     * 별도 파일이 아니라 여기에 둔다 — 목록이 길어지면 라이브러리를 너무 많이 들인 것이다.
     */
    private val FOREIGN_TABLES = listOf(
        "flyway_schema_history",                                 // Flyway
        "spring_session", "spring_session_attributes",           // Spring Session JDBC (ISSUE-005)
    )

    /** SQL 에 그대로 박는다. 상수라 주입 위험이 없다. */
    private val NOT_FOREIGN =
        "table_name NOT IN (${FOREIGN_TABLES.joinToString(", ") { "'$it'" }})"

    /**
     * 단수형인데 `s` 로 끝나는 이름들. 규칙 4 의 예외다.
     *
     * 억제가 쌓이는 것이 보이도록 별도 파일이 아니라 여기에 둔다 —
     * 늘어나기 시작하면 규칙이 아니라 명명 규약을 다시 봐야 한다는 신호다.
     */
    private val SINGULAR_ENDING_IN_S = setOf("glass", "status", "press")

    /**
     * `is_`/`has_` 가 아니어도 술어로 읽히는 불리언. 규칙 5 의 예외다.
     *
     * **SPEC-06 이 스스로와 충돌하는 지점**이다 — §1.1 은 접두를 요구하는데
     * 같은 문서 §3.1 의 `recipe_ingredient` 표가 `counts_for_stock` 으로 명시했고,
     * SPEC-02 §2.7 도 같은 이름이다. 더 구체적인 쪽(컬럼 정의)을 따르되,
     * §1.1 의 취지("이름만 보고 불리언인 줄 안다")는 이 이름도 만족한다 —
     * 3인칭 동사구라 술어로 읽힌다. GAPS G-25.
     *
     * 억제가 쌓이는 것이 보이도록 여기 둔다. 늘어나면 §1.1 을 고쳐야 한다는 신호다.
     */
    private val PREDICATE_BOOLEANS = listOf("counts_for_stock")

    /** SQL 에 그대로 박는다. 상수라 주입 위험이 없다 ([NOT_FOREIGN] 과 같은 방식). */
    private val NOT_PREDICATE_BOOLEAN =
        "column_name NOT IN (${PREDICATE_BOOLEANS.joinToString(", ") { "'$it'" }})"

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

    /**
     * SPEC-06 §1.2 — 모든 **실체** 테이블에 `id` · `created_at` · `updated_at`.
     *
     * ## 연관 테이블은 대상이 아니다
     *
     * `user_role` · `cocktail_style` 처럼 **복합 PK 를 가진 표**는 실체가 아니라 관계다.
     * 대리키 `id` 를 붙이면 복합 PK 가 의미를 잃고 같은 조합이 두 번 들어간다 —
     * 규약을 지키려다 무결성을 깨는 꼴이다.
     *
     * 이름 목록이 아니라 **구조**로 판정한다. 목록은 새 테이블마다 빠뜨린다.
     */
    fun missingCommonColumns(conn: Connection, schema: String): List<String> = conn.query(
        """
        SELECT t.table_name || ' 에 ' || required.name || ' 없음'
        FROM information_schema.tables t
        CROSS JOIN (VALUES ('id'), ('created_at'), ('updated_at')) AS required(name)
        WHERE t.table_schema = ? AND t.table_type = 'BASE TABLE' AND t.$NOT_FOREIGN
          -- 복합 PK = 연관 테이블. 실체가 아니라 관계다.
          AND (
              SELECT count(*) FROM pg_index i
              WHERE i.indrelid = (quote_ident(t.table_schema) || '.' || quote_ident(t.table_name))::regclass
                AND i.indisprimary
                AND array_length(i.indkey, 1) > 1
          ) = 0
          AND NOT EXISTS (
              SELECT 1 FROM information_schema.columns c
              WHERE c.table_schema = t.table_schema AND c.table_name = t.table_name
                AND c.column_name = required.name)
        ORDER BY 1
        """,
        schema,
    )

    /** SPEC-06 §1.2 — `id BIGINT GENERATED ALWAYS AS IDENTITY`. `serial` 은 시퀀스를 남긴다. */
    fun idNotAlwaysIdentity(conn: Connection, schema: String): List<String> = conn.query(
        """
        SELECT table_name || '.id 가 GENERATED ALWAYS AS IDENTITY 아님 (identity=' ||
               is_identity || ', generation=' || coalesce(identity_generation, '-') || ')'
        FROM information_schema.columns
        WHERE table_schema = ? AND column_name = 'id' AND $NOT_FOREIGN
          AND (is_identity <> 'YES' OR identity_generation <> 'ALWAYS')
        ORDER BY 1
        """,
        schema,
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
        WHERE t.table_schema = ? AND t.table_type = 'BASE TABLE' AND t.$NOT_FOREIGN
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
        schema,
    )

    /** SPEC-06 §1.1 — 테이블은 `snake_case` **단수형**. */
    fun badTableNames(conn: Connection, schema: String): List<String> = conn.query(
        """
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = ? AND table_type = 'BASE TABLE' AND $NOT_FOREIGN
          AND (table_name !~ '^[a-z][a-z0-9_]*$' OR table_name LIKE '%s')
        ORDER BY 1
        """,
        schema,
    ).filterNot { it in SINGULAR_ENDING_IN_S }
        .map { "$it — snake_case 단수형이 아니다" }

    /** SPEC-06 §1.1 — 불리언은 `is_` / `has_` 접두. */
    fun badBooleanNames(conn: Connection, schema: String): List<String> = conn.query(
        """
        SELECT table_name || '.' || column_name || ' — 불리언인데 is_/has_ 접두가 아니다'
        FROM information_schema.columns
        WHERE table_schema = ? AND $NOT_FOREIGN AND data_type = 'boolean'
          AND column_name !~ '^(is|has)_'
          AND $NOT_PREDICATE_BOOLEAN
        ORDER BY 1
        """,
        schema,
    )

    /** SPEC-06 §1.1 — 시각은 `_at` 접미, 날짜는 `_on` 접미. */
    fun badTemporalNames(conn: Connection, schema: String): List<String> = conn.query(
        """
        SELECT table_name || '.' || column_name || ' (' || data_type || ') — ' ||
               CASE WHEN data_type = 'date' THEN '_on 접미가 아니다' ELSE '_at 접미가 아니다' END
        FROM information_schema.columns
        WHERE table_schema = ? AND $NOT_FOREIGN
          AND ((data_type LIKE 'timestamp%' AND column_name !~ '_at$')
            OR (data_type = 'date' AND column_name !~ '_on$'))
        ORDER BY 1
        """,
        schema,
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
        WHERE table_schema = ? AND $NOT_FOREIGN
          AND data_type = 'timestamp without time zone'
        ORDER BY 1
        """,
        schema,
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
