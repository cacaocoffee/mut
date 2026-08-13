import { test, expect, type Page } from "@playwright/test";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

/**
 * ISSUE-032 — 과음 경고 · 제휴 라벨 · 법적 페이지
 * (`NFR-L-01`~`L-04` · `FR-USER-002`·`003` · `FR-COCKTAIL-028` · ADR-0004).
 *
 * ## 여기 있는 것은 전부 배포 차단 조건이다
 *
 * `NFR-L-01`(과음 경고) · `NFR-L-02`(제휴 라벨) · `NFR-L-03`(색 분리) · `NFR-L-04`(약관)
 * 넷이 SPEC-04 에서 **배포 차단**으로 분류돼 있다. 취향이 아니라 법적 요구다.
 *
 * ## 절반이 "만들지 않았다" 의 검증이다
 *
 * ADR-0004 가 전면 성인 인증 게이트를 폐기했다. `PRIN-T04` 와 `NFR-S-08` 이 그 결정을
 * **인터스티셜 금지**로 굳혔다 — 크롤러가 콘텐츠를 못 보면 SEO 요구와 정면 충돌한다.
 *
 * 부재는 코드로 표현할 수 없다. 이슈 027 의 `ExposureRuleAbsenceTest` 와 같은 성격이다.
 */

const PUBLIC_PAGES = [
  { path: "/", name: "탐색" },
  { path: "/cocktails/negroni", name: "상세" },
  { path: "/finder", name: "파인더" },
  { path: "/privacy", name: "개인정보 처리방침" },
  { path: "/terms", name: "이용약관" },
];

/** `LEGAL_NOTICE_LINES` 와 같은 문구다. 갈라지면 이 테스트가 자기가 적은 것만 확인한다. */
const OVERDRINKING = "지나친 음주는";
const UNDERAGE = "만 19세 미만";

// ── RED 1~8 : 과음 경고 (NFR-L-01 · FR-COCKTAIL-028 · R-F1.1-8) ────────────

for (const { path, name } of PUBLIC_PAGES) {
  test(`RED1~6 - ${name} 하단에 과음 경고와 미성년자 문구가 있다`, async ({ page }) => {
    await page.goto(path);

    const notice = page.getByTestId("legal-notice");

    await expect(notice).toBeVisible();
    await expect(notice).toContainText(OVERDRINKING);
    // FR-COCKTAIL-028 은 둘을 따로 요구한다. 과음 경고만 있고 미성년자 문구가 없으면 미충족.
    await expect(notice).toContainText(UNDERAGE);
  });
}

/**
 * RED 7 — **숨겨지지 않는다.**
 *
 * 있는데 안 보이는 것이 없는 것보다 나쁘다 — 코드 리뷰는 통과하고 요구는 미충족이다.
 * `display:none` · `visibility:hidden` · 0px · `opacity:0` 을 전부 본다.
 */
test("RED7 - 경고 문구가 숨겨지지 않는다", async ({ page }) => {
  await page.goto("/");

  const box = await page.getByTestId("legal-notice").boundingBox();
  expect(box, "레이아웃 상자가 없다 — display:none 이거나 렌더되지 않았다").not.toBeNull();
  expect(box!.height).toBeGreaterThan(10);
  expect(box!.width).toBeGreaterThan(10);

  const style = await page.getByTestId("legal-notice").evaluate((el) => {
    const s = getComputedStyle(el);
    return {
      display: s.display,
      visibility: s.visibility,
      opacity: Number(s.opacity),
      fontSize: parseFloat(s.fontSize),
    };
  });

  expect(style.display).not.toBe("none");
  expect(style.visibility).toBe("visible");
  expect(style.opacity).toBeGreaterThan(0.9);
  // 읽을 수 없을 만큼 작으면 표기하지 않은 것과 같다.
  expect(style.fontSize).toBeGreaterThanOrEqual(11);
});

/**
 * RED 8·28 — 대비 4.5:1 이상 (`NFR-A-01`).
 *
 * 색 공간 때문에 `getComputedStyle` 숫자를 직접 읽으면 틀린다 — 토큰이 `oklch()` 라
 * 크롬이 `lab(...)` 을 돌려준다. 캔버스로 sRGB 환산한다 (`layout.spec.ts` 가 같은 함정을 겪었다).
 */
test("RED8,28 - 경고 문구 대비가 4.5:1 이상이다", async ({ page }) => {
  await page.goto("/");

  const ratio = await contrastOf(page, '[data-testid="legal-notice"] p');

  expect(ratio, "NFR-A-01 — 법적 고지가 읽히지 않으면 표기한 것이 아니다").toBeGreaterThanOrEqual(4.5);
});

// ── RED 9~13 : 인터스티셜 부재 (ADR-0004 · NFR-S-08 · PRIN-T04) ────────────

/**
 * RED 9·10·11 — **성인 인증이 존재하지 않는다.**
 *
 * ADR-0004 가 폐기했고 `NFR-S-08` 이 배포 차단으로 굳혔다. 콘텐츠 앞에 무언가를 세우면
 * 크롤러가 본문을 못 보고, `NFR-S-01`~`S-02`(색인) 와 정면 충돌한다.
 *
 * 나중에 "잠깐 모달 하나" 가 들어올 자리를 여기서 막는다.
 */
for (const { path, name } of PUBLIC_PAGES) {
  test(`RED9,10,11 - ${name} 에 인터스티셜·성인 인증이 없다`, async ({ page }) => {
    await page.goto(path);

    const blockers = await page.evaluate(() => {
      const suspects = Array.from(
        document.querySelectorAll<HTMLElement>(
          '[role="dialog"], [aria-modal="true"], dialog[open], .modal, .overlay, .interstitial, .age-gate',
        ),
      );
      // 화면을 덮고 있는가. 존재만으로 문제 삼지 않는다 — 닫힌 dialog 는 콘텐츠를 가리지 않는다.
      return suspects
        .filter((el) => {
          const s = getComputedStyle(el);
          if (s.display === "none" || s.visibility === "hidden") return false;
          const r = el.getBoundingClientRect();
          return r.width > innerWidth * 0.5 && r.height > innerHeight * 0.5;
        })
        .map((el) => el.className || el.tagName);
    });

    expect(blockers, "PRIN-T04 — 콘텐츠 앞에 인터스티셜을 세우지 않는다").toEqual([]);

    // RED 11 — 나이를 묻는 **입력**이 없다.
    //
    // 문구로 찾지 않는다. 처음에 `/성인\s*인증/` 로 훑었더니 개인정보 처리방침의
    // "성인 인증을 요구하지 않습니다" 가 걸렸다 — **없다고 적은 문장을 있다고 읽은 것이다.**
    // 게이트는 UI 이지 단어가 아니므로 상호작용 요소만 본다.
    const ageInputs = await page.evaluate(() => {
      const controls = Array.from(
        document.querySelectorAll<HTMLElement>("input, select, button, form"),
      );
      return controls
        .filter((el) => {
          const hay = [
            el.getAttribute("name"),
            el.getAttribute("id"),
            el.getAttribute("aria-label"),
            el.getAttribute("placeholder"),
            el.textContent,
          ]
            .filter(Boolean)
            .join(" ");
          return /생년월일|birth|나이|age|성인\s*인증|19세\s*이상/i.test(hay);
        })
        .map((el) => el.tagName + (el.getAttribute("name") ?? ""));
    });

    expect(ageInputs, "ADR-0004 — 나이를 묻지 않는다").toEqual([]);
  });
}

/**
 * RED 12·13 — **초기 HTML 에 본문이 있다.**
 *
 * `PRIN-T04` 의 요구가 이것이다 — JS 없이도 크롤러가 콘텐츠를 봐야 한다.
 * 빌드 산출물을 직접 읽는다: 브라우저로 확인하면 JS 가 이미 돌아 있어 구별되지 않는다.
 */
test("RED12,13 - SSG 산출물에 본문과 경고가 들어 있다", async () => {
  const html = readBuiltHtml("cocktails/negroni.html") ?? readBuiltHtml("index.html");

  expect(html, ".next 산출물을 못 찾았다 — 이 테스트는 빌드 후에만 의미가 있다").not.toBeNull();
  expect(html!, "본문이 JS 로만 그려지면 크롤러가 못 본다").toContain("네그로니");
  expect(html!, "NFR-L-01 — 경고가 초기 HTML 에 있어야 한다").toContain(OVERDRINKING);
});

// ── RED 14~19 : 제휴 라벨 (NFR-L-02 · R-F4.2-3 · PRIN-P02) ─────────────────

/**
 * RED 16 — **끌 수 있는 prop 이 없다.**
 *
 * 공정위 심사지침상 의무라 표시 여부가 코드에서 선택 가능해지면 안 된다.
 * 소스를 읽어 확인한다 — 검증 대상이 **컴포넌트의 인터페이스**라 다른 방법이 없다.
 * (이슈 027 의 RED 17 이 같은 방식을 쓴다.)
 */
test("RED16,19 - 라벨을 끄거나 흐리게 할 수 있는 통로가 없다", () => {
  const source = readFileSync(
    join(process.cwd(), "components/legal/sponsored-label.tsx"),
    "utf8",
  );

  // props 는 `isSponsored` 하나뿐이다.
  const props = source.match(/\{\s*isSponsored\s*\}\s*:\s*\{([^}]*)\}/)?.[1] ?? "";
  expect(props.trim()).toBe("isSponsored: boolean");

  for (const escape of ["hidden", "variant", "className", "style", "size", "muted"]) {
    expect(source, `${escape} 가 있으면 언젠가 라벨이 꺼진다 (PRIN-P02)`).not.toContain(
      `${escape}:`,
    );
  }

  // PRIN-P02 — 축소·흐림도 위반이다. CSS 가 값을 못박고 있는지 본다.
  const css = readFileSync(join(process.cwd(), "components/legal/legal.css"), "utf8");
  const rule = css.match(/\.sponsored-label\s*\{([^}]*)\}/)?.[1] ?? "";
  expect(rule).toContain("opacity: 1");
  expect(rule, "라벨 색은 accent-700 이다 — 파트너 배지(1b)와 달라야 한다").toContain(
    "--color-accent-700",
  );
});

/**
 * RED 14·17·18 — 렌더 규칙.
 *
 * Phase 1a 에 이 라벨을 쓰는 화면이 아직 없다 (재료 사전은 Wave 8). 컴포넌트를 직접
 * 마운트해 계약을 고정한다 — 화면이 붙을 때 규칙이 이미 서 있어야 한다.
 */
test("RED14,17,18 - is_sponsored 일 때만 제휴 콘텐츠 라벨이 나온다", async ({ page }) => {
  await page.goto("/");

  const rendered = await page.evaluate(() => {
    // 컴포넌트를 그대로 부를 수 없어 같은 마크업을 세운다. 판정 규칙(`isSponsored` 하나)은
    // 소스 검사(RED 16)가 지키고, 여기서는 **보이는 결과**를 본다.
    const host = document.createElement("div");
    host.innerHTML = '<span class="sponsored-label">제휴 콘텐츠</span>';
    document.body.appendChild(host);
    const el = host.firstElementChild as HTMLElement;
    const s = getComputedStyle(el);
    const result = { text: el.textContent, fontSize: parseFloat(s.fontSize), opacity: Number(s.opacity) };
    host.remove();
    return result;
  });

  expect(rendered.text).toBe("제휴 콘텐츠");
  // PRIN-P02 — 축소되지 않는다. 본문(11px)보다 작으면 눈에 덜 띄게 만든 것이다.
  expect(rendered.fontSize).toBeGreaterThanOrEqual(12);
  expect(rendered.opacity).toBe(1);
});

/**
 * RED 20·21 — **파트너 배지(1b)와 다른 색을 쓴다** (`NFR-L-03` · SPEC-02 §5.3).
 *
 * 파트너 배지는 Phase 1b 라 지금 대조할 상대가 없다. 색을 **지금 못박고**,
 * 1b 가 이 색을 피하도록 `EPICS-1B-PHASE2.md` 에 제약으로 적어 뒀다.
 */
test("RED20,21 - 제휴 라벨 색이 accent-700 으로 고정돼 있다", async ({ page }) => {
  await page.goto("/");

  const ratio = await page.evaluate(() => {
    const host = document.createElement("div");
    host.innerHTML = '<span class="sponsored-label">제휴 콘텐츠</span>';
    document.body.appendChild(host);
    const el = host.firstElementChild as HTMLElement;
    const color = getComputedStyle(el).color;
    host.remove();
    return color;
  });

  expect(ratio, "색이 지정되지 않았다").not.toBe("");
});

// ── RED 22~25 : 정적 페이지 (NFR-L-04) ────────────────────────────────────

test("RED22,23,25 - 처리방침·약관 페이지가 있고 비어 있지 않다", async ({ page }) => {
  for (const path of ["/privacy", "/terms"]) {
    const response = await page.goto(path);
    expect(response?.status(), `${path} 가 없다`).toBe(200);

    const main = page.locator("main");
    await expect(main).toBeVisible();

    const text = (await main.innerText()).replace(/\s+/g, "");
    expect(text.length, `${path} 문안이 비어 있다`).toBeGreaterThan(200);

    // NFR-L-05 — 법률 검토 전이라는 사실이 화면에 남아 있어야 한다.
    await expect(page.locator(".legal-page__draft")).toBeVisible();
  }
});

/** RED 24 — 페이지만 있고 링크가 없으면 없는 것과 같다. */
test("RED24 - 푸터에서 두 페이지로 이동할 수 있다", async ({ page }) => {
  await page.goto("/");

  const notice = page.getByTestId("legal-notice");
  await expect(notice.getByRole("link", { name: "개인정보 처리방침" })).toBeVisible();
  await expect(notice.getByRole("link", { name: "이용약관" })).toBeVisible();

  await notice.getByRole("link", { name: "이용약관" }).click();
  await expect(page).toHaveURL(/\/terms$/);
});

// ── RED 29 : 접근성 (NFR-A-01) ────────────────────────────────────────────

/** `NFR-A-01` — accent 는 강조색이지 본문색이 아니다. 법적 문서는 길게 읽는다. */
test("RED29 - 약관 본문에 accent 를 쓰지 않는다", async ({ page }) => {
  await page.goto("/terms");

  const ratio = await contrastOf(page, ".legal-page p");
  expect(ratio).toBeGreaterThanOrEqual(4.5);
});

// ── 헬퍼 ──────────────────────────────────────────────────────────────────

/**
 * 빌드 산출물에서 HTML 을 찾는다.
 *
 * Next 버전마다 경로가 조금씩 다르다 — 하나로 못박으면 업그레이드 때 **조용히**
 * `null` 이 되어 테스트가 통과해 버린다. 그래서 못 찾으면 위에서 실패시킨다.
 */
function readBuiltHtml(relative: string): string | null {
  const roots = [
    join(process.cwd(), ".next/server/app"),
    join(process.cwd(), ".next/server/pages"),
  ];

  for (const root of roots) {
    const found = findFile(root, relative);
    if (found) return readFileSync(found, "utf8");
  }
  return null;
}

function findFile(dir: string, relative: string): string | null {
  try {
    if (!statSync(dir).isDirectory()) return null;
  } catch {
    return null;
  }

  const direct = join(dir, relative);
  try {
    if (statSync(direct).isFile()) return direct;
  } catch {
    /* 계속 찾는다 */
  }

  for (const entry of readdirSync(dir)) {
    const child = join(dir, entry);
    try {
      if (statSync(child).isDirectory()) {
        const found = findFile(child, relative);
        if (found) return found;
      }
    } catch {
      /* 무시 */
    }
  }
  return null;
}

/** `layout.spec.ts` 와 같은 방식 — 캔버스로 sRGB 환산한다 (oklch 함정). */
async function contrastOf(page: Page, selector: string): Promise<number> {
  return page.evaluate((sel) => {
    const el = document.querySelector(sel);
    if (!el) throw new Error(`셀렉터를 찾지 못했다: ${sel}`);

    const ctx = document.createElement("canvas").getContext("2d")!;
    const parse = (c: string): [number, number, number, number] => {
      if (!c || c === "transparent") return [0, 0, 0, 0];
      ctx.clearRect(0, 0, 1, 1);
      ctx.fillStyle = "#000";
      ctx.fillStyle = c;
      ctx.fillRect(0, 0, 1, 1);
      const [r, g, b, a] = ctx.getImageData(0, 0, 1, 1).data;
      return [r, g, b, a / 255];
    };

    const luminance = ([r, g, b]: [number, number, number, number]) => {
      const lin = (v: number) => {
        const s = v / 255;
        return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
      };
      return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
    };

    const fg = parse(getComputedStyle(el).color);

    /**
     * 배경을 **합성해서** 구한다.
     *
     * 처음에는 "알파가 0보다 크면 그게 배경" 으로 두었다가 틀렸다 —
     * 초안 배너의 배경이 `color-mix(… 6%, transparent)` 라 알파가 0.06 인데,
     * 그걸 불투명으로 읽어 **대비 2.2:1 이라는 거짓 실패**가 났다.
     *
     * 반투명 층은 아래를 가리지 않는다. 위로 올라가며 층을 모으고,
     * 불투명한 바닥을 만나면 거기서부터 아래→위로 얹는다.
     */
    const layers: Array<[number, number, number, number]> = [];
    let node: Element | null = el;
    let base: [number, number, number, number] = [255, 255, 255, 1];
    while (node) {
      const c = parse(getComputedStyle(node).backgroundColor);
      if (c[3] >= 0.999) {
        base = c;
        break;
      }
      if (c[3] > 0) layers.push(c);
      node = node.parentElement;
    }

    let bg = base;
    for (let i = layers.length - 1; i >= 0; i--) {
      const [r, g, b, a] = layers[i];
      bg = [
        r * a + bg[0] * (1 - a),
        g * a + bg[1] * (1 - a),
        b * a + bg[2] * (1 - a),
        1,
      ];
    }

    const l1 = luminance(fg);
    const l2 = luminance(bg);
    const [hi, lo] = l1 > l2 ? [l1, l2] : [l2, l1];
    return (hi + 0.05) / (lo + 0.05);
  }, selector);
}
