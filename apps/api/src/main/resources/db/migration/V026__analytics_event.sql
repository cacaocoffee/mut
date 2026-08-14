-- ISSUE-034 — 이벤트 수집 (SPEC-06 §3.8, SPEC-10, NFR-R-04)
--
-- 이슈 편성표는 V034 라 불렀다. 번호는 이슈가 아니라 **적용 순서**이고 앞이 V025 까지 썼다.
--
-- ## 왜 지금 만드나
--
-- SPEC-10 §1 — **이벤트는 소급이 안 된다.** 나중에 심으면 그 기간의 데이터가 영원히 없다.
-- 3개월 뒤 "유입이 늘었나" 를 물어도 비교할 과거가 없다. 화면이 셋뿐인 지금이 가장 싸다.

CREATE TABLE analytics_event (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY,

    event_type    VARCHAR(24)  NOT NULL,

    -- 클라이언트가 만든다. 30분 무활동이면 갱신된다 (SPEC-10 §3).
    -- 개인 식별자가 아니다 — 세션이 끊기면 같은 사람도 다른 값이 된다.
    session_id    UUID         NOT NULL,

    -- FK 를 걸지 않는다. audit_log 와 같은 이유다 (이슈 014):
    -- SPEC-08 §5.3 이 탈퇴 시 **NULL 익명화 · 행 유지**를 요구하는데,
    -- FK 가 있으면 삭제가 막히거나 ON DELETE 가 행을 지운다 — 둘 다 §5.3 위반이다.
    --
    -- audit_log 와 다른 점: 저쪽은 id 를 남기고 이쪽은 지운다.
    -- 발행 이력은 법적 근거라 주체가 필요하고, 집계는 주체 없이도 성립한다.
    user_id       BIGINT,

    -- 쿼리스트링을 제외한다 (SPEC-10 §3). 검색어·좌표가 섞여 들어오는 자리다.
    -- 서버가 절삭하지만 컬럼 폭으로도 한 번 더 막는다.
    path          VARCHAR(255),

    -- 원본 referrer URL 을 저장하지 않는다 (SPEC-10 §3).
    -- 유기 검색 비중을 세는 데는 분류값으로 충분하고, **원본에는 개인정보가 섞일 수 있다.**
    referrer_type VARCHAR(12),

    -- cross_nav (Phase 1b · SPEC-10 §5). 지금은 아무도 안 쓴다.
    -- 미리 두는 이유: 이벤트 테이블에 컬럼을 더하는 것은 나중에도 쉽지만,
    -- **그 사이에 쌓인 행에는 값이 영원히 없다.**
    from_type     VARCHAR(12),
    from_id       BIGINT,
    to_type       VARCHAR(12),
    to_id         BIGINT,

    payload       JSONB,

    -- 클라이언트 시각이다 (SPEC-10 §3). 서버 수신 시각은 created_at 이 갖는다 —
    -- 둘이 벌어지는 것 자체가 신호라 한 컬럼으로 합치지 않는다.
    occurred_at   TIMESTAMPTZ  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- SPEC-06 §3.8 — **월 단위 파티셔닝을 전제**로 설계한다.
    -- Phase 1a 는 단일 테이블이되 파티션 키가 될 컬럼을 PK 에 넣어 둔다:
    -- 나중에 쪼갤 때 PK 를 바꾸려면 테이블을 다시 만들어야 하고, 그때는 행이 수백만이다.
    PRIMARY KEY (id, occurred_at),

    CONSTRAINT ck_analytics_event__referrer_type CHECK (referrer_type IN
        ('organic', 'internal', 'social', 'direct', 'unknown'))
);

-- SPEC-06 §1.2 규약. append-only 라 실제로 불릴 일은 없지만 규약에서 빠지는 테이블을
-- 만들지 않는다 — 규약은 예외가 생기는 순간 규약이 아니다 (audit_log 와 같은 판단).
CREATE TRIGGER analytics_event_set_updated_at BEFORE UPDATE ON analytics_event
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- SPEC-06 §5 — 지표는 "기간 안의 이 이벤트" 로 읽는다.
CREATE INDEX ix_analytics_event__type_occurred_at
    ON analytics_event (event_type, occurred_at DESC);

-- 세션 단위 퍼널 (SPEC-10 §6 — 파인더 완주율 등).
CREATE INDEX ix_analytics_event__session_occurred_at
    ON analytics_event (session_id, occurred_at);

-- 탈퇴 익명화가 이 경로로 돈다 (SPEC-08 §5.3). 없으면 전수 스캔이다.
CREATE INDEX ix_analytics_event__user
    ON analytics_event (user_id) WHERE user_id IS NOT NULL;

COMMENT ON TABLE analytics_event IS
    'SPEC-10 — 자체 저장. IP · User-Agent · 좌표 컬럼이 없다 (§2 · §10, PRIN-D04).';
COMMENT ON COLUMN analytics_event.user_id IS
    'SPEC-08 §5.3 — 탈퇴 시 NULL 로 익명화하고 행은 남긴다. 그래서 FK 가 없다.';
COMMENT ON CONSTRAINT analytics_event_pkey ON analytics_event IS
    'SPEC-06 §3.8 — occurred_at 을 포함해 월 파티셔닝을 나중에 붙일 수 있게 한다.';
