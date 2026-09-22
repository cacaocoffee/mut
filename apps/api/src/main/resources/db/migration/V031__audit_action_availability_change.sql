-- ISSUE #213 — 감사 행위에 유통 여부 자동 변경을 더한다 (G-41).
--
-- 매칭 배치가 승인된 유통 제품을 세어 재료의 domestic_availability 를 common·specialty 로
-- **올릴 때** 남긴다. 사람이 아니라 배치가 바꾸는 값이라 더더욱 남겨야 한다 —
-- "왜 이 재료가 갑자기 common 이 됐나" 에 답할 것이 이것뿐이다. 내리는 방향은 배치가 하지 않는다.
--
-- V023 과 같은 방식 — CHECK 를 지우고 다시 만든다. 열거는 AuditAction 과 AuditLogTest 가 맞춘다.

ALTER TABLE audit_log DROP CONSTRAINT ck_audit_log__action;

ALTER TABLE audit_log
    ADD CONSTRAINT ck_audit_log__action CHECK (action IN (
        'publish', 'unpublish', 'archive', 'restore',
        'tier_change', 'rank_change', 'verify',
        'slug_change_attempt',
        'approve',
        'availability_change'
    ));
