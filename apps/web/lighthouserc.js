/**
 * Lighthouse CI (ISSUE-046 · SPEC-04 §1·§9.1).
 *
 * ## 왜 CI 에서 막나
 *
 * `NFR-P-01`~`P-03` 은 **배포 차단**이다. 사람이 가끔 재면 느려진 것을 배포한 뒤에 안다.
 *
 * ## 기준이 경로마다 다르다
 *
 * SPEC-04 §1.1 — 상세·카테고리는 **SSG + ISR** 이라 2.0s 다. 나머지(탐색·파인더·통합 검색)는
 * 2.5s. **못 지키면 렌더링 전략이 어긋난 것이다** — 미리 그려 둔 것이 느리다는 말이라,
 * 숫자를 올릴 게 아니라 왜 정적이 아닌지를 봐야 한다 (RED 10).
 *
 * ## 로컬 실측이라 조건이 다르다
 *
 * SPEC-04 는 **모바일 · 4G Slow · p75** 를 말한다. CI 러너는 그것과 다르므로 여기 값은
 * **회귀를 잡는 눈금**이다. 실사용 값은 이슈 050 의 수동 확인이 맡는다.
 */
const BLOCKING = ["error"];

/** 미리 그려 두는 경로. 더 엄격하다 (SPEC-04 §1.1). */
const PRERENDERED = ["/cocktails/negroni", "/cocktails/base/gin"];

/** 브라우저에서 거르는 경로. */
const CLIENT_SIDE = ["/cocktails/search", "/finder", "/search"];

/** 경로와 무관한 기준. LCP 만 경로마다 다르다 (SPEC-04 §1.1). */
const COMMON = {
  // NFR-P-03 — CLS. 레이아웃이 튀면 누르려던 것을 잘못 누른다.
  "cumulative-layout-shift": [...BLOCKING, { maxNumericValue: 0.1 }],
  // NFR-P-02 의 대리값. INP 는 실사용 지표라 Lighthouse 는 TBT 로 근사한다.
  "total-blocking-time": [...BLOCKING, { maxNumericValue: 200 }],

  // 접근성 **차단은 axe 가 한다** (`e2e/a11y.spec.ts` · SPEC-04 §9.1 이 axe-core 를 지목).
  // 여기 점수는 경고다 — Lighthouse 는 accent 바탕 글자(G-16)를 함께 세는데 그것은
  // 제품 결정 대기라 임의로 못 고친다. 두 곳에서 차단하면 같은 미결로 배포가 두 번 막힌다.
  "categories:accessibility": ["warn", { minScore: 0.95 }],
  "categories:performance": ["warn", { minScore: 0.9 }],
  // 필터·검색 화면은 `noindex` 라(`NFR-S-02`) SEO 점수가 낮게 나오는 것이 정상이다.
  "categories:seo": ["warn", { minScore: 0.95 }],
  "unused-javascript": "off",
  "uses-long-cache-ttl": "off",
};

module.exports = {
  ci: {
    collect: {
      // 이미 빌드된 것을 띄운다. 여기서 다시 빌드하면 CI 가 두 번 짓는다.
      startServerCommand: "npm run start -- --port 3200",
      url: [...PRERENDERED, ...CLIENT_SIDE].map((p) => `http://localhost:3200${p}`),
      numberOfRuns: 1,
      settings: {
        // SPEC-04 §1 이 모바일 기준이다.
        preset: "desktop",
        emulatedFormFactor: "mobile",
        throttlingMethod: "simulate",
        skipAudits: ["uses-http2", "canonical"],
      },
    },
    // 경로마다 기준이 다르다 (SPEC-04 §1.1). 미리 그려 두는 경로가 더 엄격하다.
    assertMatrix: [
      {
        matchingUrlPattern: "/cocktails/(negroni|base/gin)$",
        assertions: {
          // **2.0s** — SSG + ISR 이다. 못 지키면 숫자를 올릴 게 아니라
          // 왜 미리 그린 것이 느린지를 봐야 한다 (RED 10).
          "largest-contentful-paint": [...BLOCKING, { maxNumericValue: 2000 }],
          ...COMMON,
        },
      },
      {
        matchingUrlPattern: "(cocktails/search|finder|/search)$",
        assertions: {
          "largest-contentful-paint": [...BLOCKING, { maxNumericValue: 2500 }],
          ...COMMON,
        },
      },
    ],
    assert: {
      assertions: {
        "largest-contentful-paint": [...BLOCKING, { maxNumericValue: 2500 }],
        // NFR-P-03 — CLS. 레이아웃이 튀면 누르려던 것을 잘못 누른다.
        "cumulative-layout-shift": [...BLOCKING, { maxNumericValue: 0.1 }],
        // NFR-P-02 의 대리값. INP 는 실사용 지표라 Lighthouse 는 TBT 로 근사한다.
        "total-blocking-time": [...BLOCKING, { maxNumericValue: 200 }],

        // 접근성 **차단은 axe 가 한다** (`e2e/a11y.spec.ts` · SPEC-04 §9.1 이 axe-core 를 지목).
        // 여기 점수는 경고다 — Lighthouse 는 accent 바탕 글자(G-16)를 함께 세는데 그것은
        // 제품 결정 대기라 임의로 못 고친다. 두 곳에서 차단하면 같은 미결로 배포가 두 번 막힌다.
        "categories:accessibility": ["warn", { minScore: 0.95 }],

        // 아래는 참고. 차단하지 않는다 — SPEC-04 §9 "전부를 차단으로 만들면 아무것도 못 나간다"
        "categories:performance": ["warn", { minScore: 0.9 }],
        // 필터·검색 화면은 `noindex` 라(`NFR-S-02`) SEO 점수가 낮게 나오는 것이 정상이다.
        "categories:seo": ["warn", { minScore: 0.95 }],
        "unused-javascript": "off",
        "uses-long-cache-ttl": "off",
      },
    },
    upload: { target: "filesystem", outputDir: ".lighthouseci" },
  },
};
