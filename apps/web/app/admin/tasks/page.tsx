import Link from "next/link";
import { requireAdmin } from "@/lib/admin-session";
import { verificationTasks } from "@/lib/admin-api";
import { TaskResolve } from "@/components/admin/task-resolve";

/**
 * 검증 태스크 큐 (ISSUE-048 · `FR-ADMIN-004`).
 *
 * ## `editor` 도 본다
 *
 * SPEC-08 §2 — "검증 태스크 처리" 는 `editor`·`admin` 둘 다다. 재료 승인·감사 조회와
 * 다른 점이고, 그래서 이 화면에는 역할 분기가 없다.
 *
 * ## 대상으로 가는 길은 서버가 준다
 *
 * `adminPath` 를 그대로 링크한다 (이슈 028). 화면에서 `entityType` 으로 주소를 조립하면
 * 태스크 유형이 늘 때마다 여기가 밀린다 — 1b 의 영업시간 만료·인스타 폐업 신호가 그렇다.
 *
 * ## 없는 유형을 골라도 깨지지 않는다
 *
 * 아직 배치가 만들지 않는 유형(1b)을 골라도 빈 목록일 뿐이다 (RED 13). 목록을 화면에서
 * 걸러 내지 않고 서버 질의로 넘기기 때문이다.
 */
export const dynamic = "force-dynamic";

const STATUSES = [
  { value: "open", ko: "열림" },
  { value: "resolved", ko: "고침" },
  { value: "dismissed", ko: "넘김" },
] as const;

/** 배치가 만드는 유형 (`FR-ADMIN-004`). 1b 것도 골라 둘 수 있다 — 아직 비어 있을 뿐이다. */
const TASK_TYPES = [
  { value: "", ko: "전체" },
  { value: "invariant_violation", ko: "불변식 위반" },
  { value: "gate_bypass", ko: "발행 게이트 우회" },
  { value: "slug_changed", ko: "슬러그 변경" },
  { value: "hours_expired", ko: "영업시간 만료" },
  { value: "instagram_signal", ko: "인스타그램 폐업 신호" },
] as const;

export default async function AdminTasks({
  searchParams,
}: {
  searchParams: Promise<{ status?: string; taskType?: string }>;
}) {
  await requireAdmin();

  const params = await searchParams;
  const status = params.status ?? "open";
  const taskType = params.taskType ?? "";

  const tasks = await verificationTasks({ status, taskType: taskType || undefined });

  return (
    <>
      <h2 className="admin__section-head">검증 태스크 {tasks.length}건</h2>

      {/* 자바스크립트 없이도 걸린다 — 주소가 곧 필터라 링크로 공유된다 */}
      <form className="admin-filters" method="get">
        <label className="admin-field">
          <span className="admin-field__label">상태</span>
          <select name="status" defaultValue={status}>
            {STATUSES.map((s) => (
              <option key={s.value} value={s.value}>
                {s.ko}
              </option>
            ))}
          </select>
        </label>
        <label className="admin-field">
          <span className="admin-field__label">유형</span>
          <select name="taskType" defaultValue={taskType}>
            {TASK_TYPES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.ko}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="btn btn-ghost">
          거르기
        </button>
      </form>

      {tasks.length === 0 ? (
        <p className="admin__empty">
          이 조건에 해당하는 태스크가 없습니다. 상태를 <b>열림</b> 으로 두면 지금 처리할 것만
          보입니다.
        </p>
      ) : (
        <ul className="admin__list">
          {tasks.map((task) => (
            <li key={task.id}>
              <div className="admin__section-head--row">
                <b>
                  {/* RED 12 — 무엇을 어긴 것인지는 문구가 아니라 코드가 말한다 */}
                  <code>{task.code}</code> {task.entityType} #{task.entityId}
                </b>
                <span className="admin-field__hint">
                  {new Date(task.detectedAt).toLocaleString("ko-KR")}
                </span>
              </div>
              <span>
                {task.taskType} · {task.status}
                {task.resolution ? ` · ${task.resolution}` : ""}
              </span>

              {task.adminPath ? (
                <Link href={task.adminPath}>대상 열기 →</Link>
              ) : (
                <span className="admin-field__hint">대상 화면이 아직 없습니다</span>
              )}

              {task.status === "open" ? <TaskResolve id={task.id} /> : null}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
