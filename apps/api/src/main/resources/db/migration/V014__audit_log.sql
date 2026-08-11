-- ISSUE-014 — 감사 로그 (SPEC-06 §3.8, PRIN-T08, NFR-O-05)
--
-- "누가 · 언제 · 무엇을 바꿨는지" 를 재구성할 수 있어야 한다 (NFR-O-05).
-- 되돌릴 수 있어야 하고, 다툼이 생겼을 때 근거가 된다.

CREATE TABLE audit_log (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_type   VARCHAR(24) NOT NULL,
    entity_id     BIGINT      NOT NULL,
    action        VARCHAR(32) NOT NULL,

    -- FK 를 걸지 않는다. SPEC-08 §5.3 이 "탈퇴해도 actor_user_id 는 유지" 라고 했고,
    -- 같은 절이 user 행 즉시 삭제도 요구한다. FK 가 있으면 둘 다 만족할 수 없다 —
    -- 삭제가 FK 위반으로 막히거나, ON DELETE 가 이력을 지운다.
    -- SPEC-06 §3.8 의 컬럼 표도 FK 를 명시하지 않았다. 느슨한 참조로 간다.
    actor_user_id BIGINT,

    before        JSONB,
    after         JSONB,

    -- SPEC-06 §3.8 은 이 자리를 `at` 이라 불렀지만 §1.2 의 공통 컬럼 규약과 §6 의
    -- 명명 규칙(시각은 `_at` 접미)이 우선한다 — `at` 은 접미가 아니라 이름 전체다.
    -- 별도 컬럼을 두지 않은 이유: 감사 행은 행위와 같은 트랜잭션에서 한 번 쓰이고
    -- 다시는 바뀌지 않는다. **생성 시각이 곧 행위 시각이다.** (GAPS G-26)
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- SPEC-06 §1.2 — 실체 테이블마다 건다. append-only 라 실제로 불릴 일은 없지만,
-- 규약에서 빠지는 테이블을 만들지 않는다. 규약은 예외가 생기는 순간 규약이 아니다.
CREATE TRIGGER audit_log_set_updated_at BEFORE UPDATE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- SPEC-06 §3.8 의 5종 + 상태 전이에 필요한 archive·restore
-- + slug 변경 시도(NFR-D-04 "즉시 조사" 의 근거가 되려면 거부된 시도도 남아야 한다).
--
-- 값을 지금 전부 넣어 두는 이유: Phase 1b·2 에서 늘리면 그때 이 목록을 읽는 쪽이 깨진다.
-- SPEC-06 §3.8 표를 넘어서는 확장이라 docs/prd/GAPS.md 에 근거를 남겼다.
ALTER TABLE audit_log
    ADD CONSTRAINT ck_audit_log__action CHECK (action IN (
        'publish', 'unpublish', 'archive', 'restore',
        'tier_change', 'rank_change', 'verify',
        'slug_change_attempt'
    ));

-- SPEC-06 §5 — 한 엔티티의 이력을 시간순으로 훑는다 (NFR-O-05 재구성).
-- 명세는 `(entity_type, entity_id, at)` 이고, 그 `at` 이 여기서는 `created_at` 이다.
CREATE INDEX ix_audit_log__entity_at ON audit_log (entity_type, entity_id, created_at);

-- PRIN-T08 — 감사 로그는 append-only 다.
--
-- SPEC-06 §4.1 의 보호 테이블 목록에 audit_log 는 없지만, "되돌릴 수 있어야 하고
-- 다툼의 근거가 돼야 한다" 는 취지는 고쳐 쓸 수 없을 때만 성립한다.
-- 고칠 수 있는 이력은 이력이 아니다. DELETE 뿐 아니라 UPDATE 도 회수한다.
REVOKE UPDATE, DELETE ON audit_log FROM kcocktail_app;

COMMENT ON TABLE audit_log IS
    'PRIN-T08 — append-only. 앱 역할에 UPDATE·DELETE 권한이 없다.';
COMMENT ON COLUMN audit_log.actor_user_id IS
    'SPEC-08 §5.3 — 탈퇴해도 유지한다. 그래서 FK 가 없다.';
