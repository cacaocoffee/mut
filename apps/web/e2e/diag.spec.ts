import { test } from "@playwright/test";
import { appendFileSync } from "node:fs";

const OUT = "/tmp/diag.txt";
const log = (s: string) => appendFileSync(OUT, s + "\n");

/** 진단용 — 실패 원인을 재기 위한 임시 스펙. 원인 확인 후 삭제한다. */

test("탭 계산색 덤프", async ({ page }) => {
  await page.goto("/");
  await page.waitForLoadState("networkidle");

  const dump = await page.evaluate(() => {
    const out: string[] = [];
    document.querySelectorAll(".tab").forEach((el, i) => {
      const cs = getComputedStyle(el);
      out.push(
        `[${i}] current=${el.getAttribute("aria-current")} color=${cs.color} bg=${cs.backgroundColor} borderBottom=${cs.borderBottomColor}`
      );
      let p: Element | null = el.parentElement;
      let depth = 0;
      while (p && depth < 4) {
        out.push(`     ↑ ${p.className || p.tagName} bg=${getComputedStyle(p).backgroundColor}`);
        p = p.parentElement;
        depth++;
      }
    });
    return out.join("\n");
  });
  log(dump);
});

test("320px 상세 넘침 원인", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 800 });
  await page.goto("/cocktails/negroni");
  await page.waitForLoadState("networkidle");

  const dump = await page.evaluate(() => {
    const html = document.documentElement;
    html.style.overflowX = "visible";
    document.body.style.overflowX = "visible";
    void html.offsetWidth;

    const vw = html.clientWidth;
    const bad: string[] = [];
    document.querySelectorAll<HTMLElement>("*").forEach((el) => {
      const r = el.getBoundingClientRect();
      if (r.right > vw + 1 && r.width > 0) {
        const id = `${el.tagName.toLowerCase()}${el.className ? "." + String(el.className).split(" ").join(".") : ""}`;
        bad.push(`${Math.round(r.right - vw)}px 초과  ${id.slice(0, 90)}  (w=${Math.round(r.width)})`);
      }
    });
    return `뷰포트 ${vw}px · scrollWidth ${html.scrollWidth}px\n` + bad.slice(0, 12).join("\n");
  });
  log(dump);
});
