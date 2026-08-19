/**
 * ISSUE-036 — 프로토타입 24종(현재 49종) → Postgres 시드 (SPEC-01 §6 · SPEC-06 §6).
 *
 * `packages/domain/src/data.ts` 를 읽어 `R__seed_*.sql` 두 개를 만든다.
 *
 * ## 일회성인데 커밋한다
 *
 * **변환 규칙이 곧 이관 근거**다. SQL 만 남기면 "왜 이 재료가 `liqueur` 인가" 를
 * 다시 물었을 때 답할 것이 없다. 시드를 고칠 일도 생긴다 (`R__` 는 repeatable 이다).
 *
 * ## 단순 복사가 아니다
 *
 * 프로토타입 타입과 SPEC-06 스키마가 여러 곳에서 다르다:
 *
 * | 프로토타입 | 스키마 | 변환 |
 * |---|---|---|
 * | ~~`base: "진"` (한국어)~~ | `base_spirit: "gin"` | 이슈 037 이후 **값이 이미 슬러그다** |
 * | `sweet: 0\|1\|2\|3` | `sweetness: "dry"…` | 숫자 → 슬러그 |
 * | `id: "negroni"` | `slug` + DB 가 준 `id` | 이름 이동 |
 * | `ingredients[]` 인라인 | `recipe_ingredient.ingredient_id` FK | **마스터를 먼저 만든다** |
 * | `styles[]` · `flavors[]` | 조인 테이블 | 행 분해 |
 * | `story: {title, paragraphs}` | `story TEXT` | 마크다운 직렬화 |
 *
 * ## 실행
 *
 * ```
 * npx tsx scripts/seed-from-prototype.ts
 * ```
 */
import { writeFileSync } from "node:fs";
import { join } from "node:path";
import { COCKTAILS, type Cocktail, type Ingredient } from "@mut/domain";

/* ─────────────────  재료 마스터  ───────────────── */

/**
 * 재료 카테고리 (7종 — `FR-INGREDIENT-006`).
 *
 * **자동 추론하지 않는다.** "리큐르" 라는 글자가 이름에 있는지로 나누면
 * `베네딕틴 돔` 은 리큐르인데 안 걸리고 `커피 리큐어` 만 걸린다.
 * 사람이 한 번 적고 그것을 근거로 남긴다 (RED 4).
 *
 * 키는 **정규화된 영문명**이다 — 한국어 표기가 흔들려서다 (아래 [canonicalKey] 참조).
 */
const CATEGORY: Record<string, string> = {
  // 증류주
  "dry gin": "spirit",
  vodka: "spirit",
  "citron vodka": "spirit",
  "white rum": "spirit",
  "dark rum": "spirit",
  "jamaican rum": "spirit",
  "barbados rum": "spirit",
  "rhum agricole": "spirit",
  rum: "spirit",
  "blanco tequila": "spirit",
  "rye whiskey": "spirit",
  "bourbon / rye": "spirit",
  bourbon: "spirit",
  "bourbon whiskey": "spirit",
  "scotch whisky": "spirit",
  "blended scotch": "spirit",
  "islay single malt": "spirit",
  cognac: "spirit",
  calvados: "spirit",
  "distilled soju": "spirit",
  "munbaeju 40%": "spirit",
  absinthe: "spirit",

  // 리큐르 · 주정강화
  campari: "liqueur",
  "sweet vermouth": "liqueur",
  "dry vermouth": "liqueur",
  "lillet blanc": "liqueur",
  "fino sherry": "liqueur",
  cointreau: "liqueur",
  "orange curaçao": "liqueur",
  "blue curaçao": "liqueur",
  "coffee liqueur": "liqueur",
  "bénédictine dom": "liqueur",
  "crème de cassis": "liqueur",
  "banana liqueur": "liqueur",
  "green tea liqueur": "liqueur",
  "lychee liqueur": "liqueur",
  "sloe gin": "liqueur",
  suze: "liqueur",
  makgeolli: "liqueur", // 발효주. 7종에 그 칸이 없어 가장 가까운 곳에 둔다 (GAPS G-31)
  "lager beer": "liqueur",

  // 비터스
  "angostura bitters": "bitters",
  "orange bitters": "bitters",

  // 시럽
  "simple syrup 1:1": "syrup",
  "rich simple syrup": "syrup",
  orgeat: "syrup",
  "honey-ginger syrup": "syrup",
  "jocheong syrup": "syrup",
  "citrus shrub syrup": "syrup",
  "coconut cream": "syrup", // 시럽은 아니지만 계량해 넣는 감미 재료다

  // 주스
  "lemon juice": "juice",
  "lime juice": "juice",
  "grapefruit juice": "juice",
  "cranberry juice": "juice",
  "tomato juice": "juice",
  "pineapple juice": "juice",
  espresso: "juice", // 계량해 넣는 액체. 7종에 커피 칸이 없다
  "oolong tea": "juice",
  milk: "juice",

  // 믹서
  "soda water": "mixer",
  "tonic water": "mixer",
  "ginger beer": "mixer",
  "grapefruit soda": "mixer",

  // 가니시 — `counts_for_stock` 이 false 인 부류다 (이슈 008 RED 2)
  "lemon peel": "garnish",
  "orange peel": "garnish",
  "pear peel": "garnish",
  "lime wedge": "garnish",
  "lemon wheel": "garnish",
  "orange wheel": "garnish",
  "grapefruit slice": "garnish",
  lime: "garnish",
  "mint leaves": "garnish",
  rosemary: "garnish",
  cherry: "garnish",
  "salt rim": "garnish",
  sugar: "garnish",
  "egg white": "garnish", // 계량하지 않는 부재료. 가니시는 아니지만 7종 중 가장 가깝다
  "worcestershire · tabasco": "garnish",
};

/**
 * 국내 유통 (`PRIN-P05` · 4종).
 *
 * 기본은 `common` 이다 (RED 6). 여기 적힌 것만 다르게 간다 —
 * **미유통이면 `substitute_note` 가 필수**라(`INV-INGREDIENT-01`) 근거 없이 올리면
 * DB CHECK 에 막힌다.
 */
const AVAILABILITY: Record<string, string> = {
  "rhum agricole": "specialty",
  "islay single malt": "specialty",
  "bénédictine dom": "specialty",
  "fino sherry": "specialty",
  "lillet blanc": "specialty",
  suze: "specialty",
  orgeat: "specialty",
  "green tea liqueur": "specialty",
  "lychee liqueur": "specialty",
  "citrus shrub syrup": "specialty",
  "jocheong syrup": "specialty",
};

/**
 * 영문 표기도 흔들린다 — **한국어만 정규화해서는 안 된다.**
 *
 * `ko` 로 묶으면 `쿠앵트로`/`코앵트로` 가 두 재료가 되고, `en` 으로 묶으면
 * `Simple Syrup`/`Simple Syrup 1:1` 이 두 재료가 된다. 프로토타입이 양쪽에서 드리프트했다.
 *
 * 이 표가 **드리프트의 목록**이다. 마스터를 만들지 않으면 영영 안 보였을 것들이라
 * 지우지 않고 남긴다 — `PRIN-D01` 이 "재료는 문자열이 아니라 참조" 라고 한 근거다.
 */
const EN_ALIASES: Record<string, string> = {
  "simple syrup": "simple syrup 1:1",
  "rich syrup 2:1": "rich simple syrup",
  mint: "mint leaves",
  salt: "salt rim",
  "angostura aromatic bitters": "angostura bitters",
  // 등급 표기다. 마스터에서 나눌 값이 아니다 — 레시피가 등급을 요구하면 `amount_label` 이 진다.
  "vsop cognac": "cognac",
};

/** 표기가 흔들린 것들의 대표 이름. 가장 많이 쓰인 표기를 고른다. */
const canonicalKey = (ing: Ingredient) => {
  const raw = ing.en.trim().toLowerCase();
  return EN_ALIASES[raw] ?? raw;
};

interface Master {
  slug: string;
  nameKo: string;
  nameEn: string;
  aliases: string[];
  category: string;
  availability: string;
}

/**
 * 재료 마스터를 뽑는다.
 *
 * **`ko` 가 아니라 `en` 으로 묶는다.** 프로토타입에 표기 흔들림이 있다 —
 * `쿠앵트로`/`코앵트로`, `오렌지 비터`/`오렌지 비터스`, `탄산수`/`소다`.
 * 문자열로 들고 있을 때는 안 보이다가 **마스터를 만드는 순간 드러난다** (`PRIN-D01`).
 *
 * 대표 한국어 이름은 **가장 많이 쓰인 표기**를 고르고, 나머지는 별칭으로 남긴다 —
 * 검색이 두 표기 모두를 찾아야 한다 (`R-F2.1-3`).
 */
function extractMasters(): Map<string, Master> {
  const counts = new Map<string, Map<string, number>>();
  const english = new Map<string, string>();

  for (const cocktail of COCKTAILS) {
    for (const ing of cocktail.ingredients) {
      const key = canonicalKey(ing);
      const byKo = counts.get(key) ?? new Map<string, number>();
      byKo.set(ing.ko, (byKo.get(ing.ko) ?? 0) + 1);
      counts.set(key, byKo);
      english.set(key, ing.en.trim());
    }
  }

  const masters = new Map<string, Master>();
  // 누락을 **한 번에 모아** 보고한다. 하나씩 터뜨리면 72종을 채우는 데 왕복이 72번이다.
  const missing: string[] = [];

  for (const [key, byKo] of counts) {
    const sorted = [...byKo.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]));
    const [primary, ...rest] = sorted.map(([ko]) => ko);

    const category = CATEGORY[key];
    if (!category) {
      missing.push(`  ${JSON.stringify(key)}: "?", // ${primary}`);
      continue;
    }

    masters.set(key, {
      slug: slugify(english.get(key)!),
      nameKo: primary,
      nameEn: english.get(key)!,
      aliases: rest,
      category,
      availability: AVAILABILITY[key] ?? "common",
    });
  }

  /**
   * **별칭이 남의 대표 이름이면 뺀다.**
   *
   * 실제로 걸렸다: `올드 패션드` 가 `{ ko: "설탕 시럽", en: "Rich Syrup 2:1" }` 이다.
   * 올드 패션드는 리치 시럽을 쓰므로 `en` 이 맞고 **`ko` 가 원본의 오기**다.
   * `en` 으로 묶은 것은 옳지만, 그 바람에 `설탕 시럽` 이 `리치 시럽` 의 별칭이 됐다 —
   * 그러면 "설탕 시럽" 을 검색한 사람이 **엉뚱한 재료**를 받는다 (`R-F2.1-3`).
   *
   * 별칭은 "같은 것의 다른 이름" 이지 "비슷한 것의 이름" 이 아니다.
   */
  const primaryNames = new Set([...masters.values()].map((m) => m.nameKo));
  const dropped: string[] = [];
  for (const master of masters.values()) {
    const kept = master.aliases.filter((alias) => {
      if (!primaryNames.has(alias)) return true;
      dropped.push(`${master.nameKo} ⊅ ${alias} (다른 재료의 대표 이름이다)`);
      return false;
    });
    master.aliases = kept;
  }
  if (dropped.length > 0) {
    console.log("\n별칭에서 뺀 것 (원본 데이터의 오기):");
    for (const line of dropped) console.log(`  ${line}`);
  }

  if (missing.length > 0) {
    // **추론하지 않는다** (RED 4). `리큐르` 라는 글자로 나누면 `베네딕틴 돔` 은 놓치고
    // `커피 리큐어` 만 걸린다. 사람이 한 번 적고 그것이 근거로 남는다.
    throw new Error(
      `CATEGORY 매핑이 ${missing.length}종 빠졌다. 아래를 채운다:\n\n${missing.join("\n")}\n`,
    );
  }
  return masters;
}

/* ─────────────────  값 변환  ───────────────── */

// 당도도 이슈 037 이후 슬러그다 (`0` → `"dry"`). 변환표가 필요 없어졌다.

// 메이킹 방법도 이슈 037 이후 슬러그다. 변환표가 필요 없어졌다.

function slugify(name: string): string {
  return name
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "") // 발음 구별 부호 (Curaçao → Curacao)
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
}

const sql = (value: string | null | undefined) =>
  value == null ? "NULL" : `'${value.replace(/'/g, "''")}'`;

const sqlArray = (values: string[]) =>
  values.length === 0 ? "'{}'" : `ARRAY[${values.map(sql).join(", ")}]::TEXT[]`;

/**
 * `{title, paragraphs[]}` → 마크다운 (RED 35).
 *
 * 구조를 잃지만 **읽을 수 있는 형태로** 잃는다. `story TEXT` 한 칸에 넣어야 하는데,
 * JSON 을 그대로 박으면 사람이 어드민에서 고칠 수 없다.
 */
function storyOf(cocktail: Cocktail): string | null {
  if (!cocktail.story) return null;
  return [`## ${cocktail.story.title}`, "", ...cocktail.story.paragraphs].join("\n\n");
}

/* ─────────────────  SQL 생성  ───────────────── */

function ingredientSeed(masters: Map<string, Master>): string {
  const rows = [...masters.values()]
    .sort((a, b) => a.slug.localeCompare(b.slug))
    .map(
      (m) =>
        `    (${sql(m.slug)}, ${sql(m.nameKo)}, ${sql(m.nameEn)}, ${sqlArray(m.aliases)}, ` +
        `${sql(m.category)}, ${sql(m.availability)}, true)`,
    );

  return `${header("재료 마스터", masters.size)}
INSERT INTO ingredient (slug, name_ko, name_en, aliases, category, domestic_availability, is_approved)
VALUES
${rows.join(",\n")}
-- RED 32·33 — repeatable 이라 체크섬이 바뀌면 다시 돈다.
-- **덮어쓰지 않는다**: 운영에서 에디터가 고친 값을 시드가 되돌리면 안 된다.
ON CONFLICT (slug) DO NOTHING;
`;
}

function cocktailSeed(masters: Map<string, Master>): string {
  const blocks = COCKTAILS.map((c) => cocktailBlock(c, masters));

  return `${header("칵테일", COCKTAILS.length)}
-- ## 서술이 있는 것만 발행한다
--
-- \`tasting_note\` 는 발행 필수다 (GATE-COCKTAIL-01). \`PRIN-P03\` 이 그것을 요구한 이유는
-- **직접 만들어 보고 쓴 내용**이어야 해서다 — 남의 설명을 옮기면 레시피 나열형 블로그와
-- 구별되지 않는다.
--
-- 프로토타입의 \`summary\` 가 그 자리를 대신하고 있었다 (validate.ts 의 주석이 그렇게 적었다).
-- 에디터 본인이 만들어 보고 쓴 문장이라 옮겨도 원칙에 어긋나지 않는다.
--
-- 다만 8종은 옮기지 않았다. \`summary\` 에 **만드는 법**을 적어 둔 것들이라
-- ("온도는 −3℃ 이하로 유지한다") 향·맛 서술이 아니다. 그것을 tasting_note 에 넣으면
-- 게이트를 글자로는 통과하고 뜻으로는 어긴다. 그 8종은 draft 로 남고 에디터가 채운다.

DO $seed$
DECLARE
    v_cocktail_id   BIGINT;
    v_recipe_id     BIGINT;
    v_ingredient_id BIGINT;
BEGIN
${blocks.join("\n")}
END
$seed$;
`;
}

function cocktailBlock(cocktail: Cocktail, masters: Map<string, Master>): string {
  // 이슈 037 이후 `cocktail.base` 자체가 슬러그다. 예전에는 한국어라 맵을 거쳐야 했다.
  const baseSlug = cocktail.base;
  const method = cocktail.method;
  const lines: string[] = [];

  lines.push(`    -- ${cocktail.ko} (${cocktail.en})`);
  lines.push(`    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = ${sql(cocktail.id)}) THEN`);

  // 3축 중 style_primary 는 cocktail_style 에 그 행이 있어야 한다 (복합 FK, DEFERRABLE).
  // 같은 트랜잭션 안이라 커밋 시점에 함께 검사된다 — DO 블록이 그 트랜잭션이다.
  lines.push(`        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile,
            tasting_note, status, published_at
        ) VALUES (
            ${sql(cocktail.id)}, ${sql(cocktail.ko)}, ${sql(cocktail.en)}, ${sql(cocktail.summary)},
            ${sql(baseSlug)}, ${sql(cocktail.stylePrimary)}, ${sql(method)},
            ${sql(cocktail.sweet)}, ${sql(cocktail.glass)},
            ${cocktail.abv === 0 ? "0" : cocktail.abv},
            ${cocktail.story ? "true" : "false"}, ${sql(storyOf(cocktail))},
            ${sql(cocktail.origin?.year)}, ${sql(cocktail.origin?.place)}, ${sql(cocktail.origin?.creator)},
            ${cocktail.profile ? `ARRAY[${cocktail.profile.join(", ")}]::SMALLINT[]` : "NULL"},
            ${sql(cocktail.tastingNote)},
            ${cocktail.tastingNote ? "'published', now()" : "'draft', NULL"}
        ) RETURNING id INTO v_cocktail_id;`);

  for (const style of cocktail.styles) {
    lines.push(
      `        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, ${sql(style)});`,
    );
  }
  for (const flavor of cocktail.flavors) {
    lines.push(
      `        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, ${sql(flavor)});`,
    );
  }

  // INV-COCKTAIL-07 — 칵테일마다 standard 레시피가 정확히 하나다.
  lines.push(
    `        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;`,
  );

  cocktail.ingredients.forEach((ing, index) => {
    const master = masters.get(canonicalKey(ing))!;
    // PRIN-D01 — 재료는 참조다. 프리텍스트로 넣지 않는다 (RED 20).
    //
    // `SELECT ... INTO STRICT` 다. `INSERT ... SELECT` 로 쓰면 재료를 못 찾았을 때
    // **0행을 조용히 넣는다** — 실제로 그렇게 당했다: repeatable 마이그레이션이
    // 알파벳순이라 `cocktail` 이 `ingredient` 보다 먼저 돌았고, 레시피 재료가 통째로 비었다.
    // 그런데 마이그레이션은 성공했고 아무도 몰랐다. STRICT 는 없으면 터진다.
    lines.push(
      `        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = ${sql(master.slug)};
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, ${index + 1}, ${ing.ml ?? "NULL"}, ${ing.ml ? "'ml'" : "NULL"}, ` +
        `${sql(ing.amount)}, ${sql(ing.sub)});`,
    );
  });

  cocktail.steps.forEach((step, index) => {
    lines.push(
      `        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, ${index + 1}, ${sql(step)});`,
    );
  });

  lines.push("    END IF;");
  return lines.join("\n");
}

function header(what: string, count: number): string {
  return `-- ISSUE-036 — 프로토타입 ${what} ${count}종 시드 (SPEC-01 §6 · SPEC-06 §6).
--
-- ⚠️ **손으로 고치지 않는다.** \`packages/domain/src/data.ts\` 를 고치고
--    \`npx tsx scripts/seed-from-prototype.ts\` 로 다시 만든다.
--    변환 규칙은 그 스크립트에 있고, 그것이 이관 근거다.
`;
}

/* ─────────────────  실행  ───────────────── */

const masters = extractMasters();
const outDir = join(import.meta.dirname, "../apps/api/src/main/resources/db/migration");

// **파일명에 번호가 붙는 이유** — Flyway 는 repeatable 마이그레이션을 **설명 알파벳순**으로 돈다.
// `R__seed_cocktail` / `R__seed_ingredient` 로 두었더니 `c` < `i` 라 칵테일이 먼저 돌았고,
// 재료가 없는 상태에서 `SELECT ... FROM ingredient` 가 0행을 돌려줘 레시피 재료가 통째로 비었다.
// 마이그레이션은 성공했다 — 그게 이 실패의 나쁜 점이다.
writeFileSync(join(outDir, "R__seed_01_ingredient.sql"), ingredientSeed(masters));
writeFileSync(join(outDir, "R__seed_02_cocktail.sql"), cocktailSeed(masters));

console.log(`재료 ${masters.size}종 · 칵테일 ${COCKTAILS.length}종 시드를 만들었다.`);

const aliased = [...masters.values()].filter((m) => m.aliases.length > 0);
if (aliased.length > 0) {
  console.log("\n표기가 흔들린 재료 (별칭으로 흡수했다):");
  for (const m of aliased) console.log(`  ${m.nameKo} ← ${m.aliases.join(" · ")}`);
}
