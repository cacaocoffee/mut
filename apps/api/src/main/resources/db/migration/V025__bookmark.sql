-- ISSUE-031 — 북마크 · 컬렉션 · 공유 링크 (SPEC-06 §3.5, FR-USER-004, R-F5-2)
--
-- 이슈 편성표는 이 파일을 V031 이라 불렀다. 번호는 **이슈 번호가 아니라 적용 순서**이고
-- 앞 웨이브가 V024 까지 썼다 (SPEC-06 §6 — 앞으로만 간다). 실제 순번을 쓴다.

CREATE TABLE bookmark_collection (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- SPEC-08 §5.3 — 탈퇴하면 즉시 삭제된다. audit_log 와 정반대인데 근거가 다르다:
    -- 발행 이력은 법적 근거라 남기고, 북마크는 순수한 개인 취향 기록이라 남길 이유가 없다.
    user_id     BIGINT      NOT NULL REFERENCES "user" ON DELETE CASCADE,

    name        VARCHAR(60) NOT NULL,

    -- R-F5-2 공유 링크. 순차 id 를 쓰면 남의 컬렉션을 1 부터 훑을 수 있다 (RED 20).
    -- NULL 이면 아직 공유하지 않은 컬렉션이다 — UNIQUE 는 NULL 을 여럿 허용한다.
    share_token VARCHAR(64) UNIQUE,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 이름이 공백만 있으면 없는 것으로 친다 (RED 17). NOT NULL 만 걸면 스페이스 하나로 통과한다.
    CONSTRAINT ck_bookmark_collection__name CHECK (name ~ '\S')
);

CREATE TRIGGER bookmark_collection_set_updated_at BEFORE UPDATE ON bookmark_collection
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 내 컬렉션 목록. 사용자당 몇 개 수준이지만 목록 조회가 이 경로다.
CREATE INDEX ix_bookmark_collection__user ON bookmark_collection (user_id, created_at DESC);

CREATE TABLE bookmark (
    id            BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES "user" ON DELETE CASCADE,

    -- SPEC-06 §3.5 — NULL 이면 기본 컬렉션이다 (RED 14).
    -- 기본 컬렉션 행을 따로 만들지 않는 이유: 가입할 때마다 행을 하나 심어야 하고,
    -- 그 행이 없는 계정(마이그레이션 이전·직접 INSERT)이 생기면 조회가 NULL 을 만난다.
    -- 어차피 NULL 을 다뤄야 한다면 그것을 기본값으로 삼는 편이 갈래가 하나 적다.
    --
    -- SET NULL 이다. 컬렉션을 지운다고 북마크까지 사라지면 "정리하려다 저장한 것을 잃는다".
    collection_id BIGINT      REFERENCES bookmark_collection ON DELETE SET NULL,

    -- R-F5-2 — 한 컬렉션에 세 종류를 섞어 담는다. 그래서 타입별 테이블로 쪼개지 않았다.
    target_type   VARCHAR(12) NOT NULL,

    -- ⚠️ **FK 가 없다.** 다형 참조라 걸 수 없다 (SPEC-06 §3.5 가 명시한 의도적 선택).
    --
    -- 대안 둘을 다 버렸다:
    --   · 타입별 컬럼 3개 + CHECK — 컬럼이 늘 때마다 스키마가 바뀌고, 조회가 COALESCE 범벅이 된다
    --   · 타입별 테이블 3개 — 컬렉션이 세 종류를 **섞어 담아야** 해서(R-F5-2) UNION 없이는 못 읽는다
    --
    -- 대가는 dangling 참조다. 참조 무결성은 **앱이 책임진다**:
    --   · 추가 시점에 대상이 발행돼 있는지 확인한다 (RED 28)
    --   · 조회 시점에 없어진 대상을 걸러 낸다 (RED 26·27) — 행은 지우지 않는다.
    --     archived 는 되돌아올 수 있고, 그때 저장해 둔 것이 살아 있어야 한다.
    target_id     BIGINT      NOT NULL,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_bookmark__target_type CHECK (target_type IN ('cocktail', 'bar', 'article')),

    -- SPEC-06 §3.5 — 같은 것을 두 번 저장할 수 없다.
    -- **컬렉션이 키에 없는 것이 의도다.** 있으면 같은 칵테일을 컬렉션마다 하나씩 담을 수 있고,
    -- 그러면 "저장함/안 함" 이 한 값으로 안 나온다 — 상세 화면의 저장 버튼이 무엇을 보여 줄지
    -- 알 수 없어진다. 한 번 저장하고 컬렉션을 옮기는 모델이다.
    CONSTRAINT uq_bookmark__user_target UNIQUE (user_id, target_type, target_id)
);

CREATE TRIGGER bookmark_set_updated_at BEFORE UPDATE ON bookmark
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 내 북마크 목록과 컬렉션별 조회. SPEC-06 §5 의 (user_id, collection_id) 다.
CREATE INDEX ix_bookmark__user_collection ON bookmark (user_id, collection_id, created_at DESC);

COMMENT ON COLUMN bookmark.target_id IS
    'SPEC-06 §3.5 — 다형 참조라 FK 가 없다. 무결성은 앱이 진다 (추가 시 검증 · 조회 시 필터).';
COMMENT ON COLUMN bookmark.collection_id IS
    'NULL 이면 기본 컬렉션 (SPEC-06 §3.5).';
COMMENT ON COLUMN bookmark_collection.share_token IS
    'R-F5-2 공유 링크. 추측 불가능해야 한다 — 순차 id 를 쓰지 않는 이유.';
