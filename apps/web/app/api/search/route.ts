import { proxySearch } from "./proxy";

/** `GET /api/search?q=` → `GET /api/v1/search?q=` (ISSUE-042). */
export async function GET(request: Request): Promise<Response> {
  return proxySearch(request, "");
}
