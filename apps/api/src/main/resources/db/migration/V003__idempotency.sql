-- ISSUE-003 — 멱등 키 저장소 (SPEC-07 §1.7, `PRIN-T07`)
--
-- 재시도가 전제인 요청(이벤트 수집 등)이 같은 키로 두 번 오면 부수효과는 한 번만 일어나고
-- 두 번째는 첫 응답을 그대로 돌려받는다.

CREATE TABLE idempotency_key (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- 클라이언트가 보낸 Idempotency-Key 헤더. 선점 자체가 UNIQUE 로 직렬화된다.
    key                  VARCHAR(120) NOT NULL,

    -- method + 경로 + 본문의 해시. 같은 키에 다른 본문이 오면 거부한다 (키 재사용 방지).
    request_fingerprint  VARCHAR(64)  NOT NULL,

    -- 처리가 끝나야 채워진다. NULL 이면 아직 진행 중이다.
    response_status      SMALLINT,

    -- TEXT 다. JSONB 로 두면 Postgres 가 키 순서를 바꾸고 공백을 정규화해서
    -- 재생본이 최초 응답과 **바이트가 달라진다.** SPEC-07 §1.7 은 "첫 결과를 그대로"라고 했고,
    -- 클라이언트가 응답을 해시하거나 서명을 검증하면 그 차이가 실패로 나타난다.
    -- 응답 본문을 질의할 일은 없으므로 jsonb 의 이점도 없다.
    response_body        TEXT,
    completed_at         TIMESTAMPTZ
);

-- 선점을 이 제약이 담당한다. 두 요청이 동시에 와도 하나만 INSERT 에 성공한다.
CREATE UNIQUE INDEX ux_idempotency_key__key ON idempotency_key (key);

-- 만료 정리 배치가 쓸 인덱스. TTL 정리는 별도 이슈다.
CREATE INDEX ix_idempotency_key__created_at ON idempotency_key (created_at);

-- SPEC-06 §1.2 — updated_at 은 트리거가 정본이다.
CREATE TRIGGER idempotency_key_set_updated_at BEFORE UPDATE ON idempotency_key
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE idempotency_key IS
    'SPEC-07 §1.7 — 재시도 안전. 보호 테이블이 아니므로 만료분은 DELETE 로 정리한다.';
