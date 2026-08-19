import { requireAdminRole } from "@/lib/admin-session";
import { auditLogs, type AuditLogEntry } from "@/lib/admin-api";

/**
 * 감사 로그 (ISSUE-048 · `FR-ADMIN-005` · SPEC-08 §2.2).
 *
 * ## `admin` 만이다
 *
 * SPEC-08 §2.2 가 권한 분리를 **중립성 장치**로 규정했다 — `editor` 는 큐레이션을 만드는
 * 사람이라, 감시받는 사람이 감시 기록을 보면 안 된다. 무엇이 기록되는지 아는 사람은
 * 기록되지 않는 방법도 알게 된다. 메뉴에서 숨기고(`AdminNav`) 주소를 쳐도 404 다
 * (`requireAdminRole`). 서버도 `Action.VIEW_AUDIT_LOG` 로 막는다 (이슈 029).
 *
 * ## 고칠 수 없다
 *
 * 수정·삭제 버튼이 **없다** (RED 19). API 에도 그 경로가 없고 앱 역할에 `UPDATE`·`DELETE`
 * 권한 자체가 없다 (`V014`). `PRIN-T08` 이 요구하는 "되돌릴 수 있어야 하고 다툼의 근거가
 * 돼야 한다" 는 고쳐 쓸 수 없을 때만 성립한다.
 */
export const dynamic = "force-dynamic";

export default async function AdminAudit({
  searchParams,
}: {
  searchParams: Promise<{
    entityType?: string;
    action?: string;
    actorUserId?: string;
    from?: string;
    to?: string;
  }>;
}) {
  await requireAdminRole();

  const filter = await searchParams;
  const { items, total } = await auditLogs(filter);

  return (
    <>
      <h2 className="admin__section-head">
        감사 로그 — 전체 {total}건 중 {items.length}건
      </h2>

      {/* 필터는 AND 로 묶인다. 주소가 곧 조건이라 조사 중인 화면을 그대로 공유할 수 있다 */}
      <form className="admin-filters" method="get">
        <label className="admin-field">
          <span className="admin-field__label">대상</span>
          <input name="entityType" defaultValue={filter.entityType ?? ""} placeholder="cocktail" />
        </label>
        <label className="admin-field">
          <span className="admin-field__label">행위</span>
          <input name="action" defaultValue={filter.action ?? ""} placeholder="publish" />
        </label>
        <label className="admin-field">
          <span className="admin-field__label">행위자 id</span>
          <input name="actorUserId" defaultValue={filter.actorUserId ?? ""} inputMode="numeric" />
        </label>
        <label className="admin-field">
          <span className="admin-field__label">이 시각 이후</span>
          <input name="from" type="datetime-local" defaultValue={filter.from ?? ""} />
        </label>
        <label className="admin-field">
          <span className="admin-field__label">이 시각 이전</span>
          <input name="to" type="datetime-local" defaultValue={filter.to ?? ""} />
        </label>
        <button type="submit" className="btn btn-ghost">
          거르기
        </button>
      </form>

      {items.length === 0 ? (
        <p className="admin__empty">이 조건에 해당하는 기록이 없습니다. 조건을 넓혀 보세요.</p>
      ) : (
        <ul className="admin__list">
          {items.map((entry) => (
            <li key={entry.id}>
              <div className="admin__section-head--row">
                <b>
                  {entry.action} — {entry.entityType} #{entry.entityId}
                </b>
                <span className="admin-field__hint">
                  {new Date(entry.at).toLocaleString("ko-KR")}
                </span>
              </div>
              <span>{actorLabel(entry)}</span>
              <Diff entry={entry} />
            </li>
          ))}
        </ul>
      )}

      {/* 왜 고치는 버튼이 없는지 적어 둔다. 없으면 "빠뜨린 것" 으로 읽혀 언젠가 생긴다 */}
      <p className="admin__foot">
        감사 로그는 덧붙이기만 합니다 — 고치거나 지울 수 없습니다 (<code>PRIN-T08</code>).
      </p>
    </>
  );
}

/**
 * 행위자 표시. **탈퇴해도 id 는 남는다** (SPEC-08 §5.3).
 *
 * 이름을 지우고 id 만 남기는 것이 개인정보 요구이고, 기록 자체는 남아야 다툼의 근거가
 * 된다. 그래서 "탈퇴한 사용자" 라고 쓰고 id 를 함께 보여 준다
 * ([DECISIONS §1.11](../../../../docs/issues/DECISIONS.md)).
 */
function actorLabel(entry: AuditLogEntry): string {
  const actor = entry.actor;
  if (!actor) return "행위자 기록 없음";
  if (actor.withdrawn) return `탈퇴한 사용자 (#${actor.userId})`;
  return `${actor.displayName ?? "이름 없음"} (#${actor.userId})`;
}

/**
 * 무엇이 바뀌었는지 (RED 17).
 *
 * 바뀐 필드만 보여 준다 — 통째로 두 덩이를 늘어놓으면 사람이 눈으로 비교해야 하고,
 * 그러면 감사 기록을 열어도 무슨 일이 있었는지 모른다.
 *
 * 계약에서 `before`·`after` 는 `JsonNode` 라 모양이 정해져 있지 않다 (테이블마다 다르다).
 * 생성 타입이 빈 객체라 여기서 한 번만 넓혀 읽는다.
 */
function Diff({ entry }: { entry: AuditLogEntry }) {
  const before = (entry.before ?? {}) as Record<string, unknown>;
  const after = (entry.after ?? {}) as Record<string, unknown>;

  const keys = [...new Set([...Object.keys(before), ...Object.keys(after)])].filter(
    (key) => stringify(before[key]) !== stringify(after[key]),
  );

  if (keys.length === 0) return null;

  return (
    <table className="admin-diff">
      <thead>
        <tr>
          <th scope="col">항목</th>
          <th scope="col">이전</th>
          <th scope="col">이후</th>
        </tr>
      </thead>
      <tbody>
        {keys.map((key) => (
          <tr key={key}>
            <th scope="row">{key}</th>
            <td>{stringify(before[key]) ?? "—"}</td>
            <td>{stringify(after[key]) ?? "—"}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function stringify(value: unknown): string | undefined {
  if (value === undefined || value === null) return undefined;
  return typeof value === "string" ? value : JSON.stringify(value);
}
