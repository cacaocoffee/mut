const BASE = process.env.MUT_API_URL?.replace(/\/$/, "") ?? "";

/**
 * 유통 제품 검색 프록시 (#209). 레시피 줄에서 "어떤 캄파리가 들어오나" 를 펼칠 때 브라우저가 부른다.
 * 상류 `GET /api/v1/products` 는 공개 조회지만 다른 오리진이라 여기서 한 번 감싼다 (검색 프록시와 같은 이유).
 */
export async function GET(request: Request): Promise<Response> {
  const sp = new URL(request.url).searchParams;
  const q = sp.get("q") ?? "";
  const size = Math.min(20, Math.max(1, Number(sp.get("size") ?? 5) || 5));

  if (!BASE) {
    return Response.json({ error: "유통 제품을 조회할 수 없습니다" }, { status: 503 });
  }

  try {
    // q 는 제품명만 본다 (#212) — 수입사까지 보면 "캄파리" 에 캄파리코리아의 다른 술이 섞인다
    const res = await fetch(`${BASE}/api/v1/products?q=${encodeURIComponent(q)}&size=${size}`, {
      next: { revalidate: 600 },
    });
    return new Response(await res.text(), {
      status: res.status,
      headers: {
        "Content-Type": res.headers.get("Content-Type") ?? "application/json",
        "X-Robots-Tag": "noindex",
      },
    });
  } catch (e) {
    console.warn(`[products] 상류 호출 실패: ${e instanceof Error ? e.message : String(e)}`);
    return Response.json({ error: "유통 제품을 조회할 수 없습니다" }, { status: 502 });
  }
}
