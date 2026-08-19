import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // 워크스페이스 패키지는 빌드 산출물 없이 소스로 직접 소비한다.
  transpilePackages: ["@mut/domain", "@mut/ui"],

  /**
   * `/` 는 탐색으로 보낸다 (ISSUE-040 · [GAPS G-31](../../docs/prd/GAPS.md)).
   *
   * 탐색 화면이 `/` 에 있었는데 SPEC-05 §4 가 정한 자리로 옮겼다. 홈(ISR · 색인)을 만드는
   * 이슈는 아직 없어 그때까지 여기서 보낸다.
   *
   * **페이지에서 `redirect()` 를 부르지 않는 이유** — 그러면 `/` 가 정적 페이지로 미리
   * 그려지고 이동은 브라우저에서 일어난다. 열자마자 화면이 한 번 그려졌다가 사라져서,
   * 그 사이에 무엇을 하려던 것(스크립트·테스트)은 실행 도중 문맥을 잃는다.
   * 설정에 두면 HTML 이 나오기 전에 **응답으로** 이동한다.
   *
   * `permanent: false` — 임시 이동이라 주소가 굳지 않는다. 홈이 생기면 이 줄을 지운다.
   */
  async redirects() {
    return [{ source: "/", destination: "/cocktails/search", permanent: false }];
  },
};

export default nextConfig;
