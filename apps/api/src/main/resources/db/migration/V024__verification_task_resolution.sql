-- ISSUE-028 — 무시 사유 (RED 18)
--
-- "고치지 않고 넘긴다"(dismissed)를 제공하되 **사유를 요구한다.**
--   · 무시를 아예 막으면 오탐 하나가 큐를 영원히 더럽힌다
--   · 사유 없이 열어 두면 큐가 조용히 비워지고, 왜 비워졌는지 아무도 모른다
-- 남길 것을 요구하는 쪽이 둘 다 피한다.
--
-- ## detail 에 넣지 않는 이유
--
-- 배치의 upsert 가 `detail = EXCLUDED.detail` 로 덮어쓴다 (V016 · PRIN-T07 멱등).
-- 같은 위반이 다시 걸리는 순간 사람이 적은 사유가 사라진다.
-- detail 은 **배치가 쓰는 칸**이고 이것은 **사람이 쓰는 칸**이라 자리를 나눈다.
--
-- ## 테이블 소유는 이슈 016 이다
--
-- 이슈 028 은 `migration: —` 로 편성됐지만 사유를 담을 자리가 없었다.
-- CONVENTIONS §4 에 따라 소유 이슈의 컬럼을 넓히는 변경으로 처리한다 —
-- 조용히 detail 에 섞는 것보다 낫다.

ALTER TABLE verification_task
    ADD COLUMN resolution VARCHAR(500);

COMMENT ON COLUMN verification_task.resolution IS
    'ISSUE-028 — dismissed 로 넘긴 사유. 사람이 쓴다 (detail 은 배치가 쓴다).';
