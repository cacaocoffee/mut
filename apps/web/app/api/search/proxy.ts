import { headers } from "next/headers";

/**
 * 통합 검색 프록시 (ISSUE-042 · SPEC-07 §2.4).
 *
 * ## 왜 브라우저가 API 를 직접 부르지 않나
 *
 * 검색은 입력할 때마다 부르는 유일한 화면이라 **브라우저에서** 불러야 한다 (자동완성).
 * 그런데 API 는 다른 오리진이고 CORS 를 열어 두지 않았다. 그래서 같은 오리진의 이 경로를
 * 거친다 — 브라우저는 `/api/search` 를 부르고, 여기서 서버 대 서버로 옮긴다.
 *
 * ## 두 벌이 되지 않게 **그대로 옮기기만** 한다
 *
 * SPEC-07 §5 — "별도의 내부 전용 조회 API 를 두지 않는다. **두 벌이 되면 반드시 어긋난다**."
 * 여기서는 질의를 그대로 넘기고 응답 본문과 상태 코드를 그대로 돌려준다. 모양을 바꾸는 순간
 * 계약이 둘이 되고, 생성 타입이 지키지 못하는 자리가 생긴다.
 *
 * ## 클라이언트 IP 를 넘긴다
 *
 * `/search` 는 60 req/min · **IP 기준**이다 (SPEC-08 §6 · `RateLimitPolicy.SEARCH`).
 * 그대로 프록시하면 API 가 보는 IP 가 프론트 서버 하나뿐이라 **모든 사용자가 한 통에 담긴다** —
 * 한 사람이 60번 치면 전체가 막힌다. 들어온 `X-Forwarded-For` 를 그대로 넘겨 원 클라이언트를
 * 알린다 (API 의 `RateLimitFilter` 가 첫 값을 쓴다). 실측: 한 IP 로 65번 부르면 60번째까지
 * 200 이고 나머지가 429 인데, **다른 IP 는 그대로 200** 이다 — 통이 나뉘어 있다는 뜻이다.
 *
 * **앞단이 그 헤더를 채워야 성립한다.** 프론트로 바로 들어오는 요청에는 `X-Forwarded-For` 가
 * 없고, 그러면 API 는 다시 프론트 서버 IP 하나만 본다. 호스팅이 정해지면([G-07](../../../../../docs/prd/GAPS.md))
 * 그 앞단이 이 헤더를 붙이는지 확인해야 한다.
 */
const BASE = process.env.KC_API_URL?.replace(/\/$/, "") ?? "";

export async function proxySearch(request: Request, path: "" | "/suggest"): Promise<Response> {
  const q = new URL(request.url).searchParams.get("q") ?? "";

  if (!BASE) {
    // 주소가 없으면 검색만 안 된다. 다른 화면은 프로토타입 데이터로 돈다 (`lib/api.ts`).
    console.warn("[search] KC_API_URL 이 없다 — 검색을 쓸 수 없다");
    return Response.json({ error: "검색을 쓸 수 없습니다" }, { status: 503 });
  }

  const forwarded = await clientAddress();
  const upstream = `${BASE}/api/v1/search${path}?q=${encodeURIComponent(q)}`;

  let res: Response;
  try {
    res = await fetch(upstream, {
      headers: forwarded ? { "X-Forwarded-For": forwarded } : {},
      // 검색 결과를 캐시하지 않는다 — 색인도 안 하는 것을 저장해 둘 이유가 없고,
      // 발행 직후 검색이 낡은 답을 주면 에디터가 자기 글을 못 찾는다.
      cache: "no-store",
    });
  } catch (e) {
    console.warn(`[search] 상류 호출 실패: ${e instanceof Error ? e.message : String(e)}`);
    return Response.json({ error: "검색을 쓸 수 없습니다" }, { status: 502 });
  }

  // 429·400 도 그대로 넘긴다. 화면이 원인에 따라 다른 안내를 해야 한다 (RED 24).
  return new Response(await res.text(), {
    status: res.status,
    headers: {
      "Content-Type": res.headers.get("Content-Type") ?? "application/json",
      // 검색 결과는 색인 대상이 아니다 (`PRIN-P06` · `NFR-S-02`). 상류도 같은 헤더를 준다.
      "X-Robots-Tag": "noindex",
    },
  });
}

/** 프록시 앞에 다른 프록시가 있으면 그쪽이 이미 채워 둔 값을 이어 쓴다. */
async function clientAddress(): Promise<string | null> {
  const incoming = await headers();
  return incoming.get("x-forwarded-for") ?? incoming.get("x-real-ip");
}
