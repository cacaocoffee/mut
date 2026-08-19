import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

/**
 * ISSUE-051 — 어드민 레시피 편집 ([G-38] · `NFR-O-01`).
 *
 * **인수 시나리오는 API 테스트가 끝까지 돈다** (`AdminRecipeApiTest`) — 생성 → 재료 →
 * 레시피 → 발행이 HTTP 만으로 된다. 여기서는 화면 쪽에서 **되돌아가기 쉬운 것**만 지킨다:
 * 재료를 문자열로 받는 칸이 생기지 않을 것, 단위가 다섯 종 다 있을 것, 저장이 통째로
 * 갈 것, 그리고 화면이 발행 게이트를 흉내 내지 않을 것.
 *
 * e2e 에 로그인이 없어(세션은 API 가 발급한다) 어드민 화면은 소스로 확인한다.
 */
const editor = readFileSync(join(process.cwd(), "components/admin/recipe-editor.tsx"), "utf8");

/**
 * `PRIN-D01` — 재료는 **참조**다. 이름을 타이핑해 넣는 길이 생기면 역검색과 바 연결이
 * 무너진다. 저장할 때 보내는 것이 `ingredientId` 인지를 본다.
 */
test("재료는 골라서 담는다 — 이름을 적어 넣지 않는다", () => {
  expect(editor, "재료를 id 로 보내지 않는다").toMatch(/ingredientId: r\.ingredientId/);
  expect(editor, "이름을 그대로 서버로 보낸다").not.toMatch(/nameKo: r\.nameKo,[\s\S]{0,80}body/);
});

/**
 * 계량 단위 5종이 화면에서 다 입력된다 (SPEC-02 §2.7).
 *
 * [G-35](../../../docs/prd/GAPS.md) — 지금 데이터에는 `ml` 뿐이다. 넣을 자리가 없었던
 * 것이 이유의 절반이라, 자리부터 만든다.
 */
test("G-35 - 계량 단위 5종이 다 있다", () => {
  for (const unit of ["ml", "dash", "barspoon", "piece", "top_up"]) {
    expect(editor, `${unit} 를 고를 수 없다`).toContain(`value: "${unit}"`);
  }
  // "채운다"는 수량이 없다 — 값을 보내면 잔 수 환산이 그것을 곱한다
  expect(editor, "top_up 에 수량을 딸려 보낸다").toMatch(/NO_AMOUNT\.has\(r\.unit\)/);
});

/** 통째로 덮는다. 줄 단위 저장이면 순서를 다시 매기는 규칙이 화면과 서버 두 벌이 된다. */
test("저장은 PUT 한 번이다", () => {
  expect(editor, "레시피를 PUT 으로 저장하지 않는다").toMatch(
    /fetch\(`\/api\/admin\/cocktails\/\$\{cocktailId\}\/recipe`, \{\s*method: "PUT"/,
  );
});

/**
 * 화면이 발행 게이트를 흉내 내지 않는다 (`PRIN-T05`).
 *
 * 재료가 없어도 저장은 된다 — 쓰다 만 초안을 못 저장하게 하면 에디터는 메모장에 쓰게 되고,
 * 그때 게이트는 아무것도 지키지 못한다. 막는 것은 발행이다.
 */
test("재료가 없어도 저장 버튼이 눌린다", () => {
  expect(editor, "저장을 재료 개수로 막는다").not.toMatch(/disabled=\{[^}]*rows\.length/);
});
