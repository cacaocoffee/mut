-- ISSUE-026 — 감사 행위에 재료 승인을 더한다 (DECISIONS §1.3)
--
-- PRIN-T08 이 열거한 4종(발행 상태 전이 · 제휴 등급 · 큐레이션 순위 · 바 검증)에
-- 재료 승인은 없다. 그래도 남긴다 —
--   · 승인은 admin 만 하는 행위다 (SPEC-08 §2)
--   · 마스터가 오염되면 역검색과 바 연결이 무너진다 (PRIN-D01)
--   · "누가 이것을 통과시켰나" 가 남지 않으면 오염을 되짚을 수 없다
--
-- SPEC-06 §3.8 표를 넘어서는 확장이라 docs/prd/GAPS.md 에 근거를 남겼다 (G-29).
--
-- CHECK 를 지우고 다시 만든다. Postgres 는 CHECK 에 값을 덧붙이는 문법이 없고,
-- 열거를 코드(AuditAction)와 DB 양쪽에 두는 이상 둘이 같은지는 테스트가 본다
-- (AuditLogTest RED 12·22) — 한쪽만 늘면 런타임에 INSERT 가 터진다.

ALTER TABLE audit_log DROP CONSTRAINT ck_audit_log__action;

ALTER TABLE audit_log
    ADD CONSTRAINT ck_audit_log__action CHECK (action IN (
        'publish', 'unpublish', 'archive', 'restore',
        'tier_change', 'rank_change', 'verify',
        'slug_change_attempt',
        'approve'
    ));
