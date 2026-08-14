import { revalidatePath } from "next/cache";

/**
 * 발행 시 on-demand 재생성 (ISSUE-038 · SPEC-07 §4).
 *
 * 어드민에서 발행하면 API 가 이 경로를 부른다 (이슈 015 가 부르는 쪽이다).
 * **에디터가 발행하고 반영을 기다리지 않아야 한다** — `NFR-O-02` 가 30초를 준다.
 *
 * ```
 * POST /api/revalidate
 * X-Revalidate-Secret: <공유 시크릿>
 * { "paths": ["/cocktails/negroni", "/cocktails/base/gin"] }
 * ```
 *
 * ## 시크릿이 번들에 들어가면 안 된다
 *
 * `process.env.REVALIDATE_SECRET` 에 `NEXT_PUBLIC_` 접두가 **없다.**
 * 붙이면 클라이언트 번들에 그대로 실려 나가고, 그러면 누구나 재생성을 부를 수 있다.
 *
 * ## 실패해도 200 을 주지 않는다
 *
 * 재생성 훅이 실패하면 발행은 유지되고 프론트만 낡는다 (`NFR-R-03`). 그래도 여기서
 * 조용히 200 을 주면 **부르는 쪽이 성공으로 알고 재시도하지 않는다** — 낡은 채로 굳는다.
 */
export async function POST(request: Request): Promise<Response> {
  const secret = process.env.REVALIDATE_SECRET;

  if (!secret) {
    // 설정을 빠뜨린 채 열려 있으면 누구나 재생성을 부를 수 있다. 열지 않는다.
    console.error("[revalidate] REVALIDATE_SECRET 이 없다 — 요청을 거부한다");
    return new Response(null, { status: 503 });
  }

  if (request.headers.get("X-Revalidate-Secret") !== secret) {
    // 어느 경로를 요청했는지 남기지 않는다. 시도 자체가 신호이고, 경로는 공개 정보가 아니다.
    console.warn("[revalidate] 시크릿 불일치 — 거부");
    return new Response(null, { status: 401 });
  }

  let paths: unknown;
  try {
    ({ paths } = (await request.json()) as { paths?: unknown });
  } catch {
    return Response.json({ error: "본문이 JSON 이 아닙니다" }, { status: 400 });
  }

  if (!Array.isArray(paths) || paths.some((p) => typeof p !== "string")) {
    return Response.json({ error: "paths 는 문자열 배열이어야 합니다" }, { status: 400 });
  }

  // 하나가 실패해도 나머지를 재생성한다 — 발행 하나 때문에 다른 경로가 낡을 이유가 없다.
  const failed: string[] = [];
  for (const path of paths as string[]) {
    try {
      revalidatePath(path);
    } catch (e) {
      failed.push(path);
      console.error(`[revalidate] 실패: ${path}`, e);
    }
  }

  if (failed.length > 0) {
    return Response.json({ revalidated: paths.length - failed.length, failed }, { status: 500 });
  }

  return Response.json({ revalidated: paths.length });
}
