-- ISSUE-005 — 사용자와 역할 (SPEC-06 §3.5, FR-USER-001)
--
-- 여기 **없는 것**이 있는 것만큼 중요하다.
--   · 성인 인증 컬럼 없음 (ADR-0004 — 판매를 하지 않으므로 전면 인증을 요구하지 않는다)
--   · 위치 좌표 컬럼 없음 (PRIN-D04 — 좌표는 요청 스코프에만 산다. 세션에도 저장하지 않는다)
--   · provider 갱신 토큰 없음 (DECISIONS §1 — 세션이 우리 것이다)
-- 부재는 주석으로만 두면 다음 이슈가 조용히 추가한다. SchemaAbsenceTest 가 지킨다.

-- `user` 는 Postgres 예약어라 따옴표가 필요하다.
-- **테이블명을 app_user 로 바꾸지 않는다** — SPEC-06 §3.5 가 `user` 로 명시했고,
-- 이름을 바꾸면 스펙과 코드가 어긋나기 시작한다. 따옴표 한 쌍이 더 싸다.
CREATE TABLE "user" (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    provider      VARCHAR(12)  NOT NULL,
    provider_uid  VARCHAR(120) NOT NULL,
    display_name  VARCHAR(60)  NOT NULL,

    -- 애플 비공개 릴레이는 이메일을 주지 않는다 (SPEC-08 §4.2). NOT NULL 로 두면 로그인이 막힌다.
    email         VARCHAR(255),

    CONSTRAINT ck_user__provider CHECK (provider IN ('kakao', 'naver', 'apple')),
    CONSTRAINT uq_user__provider_uid UNIQUE (provider, provider_uid)
);

CREATE TRIGGER user_set_updated_at BEFORE UPDATE ON "user"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE "user" IS
    'SPEC-06 §3.5. 성인 인증(ADR-0004)·위치(PRIN-D04) 컬럼을 두지 않는다.';

-- 역할을 `user` 의 컬럼이 아니라 별도 테이블로 둔다 (SPEC-06 §3.5).
--
-- 팀 규모가 작아 **한 사람이 에디터이면서 관리자인 경우가 실제로 생긴다.**
-- 단일 컬럼이면 둘 중 하나를 포기해야 한다.
--
-- 역할은 누적되지 않는다 (SPEC-08 §1) — `editor` 가 `admin` 권한을 갖지 않는다.
-- 필요하면 두 행을 준다.
CREATE TABLE user_role (
    user_id     BIGINT      NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    role        VARCHAR(16) NOT NULL,

    -- 누가 줬는지. 부여자가 탈퇴해도 이력은 남긴다 (SET NULL).
    granted_by  BIGINT      REFERENCES "user" (id) ON DELETE SET NULL,
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 복합 PK 가 "같은 역할 중복 부여"를 막는다. 그리고 SPEC-06 §5 대로
    -- 인증할 때 user_id 로 역할을 훑는 경로의 인덱스가 된다.
    PRIMARY KEY (user_id, role),
    CONSTRAINT ck_user_role__role CHECK (role IN ('member', 'editor', 'partner_owner', 'admin'))
);

COMMENT ON TABLE user_role IS
    'SPEC-08 §1 — 역할은 누적되지 않는다. editor 가 admin 을 포함하지 않는다.';

-- Spring Session JDBC. SPEC-08 §9 가 "Phase 1 은 DB 세션으로 충분"이라고 했다.
-- Redis 를 지금 들이지 않는다 — 인스턴스가 늘면 재검토한다.
--
-- 스키마는 Spring Session 이 정한 모양 그대로다. SPEC-06 §1.2 의 공통 컬럼 규약을
-- 따르지 않는 이유가 여기 있다 — 우리 테이블이 아니라 **라이브러리의 계약**이라
-- 컬럼을 더하면 라이브러리 쿼리가 깨진다. SchemaLint 도 이 둘을 예외로 둔다.
CREATE TABLE spring_session (
    primary_id            CHAR(36)     NOT NULL,
    session_id            CHAR(36)     NOT NULL,
    creation_time         BIGINT       NOT NULL,
    last_access_time      BIGINT       NOT NULL,
    max_inactive_interval INT          NOT NULL,
    expiry_time           BIGINT       NOT NULL,
    principal_name        VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36)     NOT NULL,
    attribute_name     VARCHAR(200) NOT NULL,
    attribute_bytes    BYTEA        NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);

-- 세션 테이블은 앱이 직접 지운다 (만료 정리·로그아웃·역할 변경 시 즉시 무효화).
GRANT SELECT, INSERT, UPDATE, DELETE ON spring_session, spring_session_attributes TO kcocktail_app;
