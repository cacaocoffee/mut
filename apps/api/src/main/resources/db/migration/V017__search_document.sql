-- ISSUE-017 — 통합 검색 색인 (SPEC-06 §3.8 · §5, FR-SEARCH-006·007, R-F2.1-3·4)
--
-- R-F5-1(타입별 그룹핑) · R-F2.1-3(한/영 별칭) · R-F2.1-4(초성)를 한 테이블로 받는다.
-- 별도 검색엔진은 코퍼스가 수천 건을 넘을 때 검토한다 (SPEC-05 §6) — Postgres 만으로 간다.

CREATE TABLE search_document (
    -- 실체가 아니라 투영이다. 원본의 (타입, 식별자) 가 그대로 PK 라
    -- 대리키 id 를 붙일 자리가 없다 — 붙이면 같은 엔티티가 두 번 색인된다.
    entity_type  VARCHAR(12)  NOT NULL,
    entity_id    BIGINT       NOT NULL,

    slug         VARCHAR(120) NOT NULL,
    name_ko      VARCHAR(120) NOT NULL,
    name_en      VARCHAR(120),

    -- SPEC-06 §1.4 의 배열 예외. 검색 전용이고 무결성 대상이 아니다.
    aliases      TEXT[]       NOT NULL DEFAULT '{}',

    -- R-F2.1-4 — 저장 시점에 분해한다. 조회 때 계산하면 인덱스가 안 붙는다 (G-13).
    chosung      TEXT         NOT NULL DEFAULT '',

    -- 산정식은 미정이다 (SPEC-06 §7 · G-13). entity_type 별 고정값으로 시작한다
    -- (DECISIONS §1.9). 정본은 SearchEntityType.defaultWeight 다.
    weight       SMALLINT     NOT NULL DEFAULT 0,

    -- 회수·보관은 행을 지우지 않고 이 플래그를 내린다. 지우면 회수 상태의 이름 변경이
    -- 색인에 반영되지 않아, 재발행하는 순간 낡은 이름이 검색에 뜬다.
    is_published BOOLEAN      NOT NULL DEFAULT false,

    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    PRIMARY KEY (entity_type, entity_id)
);

-- SPEC-06 §1.3 — 네이티브 ENUM 이 아니라 VARCHAR + CHECK.
-- 4종을 지금 다 정의한다. 나중에 늘리면 클라이언트의 그룹 렌더링(R-F5-1)이 깨진다.
-- bar 는 Phase 1b, article 은 Phase 2 라 아직 아무도 발행하지 않는다.
ALTER TABLE search_document
    ADD CONSTRAINT ck_search_document__entity_type CHECK (
        entity_type IN ('cocktail', 'bar', 'ingredient', 'article')
    );

-- SPEC-06 §1.2 — updated_at 은 트리거로 갱신한다.
CREATE TRIGGER search_document_set_updated_at BEFORE UPDATE ON search_document
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── 인덱스 (SPEC-06 §5) ─────────────────────────────────────────────────────
-- pg_trgm 은 V001 에서 이미 설치됐다.
--
-- B-tree 로는 초성 프리픽스가 안 된다 (G-13) — LIKE '%ㅁㄹㄱ%' 가 선두 와일드카드라
-- 정렬로 좁힐 수 없다. 트라이그램은 부분 문자열에서 인덱스를 낸다.
CREATE INDEX ix_search_document__chosung ON search_document USING GIN (chosung gin_trgm_ops);
CREATE INDEX ix_search_document__aliases ON search_document USING GIN (aliases);

-- 타입별 그룹핑(R-F5-1)은 공개된 것만 센다.
CREATE INDEX ix_search_document__published_type ON search_document (is_published, entity_type);

COMMENT ON TABLE search_document IS
    'SPEC-06 §3.8 — 투영이다. 정본은 각 도메인 테이블이고, 여기는 도메인 이벤트로만 갱신된다 (SPEC-05 §3).';
COMMENT ON COLUMN search_document.chosung IS
    'R-F2.1-4 — 이름·별칭의 초성을 공백으로 이어 붙인다. 분해는 저장 시점에 한다.';
COMMENT ON COLUMN search_document.weight IS
    'G-13 미정 — entity_type 별 고정값 (DECISIONS §1.9). 산정식이 정해지면 교체한다.';
