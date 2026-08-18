import { proxySearch } from "../proxy";

/** `GET /api/search/suggest?q=` → `GET /api/v1/search/suggest?q=` (ISSUE-042). */
export async function GET(request: Request): Promise<Response> {
  return proxySearch(request, "/suggest");
}
