-- ISSUE-016 — 검증 태스크 (SPEC-06 §4.3, FR-ADMIN-004, NFR-D-01·D-02·D-04)
--
-- 앱이 강제하는 불변식은 DB 가 못 막는다 (조건부이거나 자식 행 개수라서).
-- 그래서 일 1회 전수 스캔해 위반을 여기에 쌓고, 사람이 처리한다.
-- 24종일 때는 눈으로 보이지만 500종이 되면 이것 말고 확인할 방법이 없다.

CREATE TABLE verification_task (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- invariant_violation · gate_bypass · slug_changed
    -- Phase 1b 의 hours_expired · instagram_signal 도 같은 테이블을 쓴다 (SPEC-05 §8)
    task_type   VARCHAR(32) NOT NULL,
    entity_type VARCHAR(24) NOT NULL,
    entity_id   BIGINT      NOT NULL,

    /* INV-COCKTAIL-02 · GATE-COCKTAIL-01 같은 SPEC-02 ID */
    code        VARCHAR(40) NOT NULL,
    detail      JSONB,

    status      VARCHAR(12) NOT NULL DEFAULT 'open',
    detected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    resolved_by BIGINT,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE verification_task
    ADD CONSTRAINT ck_verification_task__status
        CHECK (status IN ('open', 'resolved', 'dismissed'));

-- 멱등의 핵심이다 (`PRIN-T07`, RED 22·23).
--
-- 배치는 매일 같은 위반을 다시 본다. 유니크가 없으면 하루에 한 줄씩 쌓여
-- 태스크 큐가 같은 문제의 사본으로 가득 찬다. 있으면 두 번째부터는
-- INSERT 가 충돌하고, 배치는 그 충돌을 "이미 아는 위반"으로 읽는다.
ALTER TABLE verification_task
    ADD CONSTRAINT uq_verification_task__occurrence
        UNIQUE (task_type, entity_type, entity_id, code);

-- 큐 화면이 여는 순서 (이슈 028). 열린 것부터, 최근 순.
CREATE INDEX ix_verification_task__status_detected_at
    ON verification_task (status, detected_at DESC);

CREATE TRIGGER verification_task_set_updated_at BEFORE UPDATE ON verification_task
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE verification_task IS
    'SPEC-06 §4.3 — 앱 강제 불변식의 배치 이중 확인 결과. FR-ADMIN-004 의 큐가 읽는다.';
COMMENT ON CONSTRAINT uq_verification_task__occurrence ON verification_task IS
    'PRIN-T07 — 배치 멱등. 같은 위반은 몇 번을 돌려도 한 줄이다.';
