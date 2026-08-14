import { BASE_SPIRIT_LABELS } from "./generated/labels";
import type {
  AbvBand,
  BaseSpirit,
  Cocktail,
  FlavorKey,
  StyleKey,
  SweetLevel,
  Technique,
} from "./types";

export const FLAVOR_LABELS: Record<FlavorKey, string> = {
  citrus: "CITRUS 시트러스",
  sour: "SOUR 산미",
  fruity: "FRUITY 과일",
  floral: "FLORAL 플로럴",
  herbal: "HERBAL 허브",
  spicy: "SPICY 스파이스",
  smoky: "SMOKY 스모키",
  bitter: "BITTER 비터",
  nutty: "NUTTY 너티 · 로스티",
  creamy: "CREAMY 크리미",
};

export const FLAVOR_KEYS = Object.keys(FLAVOR_LABELS) as FlavorKey[];

/** 스타일 축 — 표시명과 카테고리 슬러그. 슬러그는 그대로 URL이 된다. */
export const STYLE_LABELS: Record<StyleKey, string> = {
  highball: "하이볼 · 롱드링크",
  sour: "사워",
  "spirit-forward": "스피릿 포워드",
  spritz: "스프리츠 · 스파클링",
  tiki: "트로피컬 · 티키",
  creamy: "크리미 · 디저트",
  hot: "핫 드링크",
  frozen: "프로즌",
  shot: "샷",
};

export const STYLE_KEYS = Object.keys(STYLE_LABELS) as StyleKey[];

/**
 * 메이킹 방법 — 표시명과 필요 도구. 이 축은 사실상 난이도를 대신한다 (PRD 5.3).
 *
 * **키가 곧 슬러그다.** 예전에는 `Build` 같은 대문자 리터럴을 키로 쓰고 `slug` 를 따로
 * 들고 있었는데, 이슈 037 이후 값 자체가 슬러그라 그 칸이 필요 없어졌다.
 */
export const TECHNIQUES: Record<Technique, { ko: string; tools: string }> = {
  build: { ko: "잔에서 조립", tools: "잔, 바스푼" },
  shake: { ko: "흔들어 섞기", tools: "셰이커" },
  stir: { ko: "휘저어 섞기", tools: "믹싱글라스" },
  blend: { ko: "블렌드", tools: "블렌더" },
  etc: { ko: "기타", tools: "스로잉 · 머들링 · 인퓨징 · 직화" },
};

/**
 * 당도 4단계 — [한글, 영문].
 *
 * 예전에는 배열 순서가 곧 값이었다 (`SWEETNESS[cocktail.sweet]`).
 * 지금은 값이 문자열이라 **키로 찾는다** — 순서에 의미를 숨기지 않는다.
 */
export const SWEETNESS: Record<SweetLevel, readonly [string, string]> = {
  dry: ["드라이", "Dry"],
  semi_dry: ["세미 드라이", "Semi-Dry"],
  semi_sweet: ["세미 스위트", "Semi-Sweet"],
  sweet: ["스위트", "Sweet"],
};

/**
 * 당도의 **순서**. 드라이에서 스위트로 간다.
 *
 * 값이 문자열이 되면서 순서를 잃었다 — 문자열은 크기를 비교할 수 없다.
 * 파인더가 "이 정도 단맛" 을 고르면 그 근처를 찾아야 하므로(`matchScore`)
 * 순서를 따로 적어 둔다. **여기가 그 유일한 자리다.**
 */
export const SWEET_LEVELS: readonly SweetLevel[] = ["dry", "semi_dry", "semi_sweet", "sweet"];

/** 0(드라이) ~ 3(스위트). 가까운 정도를 재는 데 쓴다. */
export function sweetRank(level: SweetLevel): number {
  return SWEET_LEVELS.indexOf(level);
}

/**
 * PRD 5.1의 10종 전부. 카테고리 URL의 정본이라 아카이브에 항목이 없어도 유지한다.
 *
 * 한국어 이름은 계약에서 온다 (`BASE_SPIRIT_LABELS`) — 여기서 다시 적지 않는다 (`PRIN-T02`).
 */
export const BASES = Object.keys(BASE_SPIRIT_LABELS) as BaseSpirit[];

/**
 * 도수 구간의 **단일 정의** (ADR-0003). 탐색 필터·파인더·API가 전부 이 함수를 쓴다.
 * 경계를 바꾸려면 여기만 고친다.
 */
export function abvBandOf(abv: number): AbvBand {
  if (abv === 0) return "na";
  if (abv <= 10) return "low";
  if (abv <= 20) return "mid";
  return "high";
}

export const ABV_BANDS: ReadonlyArray<{
  key: AbvBand;
  /** 필터 칩 표기 — PRD 7.1의 구간명 */
  ko: string;
  /** 파인더 표기 — 같은 구간을 구어체로 */
  colloquial: string;
  en: string;
}> = [
  { key: "na", ko: "논알콜", colloquial: "무알콜", en: "Non-alcoholic · 0%" },
  { key: "low", ko: "저 ~10%", colloquial: "가볍게", en: "Light · ~10%" },
  { key: "mid", ko: "중 10–20%", colloquial: "적당히", en: "Medium · 10–20%" },
  { key: "high", ko: "고 20%~", colloquial: "독하게", en: "Strong · 20%~" },
];

export const ABV_BAND_KEYS = ABV_BANDS.map((b) => b.key);

export const AXES = ["SWEET", "SOUR", "BITTER", "AROMA", "PUNCH"] as const;
export const AXES_KO = ["단맛", "산미", "쓴맛", "향 강도", "알코올"] as const;

export const COCKTAILS: Cocktail[] = [
  {
    id: "negroni",
    ko: "네그로니",
    en: "Negroni",
    base: "gin",
    abv: 24,
    sweet: "dry",
    flavors: ["bitter", "herbal", "citrus"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "올드 패션드",
    method: "stir",
    summary:
      "동량 배합의 교본. 캄파리의 쓴맛과 베르무트의 단맛이 진의 주니퍼 위에서 정확히 상쇄된다.",
    tastingNote: "동량 배합의 교본. 캄파리의 쓴맛과 베르무트의 단맛이 진의 주니퍼 위에서 정확히 상쇄된다.",
    ingredients: [
      { ko: "진", en: "Dry Gin", ml: 30 },
      {
        ko: "캄파리",
        en: "Campari",
        ml: 30,
        sub: "아페롤 — 쓴맛이 절반으로 줄고 오렌지 향이 앞섭니다. 도수도 2% 정도 내려갑니다.",
      },
      {
        ko: "스위트 베르무트",
        en: "Sweet Vermouth",
        ml: 30,
        sub: "푼트 에 메스 — 쓴맛이 더 강해집니다. 반대로 카르파노 안티카는 바닐라 톤이 올라옵니다.",
      },
      { ko: "오렌지 필", en: "Orange Peel", amount: "1조각" },
    ],
    steps: [
      "믹싱 글라스에 얼음을 가득 채우고 세 재료를 붓는다.",
      "20~25회 스터. 표면에 서리가 앉으면 충분하다.",
      "큰 얼음을 넣은 올드 패션드 글라스에 스트레인한다.",
      "오렌지 필의 껍질을 짜 오일을 뿌리고 글라스에 넣는다.",
    ],
    origin: { year: "1919년경", place: "피렌체, 이탈리아", creator: "카밀로 네그로니 백작 (구전)" },
    story: {
      title: "쓴맛을 배우는 첫 잔",
      paragraphs: [
        "아메리카노에 소다 대신 진을 넣어달라는 주문에서 시작됐다는 이야기는 확인된 문서가 없다. 다만 1919년 피렌체의 카페 카소니에서 이 배합이 팔리고 있었다는 정황은 여러 기록에서 겹친다.",
        "세 재료를 같은 양으로 쓰는 구조 덕분에 네그로니는 레시피가 아니라 비율로 기억된다. 진을 45ml로 올리면 드라이해지고, 베르무트를 45ml로 올리면 디저트에 가까워진다. 아카이브에서는 1:1:1을 기준값으로 둔다.",
      ],
    },
    profile: [2, 1, 5, 4, 4],
  },
  {
    id: "martini",
    ko: "마티니",
    en: "Dry Martini",
    base: "gin",
    abv: 30,
    sweet: "dry",
    flavors: ["herbal", "citrus"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "칵테일",
    method: "stir",
    summary: "드라이 베르무트의 양이 전부를 결정한다. 온도는 −3℃ 이하로 유지한다.",
    ingredients: [
      { ko: "진", en: "Dry Gin", ml: 60 },
      {
        ko: "드라이 베르무트",
        en: "Dry Vermouth",
        ml: 10,
        sub: "릴레 블랑 — 단맛과 감귤 향이 조금 붙습니다.",
      },
      { ko: "오렌지 비터스", en: "Orange Bitters", amount: "1 dash" },
      { ko: "레몬 필", en: "Lemon Peel", amount: "1조각" },
    ],
    steps: [
      "믹싱 글라스와 잔을 미리 얼려둔다.",
      "얼음과 재료를 넣고 25회 스터한다.",
      "얼린 칵테일 글라스에 더블 스트레인한다.",
      "레몬 필로 오일을 뿌린다. 올리브를 쓰면 짠맛 계열로 바뀐다.",
    ],
    origin: { year: "1888년 이전", place: "뉴욕, 미국", creator: "불명 — 마티네즈에서 파생" },
    story: {
      title: "가장 적게 넣는 기술",
      paragraphs: [
        "마티니의 역사는 베르무트가 줄어드는 역사다. 19세기 마티네즈는 단맛이 분명했고, 20세기 중반에 이르러 베르무트는 잔을 적시는 정도로 남았다.",
        "아카이브 기준값은 6:1이다. 여기서 베르무트를 20ml까지 올리면 50-50, 5ml 이하로 내리면 사실상 차가운 진이다.",
      ],
    },
    profile: [1, 1, 2, 4, 5],
  },
  {
    id: "gimlet",
    ko: "김렛",
    en: "Gimlet",
    base: "gin",
    abv: 26,
    sweet: "semi_dry",
    flavors: ["citrus", "sour"],
    styles: ["sour"],
    stylePrimary: "sour",
    glass: "칵테일",
    method: "shake",
    summary: "라임 코디얼의 단맛과 진의 골격이 만나는 가장 단순한 사워.",
    tastingNote: "라임 코디얼의 단맛과 진의 골격이 만나는 가장 단순한 사워.",
    ingredients: [
      { ko: "진", en: "Dry Gin", ml: 60 },
      { ko: "라임 주스", en: "Lime Juice", ml: 20 },
      {
        ko: "설탕 시럽",
        en: "Simple Syrup 1:1",
        ml: 15,
        sub: "라임 코디얼 — 원형에 가깝고 단맛이 둥글어집니다.",
      },
    ],
    steps: [
      "셰이커에 재료를 넣고 얼음을 채운다.",
      "12초 하드 셰이크.",
      "얼린 칵테일 글라스에 더블 스트레인한다.",
    ],
    origin: { year: "1928년 기록", place: "런던, 영국", creator: "영국 해군 관행에서 유래" },
    story: {
      title: "비타민C의 칵테일",
      paragraphs: [
        "라임 코디얼은 원래 항해 중 비타민 결핍을 막기 위한 보급품이었다. 진과 섞은 것은 그 다음 일이다.",
        "생라임과 시럽으로 만들면 산미가 날카롭고, 코디얼로 만들면 향이 둥글다. 두 방식은 다른 음료로 취급해도 무리가 없다.",
      ],
    },
    profile: [3, 4, 1, 2, 4],
  },
  {
    id: "gintonic",
    ko: "진토닉",
    en: "Gin & Tonic",
    base: "gin",
    abv: 12,
    sweet: "semi_dry",
    flavors: ["citrus", "bitter"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "build",
    summary: "희석의 정확도가 맛을 만든다. 얼음은 크고 단단할수록 좋다.",
    ingredients: [
      { ko: "진", en: "Dry Gin", ml: 45 },
      { ko: "토닉워터", en: "Tonic Water", ml: 120 },
      { ko: "라임 웨지", en: "Lime Wedge", amount: "1조각" },
    ],
    steps: [
      "하이볼 글라스에 얼음을 가득 채워 잔을 식힌다.",
      "진을 붓고 토닉을 글라스 벽면을 따라 천천히 따른다.",
      "바 스푼으로 한 번만 들어올린다. 탄산을 지키는 것이 목적이다.",
    ],
    origin: { year: "1850년대", place: "인도 주둔 영국군", creator: "키니네 복용 관행" },
    story: {
      title: "약이었던 배합",
      paragraphs: [
        "토닉의 키니네는 말라리아 예방약이었다. 쓴맛을 견디기 위해 진과 설탕, 라임을 더한 것이 이 잔의 시작이다.",
        "토닉의 당도가 완성도를 좌우한다. 아카이브는 진 1 : 토닉 2.5~3을 기준으로 잡는다.",
      ],
    },
    profile: [2, 2, 3, 3, 2],
  },
  {
    id: "southside",
    ko: "사우스사이드",
    en: "Southside",
    base: "gin",
    abv: 22,
    sweet: "semi_dry",
    flavors: ["herbal", "citrus"],
    styles: ["sour"],
    stylePrimary: "sour",
    glass: "칵테일",
    method: "shake",
    summary: "민트를 넣은 진 사워. 모히토의 진 버전으로 읽어도 된다.",
    tastingNote: "민트를 넣은 진 사워. 모히토의 진 버전으로 읽어도 된다.",
    ingredients: [
      { ko: "진", en: "Dry Gin", ml: 50 },
      { ko: "레몬 주스", en: "Lemon Juice", ml: 20 },
      { ko: "설탕 시럽", en: "Simple Syrup", ml: 18 },
      { ko: "민트", en: "Mint Leaves", amount: "8장" },
    ],
    steps: [
      "셰이커에 민트를 넣고 가볍게 눌러 향만 낸다.",
      "나머지 재료와 얼음을 넣고 10초 셰이크.",
      "더블 스트레인해 민트 잎 하나를 띄운다.",
    ],
    origin: { year: "1920년대", place: "시카고 / 뉴욕", creator: "금주법 시대 클럽" },
    story: {
      title: "금주법의 잔",
      paragraphs: [
        "거칠던 밀주 진의 맛을 감추기 위해 민트와 레몬을 썼다는 설명이 오래 따라다닌다.",
        "민트를 세게 눌러 으깨면 풀비린내가 난다. 향만 깨우는 정도가 기준이다.",
      ],
    },
    profile: [3, 4, 1, 4, 3],
  },
  {
    id: "mule",
    ko: "모스코 뮬",
    en: "Moscow Mule",
    base: "vodka",
    abv: 12,
    sweet: "semi_sweet",
    flavors: ["spicy", "citrus"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "구리 머그",
    method: "build",
    summary: "진저비어의 매운맛이 중심. 보드카는 뼈대만 세운다.",
    tastingNote: "진저비어의 매운맛이 중심. 보드카는 뼈대만 세운다.",
    ingredients: [
      { ko: "보드카", en: "Vodka", ml: 45 },
      { ko: "라임 주스", en: "Lime Juice", ml: 15 },
      {
        ko: "진저비어",
        en: "Ginger Beer",
        ml: 120,
        sub: "진저에일 — 매운맛이 크게 줄어듭니다. 생강 시럽 5ml를 더해 보완합니다.",
      },
    ],
    steps: [
      "머그에 얼음을 채우고 보드카와 라임을 붓는다.",
      "진저비어로 채운 뒤 한 번만 젓는다.",
      "라임 웨지를 올린다.",
    ],
    origin: { year: "1941년", place: "로스앤젤레스, 미국", creator: "잭 모건 · 존 마틴" },
    story: {
      title: "재고 처리의 성공작",
      paragraphs: [
        "팔리지 않던 보드카와 진저비어를 묶어 팔기 위한 상업적 발명이었다.",
        "구리 머그는 마케팅에서 왔지만, 열전도가 빨라 실제로 잔이 더 차게 느껴진다.",
      ],
    },
    profile: [3, 3, 1, 3, 2],
  },
  {
    id: "espresso",
    ko: "에스프레소 마티니",
    en: "Espresso Martini",
    base: "vodka",
    abv: 20,
    sweet: "semi_sweet",
    flavors: ["bitter", "nutty", "fruity"],
    styles: ["creamy"],
    stylePrimary: "creamy",
    glass: "칵테일",
    method: "shake",
    summary: "크레마 층이 완성도의 지표. 커피는 뽑은 직후에 쓴다.",
    ingredients: [
      { ko: "보드카", en: "Vodka", ml: 45 },
      { ko: "에스프레소", en: "Espresso", ml: 30 },
      {
        ko: "커피 리큐어",
        en: "Coffee Liqueur",
        ml: 15,
        sub: "깔루아 대신 미스터 블랙 — 단맛이 줄고 커피 강도가 올라갑니다.",
      },
      { ko: "설탕 시럽", en: "Simple Syrup", ml: 5 },
    ],
    steps: [
      "에스프레소를 뽑아 곧바로 셰이커에 넣는다.",
      "얼음을 채우고 15초 강하게 셰이크한다.",
      "더블 스트레인하고 크레마가 자리 잡을 때까지 20초 둔다.",
      "커피 원두 세 알을 올린다.",
    ],
    origin: { year: "1983년", place: "런던, 영국", creator: "딕 브래드셀" },
    story: {
      title: "깨워달라는 주문",
      paragraphs: [
        "한 손님이 정신을 차리게 해달라고 부탁한 데서 나왔다는 일화가 널리 알려져 있다.",
        "거품은 커피의 오일과 이산화탄소에서 나온다. 뽑고 1분이 지난 에스프레소로는 같은 층이 생기지 않는다.",
      ],
    },
    profile: [3, 1, 4, 4, 3],
  },
  {
    id: "bloody",
    ko: "블러디 메리",
    en: "Bloody Mary",
    base: "vodka",
    abv: 12,
    sweet: "dry",
    flavors: ["spicy", "sour"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "build",
    summary: "짠맛·감칠맛 계열. 단맛이 거의 없는 유일한 브런치 잔.",
    tastingNote: "짠맛·감칠맛 계열. 단맛이 거의 없는 유일한 브런치 잔.",
    ingredients: [
      { ko: "보드카", en: "Vodka", ml: 45 },
      { ko: "토마토 주스", en: "Tomato Juice", ml: 120 },
      { ko: "레몬 주스", en: "Lemon Juice", ml: 15 },
      { ko: "우스터소스 · 타바스코", en: "Worcestershire · Tabasco", amount: "각 2 dash" },
    ],
    steps: [
      "하이볼 글라스에 얼음을 채운다.",
      "모든 재료를 넣고 롤링(잔 사이를 오가며 섞기)한다.",
      "셀러리와 후추로 마무리한다.",
    ],
    origin: { year: "1921년경", place: "파리 → 뉴욕", creator: "페르낭 프티오" },
    story: {
      title: "식사에 가까운 잔",
      paragraphs: [
        "스파이스와 산·염을 함께 쓰는 구조라 칵테일보다 수프의 조리 논리에 가깝다.",
        "정답 레시피가 없는 대신 균형 원칙이 있다. 산 : 염 : 매운맛을 각각 따로 조절한다.",
      ],
    },
    profile: [1, 3, 2, 3, 2],
  },
  {
    id: "cosmo",
    ko: "코즈모폴리탄",
    en: "Cosmopolitan",
    base: "vodka",
    abv: 22,
    sweet: "semi_sweet",
    flavors: ["fruity", "citrus"],
    styles: ["sour"],
    stylePrimary: "sour",
    glass: "칵테일",
    method: "shake",
    summary: "크랜베리의 색과 트리플 섹의 오렌지 향. 산미가 축이 된다.",
    tastingNote: "크랜베리의 색과 트리플 섹의 오렌지 향. 산미가 축이 된다.",
    ingredients: [
      { ko: "시트론 보드카", en: "Citron Vodka", ml: 45 },
      {
        ko: "쿠앵트로",
        en: "Cointreau",
        ml: 15,
        sub: "트리플 섹 — 단맛이 더 직선적이고 향의 층이 얇아집니다.",
      },
      { ko: "라임 주스", en: "Lime Juice", ml: 15 },
      { ko: "크랜베리 주스", en: "Cranberry Juice", ml: 30 },
    ],
    steps: [
      "모든 재료와 얼음을 셰이커에 넣는다.",
      "10초 셰이크.",
      "얼린 칵테일 글라스에 더블 스트레인하고 오렌지 필로 오일을 뿌린다.",
    ],
    origin: { year: "1987년", place: "샌프란시스코, 미국", creator: "토비 체키니 (통설)" },
    story: {
      title: "색이 만든 유행",
      paragraphs: [
        "1990년대에 이 잔이 팔린 이유의 절반은 맛이 아니라 색이었다.",
        "크랜베리를 60ml까지 늘리면 주스에 가까워진다. 30ml가 산미와 색을 모두 지키는 지점이다.",
      ],
    },
    profile: [3, 4, 1, 3, 3],
  },
  {
    id: "oldfashioned",
    ko: "올드 패션드",
    en: "Old Fashioned",
    base: "whisky",
    abv: 32,
    sweet: "semi_dry",
    flavors: ["spicy", "bitter"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "올드 패션드",
    method: "stir",
    summary: "술·설탕·비터스·물. 칵테일의 정의 그 자체.",
    ingredients: [
      {
        ko: "버번 또는 라이",
        en: "Bourbon / Rye",
        ml: 60,
        sub: "라이 위스키 — 스파이스가 앞서고 단맛이 마릅니다.",
      },
      { ko: "설탕 시럽", en: "Rich Syrup 2:1", ml: 7 },
      { ko: "앙고스투라 비터스", en: "Angostura Bitters", amount: "2 dash" },
      { ko: "오렌지 필", en: "Orange Peel", amount: "1조각" },
    ],
    steps: [
      "잔에 시럽과 비터스를 넣고 섞는다.",
      "위스키를 절반 붓고 얼음 하나로 젓는다.",
      "남은 위스키와 큰 얼음을 넣고 20회 젓는다.",
      "오렌지 필로 오일을 뿌린다.",
    ],
    origin: { year: "1880년대", place: "루이빌, 미국", creator: "펜던니스 클럽 (통설)" },
    story: {
      title: "원형이라는 이름",
      paragraphs: [
        "“옛날식으로”라는 주문이 그대로 이름이 됐다. 즉 이 잔은 어떤 칵테일보다 오래된 구조를 가리킨다.",
        "희석이 유일한 변수다. 큰 얼음 하나로 천천히 마시는 전제로 설계된 배합이다.",
      ],
    },
    profile: [2, 1, 3, 4, 5],
  },
  {
    id: "manhattan",
    ko: "맨해튼",
    en: "Manhattan",
    base: "whisky",
    abv: 30,
    sweet: "semi_dry",
    flavors: ["bitter", "fruity"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "칵테일",
    method: "stir",
    summary: "라이의 스파이스와 스위트 베르무트. 네그로니와 마티니 사이.",
    tastingNote: "라이의 스파이스와 스위트 베르무트. 네그로니와 마티니 사이.",
    ingredients: [
      { ko: "라이 위스키", en: "Rye Whiskey", ml: 60 },
      { ko: "스위트 베르무트", en: "Sweet Vermouth", ml: 30 },
      { ko: "앙고스투라 비터스", en: "Angostura Bitters", amount: "2 dash" },
      { ko: "마라스키노 체리", en: "Cherry", amount: "1개" },
    ],
    steps: [
      "믹싱 글라스에 재료와 얼음을 넣는다.",
      "25회 스터.",
      "얼린 칵테일 글라스에 스트레인하고 체리를 넣는다.",
    ],
    origin: { year: "1880년대", place: "뉴욕, 미국", creator: "맨해튼 클럽 (통설)" },
    story: {
      title: "비율의 문제",
      paragraphs: [
        "2:1은 기준이고, 1:1은 퍼펙트에 가깝다. 베르무트의 상태가 이 잔의 수명을 결정한다.",
        "개봉한 베르무트는 냉장 보관해도 3주가 한계다. 맨해튼이 실패하는 대부분의 이유가 여기에 있다.",
      ],
    },
    profile: [3, 1, 3, 4, 5],
  },
  {
    id: "whiskeysour",
    ko: "위스키 사워",
    en: "Whiskey Sour",
    base: "whisky",
    abv: 20,
    sweet: "semi_sweet",
    flavors: ["sour", "citrus", "creamy"],
    styles: ["sour"],
    stylePrimary: "sour",
    glass: "쿠페",
    method: "shake",
    summary: "사워 공식(술 2 : 산 1 : 당 1)의 표준 예시.",
    ingredients: [
      { ko: "버번", en: "Bourbon", ml: 50 },
      { ko: "레몬 주스", en: "Lemon Juice", ml: 25 },
      { ko: "설탕 시럽", en: "Simple Syrup", ml: 20 },
      {
        ko: "달걀 흰자",
        en: "Egg White",
        amount: "15ml",
        sub: "아쿠아파바 15ml — 비건 대체가 가능하고 거품이 더 안정적입니다.",
      },
    ],
    steps: [
      "얼음 없이 재료를 넣고 15초 드라이 셰이크한다.",
      "얼음을 넣고 다시 12초 셰이크한다.",
      "쿠페 글라스에 더블 스트레인하고 비터스로 표면에 점을 찍는다.",
    ],
    origin: { year: "1862년 수록", place: "미국", creator: "제리 토머스 저서" },
    story: {
      title: "공식으로서의 사워",
      paragraphs: [
        "사워는 이름이 아니라 비율이다. 술과 산, 당의 삼각형만 지키면 재료는 교체 가능하다.",
        "흰자는 맛보다 질감을 위한 재료다. 넣지 않으면 산미가 더 뚜렷해진다.",
      ],
    },
    profile: [3, 5, 1, 3, 3],
  },
  {
    id: "penicillin",
    ko: "페니실린",
    en: "Penicillin",
    base: "whisky",
    abv: 22,
    sweet: "semi_sweet",
    flavors: ["smoky", "spicy"],
    styles: ["sour"],
    stylePrimary: "sour",
    glass: "올드 패션드",
    method: "shake",
    summary: "생강·꿀·레몬에 아일라 위스키의 연기를 얹은 현대 고전.",
    tastingNote: "생강·꿀·레몬에 아일라 위스키의 연기를 얹은 현대 고전.",
    ingredients: [
      { ko: "블렌디드 스코치", en: "Blended Scotch", ml: 50 },
      { ko: "아일라 싱글몰트", en: "Islay Single Malt", ml: 7 },
      { ko: "레몬 주스", en: "Lemon Juice", ml: 22 },
      {
        ko: "생강 꿀 시럽",
        en: "Honey-Ginger Syrup",
        ml: 22,
        sub: "꿀 시럽 + 생강즙 5ml로 즉석 대체가 가능합니다.",
      },
    ],
    steps: [
      "아일라를 뺀 재료를 셰이크한다.",
      "얼음을 넣은 잔에 스트레인한다.",
      "아일라 위스키를 표면에 띄운다(플로트).",
      "생강 절임을 올린다.",
    ],
    origin: { year: "2005년", place: "뉴욕, 미국", creator: "샘 로스" },
    story: {
      title: "현대의 고전",
      paragraphs: [
        "2000년대 이후 만들어진 칵테일 중 가장 널리 복제된 배합이다.",
        "연기를 섞지 않고 위에 띄우는 것이 핵심이다. 첫 향과 끝 맛이 분리된다.",
      ],
    },
    profile: [3, 4, 2, 5, 3],
  },
  {
    id: "boulevardier",
    ko: "불바디에",
    en: "Boulevardier",
    base: "whisky",
    abv: 26,
    sweet: "semi_dry",
    flavors: ["bitter", "fruity"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "올드 패션드",
    method: "stir",
    summary: "네그로니의 위스키 버전. 진보다 무게가 있고 단맛이 길다.",
    tastingNote: "네그로니의 위스키 버전. 진보다 무게가 있고 단맛이 길다.",
    ingredients: [
      { ko: "버번 또는 라이", en: "Bourbon / Rye", ml: 45 },
      { ko: "캄파리", en: "Campari", ml: 30 },
      { ko: "스위트 베르무트", en: "Sweet Vermouth", ml: 30 },
    ],
    steps: [
      "믹싱 글라스에 재료와 얼음을 넣는다.",
      "25회 스터.",
      "큰 얼음 위에 스트레인하고 오렌지 필을 짠다.",
    ],
    origin: { year: "1927년", place: "파리, 프랑스", creator: "어스킨 그웬 (잡지 편집자)" },
    story: {
      title: "잡지에서 나온 잔",
      paragraphs: [
        "파리에서 발행된 소책자에 실린 배합이 그대로 이름과 함께 남았다.",
        "위스키를 45ml로 올려 캄파리보다 우위에 두는 것이 현행 표준이다.",
      ],
    },
    profile: [3, 1, 4, 4, 4],
  },
  {
    id: "daiquiri",
    ko: "다이키리",
    en: "Daiquiri",
    base: "rum",
    abv: 24,
    sweet: "semi_dry",
    flavors: ["citrus", "sour"],
    styles: ["sour"],
    stylePrimary: "sour",
    glass: "쿠페",
    method: "shake",
    summary: "럼의 품질이 그대로 드러난다. 세 재료 뒤에 숨을 곳이 없다.",
    ingredients: [
      { ko: "화이트 럼", en: "White Rum", ml: 60 },
      { ko: "라임 주스", en: "Lime Juice", ml: 25 },
      { ko: "설탕 시럽", en: "Simple Syrup", ml: 18 },
    ],
    steps: [
      "재료와 얼음을 셰이커에 넣는다.",
      "12초 하드 셰이크.",
      "얼린 쿠페 글라스에 더블 스트레인한다.",
    ],
    origin: { year: "1898년경", place: "산티아고, 쿠바", creator: "제닝스 콕스 (통설)" },
    story: {
      title: "바텐더의 시험지",
      paragraphs: [
        "바를 평가할 때 다이키리를 주문하는 관행은 이 잔이 기술을 감추지 못하기 때문이다.",
        "시럽 18ml는 라임의 산도에 따라 조정한다. 라임이 날카로운 계절에는 20ml까지 올린다.",
      ],
    },
    profile: [3, 4, 1, 3, 4],
  },
  {
    id: "mojito",
    ko: "모히토",
    en: "Mojito",
    base: "rum",
    abv: 13,
    sweet: "semi_sweet",
    flavors: ["herbal", "citrus"],
    styles: ["highball", "sour"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "build",
    summary: "민트 향, 라임 산미, 탄산의 세 층이 분리되어 있어야 한다.",
    tastingNote: "민트 향, 라임 산미, 탄산의 세 층이 분리되어 있어야 한다.",
    ingredients: [
      { ko: "화이트 럼", en: "White Rum", ml: 45 },
      { ko: "라임 주스", en: "Lime Juice", ml: 20 },
      { ko: "설탕 시럽", en: "Simple Syrup", ml: 20 },
      { ko: "민트", en: "Mint", amount: "10장" },
      { ko: "소다", en: "Soda Water", ml: 60 },
    ],
    steps: [
      "잔에 민트와 시럽을 넣고 가볍게 누른다.",
      "라임과 럼을 붓고 크러시드 아이스를 채운다.",
      "스푼으로 아래에서 위로 한 번 들어올린다.",
      "소다로 채우고 민트 다발을 올린다.",
    ],
    origin: { year: "19세기", place: "하바나, 쿠바", creator: "불명" },
    story: {
      title: "눌러 으깨지 않는다",
      paragraphs: [
        "민트를 으깨면 엽록소의 쓴맛이 나온다. 향이 올라올 정도로만 압을 준다.",
        "크러시드 아이스는 희석 속도가 빠르다. 그래서 시럽이 20ml까지 들어간다.",
      ],
    },
    profile: [3, 3, 1, 4, 2],
  },
  {
    id: "maitai",
    ko: "마이 타이",
    en: "Mai Tai",
    base: "rum",
    abv: 26,
    sweet: "semi_sweet",
    flavors: ["fruity", "citrus", "nutty"],
    styles: ["tiki", "sour"],
    stylePrimary: "tiki",
    glass: "올드 패션드",
    method: "shake",
    summary: "오르자(아몬드 시럽)가 향의 중심. 과일 주스는 들어가지 않는다.",
    tastingNote: "오르자(아몬드 시럽)가 향의 중심. 과일 주스는 들어가지 않는다.",
    ingredients: [
      { ko: "자메이카 럼", en: "Jamaican Rum", ml: 30 },
      { ko: "아그리콜 럼", en: "Rhum Agricole", ml: 30 },
      {
        ko: "오렌지 큐라소",
        en: "Orange Curaçao",
        ml: 15,
        sub: "쿠앵트로 — 색이 맑아지고 단맛이 가벼워집니다.",
      },
      { ko: "오르자 시럽", en: "Orgeat", ml: 15 },
      { ko: "라임 주스", en: "Lime Juice", ml: 22 },
    ],
    steps: [
      "크러시드 아이스와 재료를 셰이커에 넣고 짧게 흔든다.",
      "잔에 얼음까지 그대로 붓는다.",
      "짜낸 라임 껍질과 민트를 올린다.",
    ],
    origin: { year: "1944년", place: "오클랜드, 미국", creator: "빅터 버제론" },
    story: {
      title: "파인애플은 없다",
      paragraphs: [
        "대량 판매용 마이 타이가 과일 주스로 채워지며 원형과 멀어졌다. 원 배합에 파인애플은 없다.",
        "두 종류의 럼을 쓰는 이유는 무게(자메이카)와 풀 향(아그리콜)을 동시에 얻기 위해서다.",
      ],
    },
    profile: [4, 4, 1, 5, 4],
  },
  {
    id: "darknstormy",
    ko: "다크 앤 스토미",
    en: "Dark ’n’ Stormy",
    base: "rum",
    abv: 14,
    sweet: "semi_sweet",
    flavors: ["spicy", "fruity"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "build",
    summary: "층을 만들어 마시는 구조. 섞지 않고 낸다.",
    ingredients: [
      { ko: "다크 럼", en: "Dark Rum", ml: 60 },
      { ko: "진저비어", en: "Ginger Beer", ml: 120 },
      { ko: "라임 웨지", en: "Lime Wedge", amount: "1조각" },
    ],
    steps: [
      "잔에 얼음과 진저비어를 먼저 붓는다.",
      "다크 럼을 스푼 뒤로 흘려 위에 띄운다.",
      "라임을 곁들여 그대로 낸다.",
    ],
    origin: { year: "20세기 초", place: "버뮤다", creator: "고슬링스 럼" },
    story: {
      title: "상표가 된 배합",
      paragraphs: [
        "버뮤다에서는 이 이름과 배합이 상표로 관리된다.",
        "럼을 띄우면 첫 모금은 생강, 마지막은 당밀이다. 섞으면 이 대비가 사라진다.",
      ],
    },
    profile: [4, 2, 1, 3, 2],
  },
  {
    id: "margarita",
    ko: "마르가리타",
    en: "Margarita",
    base: "agave",
    abv: 24,
    sweet: "semi_dry",
    flavors: ["citrus", "sour"],
    styles: ["sour"],
    stylePrimary: "sour",
    glass: "쿠페",
    method: "shake",
    summary: "소금·산·단맛 세 축의 균형. 데킬라는 100% 아가베를 쓴다.",
    tastingNote: "소금·산·단맛 세 축의 균형. 데킬라는 100% 아가베를 쓴다.",
    ingredients: [
      { ko: "블랑코 데킬라", en: "Blanco Tequila", ml: 50 },
      {
        ko: "쿠앵트로",
        en: "Cointreau",
        ml: 20,
        sub: "트리플 섹 — 향의 폭이 좁아집니다. 아가베 시럽 10ml로 바꾸면 토미스 스타일이 됩니다.",
      },
      { ko: "라임 주스", en: "Lime Juice", ml: 25 },
      { ko: "소금", en: "Salt Rim", amount: "림 절반" },
    ],
    steps: [
      "잔 테두리 절반에만 소금을 묻힌다.",
      "재료와 얼음을 넣고 12초 셰이크.",
      "더블 스트레인하고 라임 휠을 올린다.",
    ],
    origin: { year: "1930~40년대", place: "멕시코 / 미국 국경", creator: "다수의 주장" },
    story: {
      title: "소금은 절반만",
      paragraphs: [
        "림 전체에 소금을 묻히면 선택권이 사라진다. 절반만 묻히는 것이 현행 관행이다.",
        "쿠앵트로를 아가베 시럽으로 바꾸면 데킬라의 식물성 향이 훨씬 선명해진다.",
      ],
    },
    profile: [3, 5, 1, 3, 4],
  },
  {
    id: "paloma",
    ko: "팔로마",
    en: "Paloma",
    base: "agave",
    abv: 14,
    sweet: "semi_sweet",
    flavors: ["citrus", "fruity"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "build",
    summary: "멕시코에서 실제로 가장 많이 마시는 데킬라 롱드링크.",
    ingredients: [
      { ko: "블랑코 데킬라", en: "Blanco Tequila", ml: 50 },
      {
        ko: "자몽 소다",
        en: "Grapefruit Soda",
        ml: 120,
        sub: "생자몽 60ml + 소다 60ml + 시럽 5ml로 대체하면 단맛이 크게 줄어듭니다.",
      },
      { ko: "라임 주스", en: "Lime Juice", ml: 15 },
      { ko: "소금", en: "Salt", amount: "1핀치" },
    ],
    steps: [
      "잔에 얼음을 채우고 데킬라와 라임을 넣는다.",
      "자몽 소다로 채운다.",
      "소금 한 꼬집을 넣고 한 번 젓는다.",
    ],
    origin: { year: "1950년대", place: "멕시코", creator: "불명" },
    story: {
      title: "소금 한 꼬집",
      paragraphs: [
        "소금은 짠맛을 위해서가 아니라 자몽의 쓴맛을 눌러 단맛을 끌어올리기 위해 들어간다.",
        "생자몽으로 만들면 완전히 다른 잔이 된다. 아카이브는 두 방식을 모두 표준으로 본다.",
      ],
    },
    profile: [3, 3, 2, 3, 2],
  },
  {
    id: "sojuhighball",
    ko: "소주 하이볼",
    en: "Soju Highball",
    base: "korean",
    abv: 9,
    sweet: "semi_dry",
    flavors: ["citrus"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "build",
    summary: "증류식 소주의 곡물 향을 탄산으로 늘린 구조. 희석률이 관건.",
    tastingNote: "증류식 소주의 곡물 향을 탄산으로 늘린 구조. 희석률이 관건.",
    ingredients: [
      {
        ko: "증류식 소주",
        en: "Distilled Soju",
        ml: 45,
        sub: "40도대 소주를 쓰면 30ml로 줄입니다.",
      },
      { ko: "탄산수", en: "Soda Water", ml: 120 },
      { ko: "레몬 필", en: "Lemon Peel", amount: "1조각" },
    ],
    steps: [
      "잔과 소주를 미리 차게 둔다.",
      "얼음을 가득 채우고 소주를 붓는다.",
      "탄산수를 벽면을 따라 붓고 한 번만 들어올린다.",
      "레몬 필의 오일을 뿌린다.",
    ],
    origin: { year: "2010년대", place: "서울, 한국", creator: "국내 바 씬" },
    story: {
      title: "희석의 문화",
      paragraphs: [
        "일본식 하이볼 문법이 증류식 소주에 그대로 옮겨오면서 정착한 형식이다.",
        "25도 소주는 1:2.5, 40도대는 1:4를 기준으로 잡는다. 곡물 향이 남는 선이다.",
      ],
    },
    profile: [2, 1, 1, 3, 2],
  },
  {
    id: "munbae",
    ko: "문배 올드 패션드",
    en: "Munbae Old Fashioned",
    base: "korean",
    abv: 28,
    sweet: "semi_dry",
    flavors: ["smoky", "spicy"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "올드 패션드",
    method: "stir",
    summary: "문배주의 배·수수 향을 올드 패션드 구조에 넣은 응용 배합.",
    tastingNote: "문배주의 배·수수 향을 올드 패션드 구조에 넣은 응용 배합.",
    ingredients: [
      { ko: "문배주 40도", en: "Munbaeju 40%", ml: 60 },
      {
        ko: "조청 시럽",
        en: "Jocheong Syrup",
        ml: 8,
        sub: "꿀 시럽 — 곡물 향이 줄고 꽃 향이 붙습니다.",
      },
      { ko: "앙고스투라 비터스", en: "Angostura Bitters", amount: "2 dash" },
      { ko: "배 껍질", en: "Pear Peel", amount: "1조각" },
    ],
    steps: [
      "잔에 조청 시럽과 비터스를 넣고 섞는다.",
      "문배주를 붓고 큰 얼음으로 20회 젓는다.",
      "배 껍질의 향을 뿌리고 넣는다.",
    ],
    origin: { year: "2010년대 응용", place: "한국", creator: "아카이브 편집부 배합" },
    story: {
      title: "전통주의 좌표",
      paragraphs: [
        "문배주는 배를 넣지 않는데도 배 향이 난다. 이 향은 수수와 좁쌀의 발효에서 온다.",
        "조청은 설탕보다 점도가 높아 5~8ml에서 이미 충분한 무게가 붙는다.",
      ],
    },
    profile: [2, 1, 3, 5, 5],
  },
  {
    id: "makgeolli",
    ko: "막걸리 콜라다",
    en: "Makgeolli Colada",
    base: "korean",
    abv: 8,
    sweet: "sweet",
    flavors: ["fruity", "creamy"],
    styles: ["creamy", "tiki"],
    stylePrimary: "creamy",
    glass: "하이볼",
    method: "shake",
    summary: "막걸리의 유산 향과 코코넛·파인애플. 가장 단 항목.",
    tastingNote: "막걸리의 유산 향과 코코넛·파인애플. 가장 단 항목.",
    ingredients: [
      { ko: "막걸리", en: "Makgeolli", ml: 90 },
      {
        ko: "화이트 럼",
        en: "White Rum",
        ml: 20,
        sub: "생략하면 무알콜에 가까운 3% 대가 됩니다.",
      },
      { ko: "코코넛 크림", en: "Coconut Cream", ml: 25 },
      { ko: "파인애플 주스", en: "Pineapple Juice", ml: 40 },
    ],
    steps: [
      "막걸리를 흔들어 침전물을 고르게 섞는다.",
      "모든 재료를 얼음과 함께 짧게 셰이크한다.",
      "크러시드 아이스 위에 붓고 파인애플을 올린다.",
    ],
    origin: { year: "2020년대", place: "한국", creator: "아카이브 편집부 배합" },
    story: {
      title: "단맛의 상한",
      paragraphs: [
        "아카이브의 24개 항목 중 당도가 가장 높다. 디저트 자리에 놓기 위한 배합이다.",
        "막걸리는 살균/비살균에 따라 산미 차이가 크다. 비살균 제품은 파인애플을 30ml로 줄인다.",
      ],
    },
    profile: [5, 2, 1, 3, 1],
  },
  {
    id: "shrub",
    ko: "시트러스 슈럽",
    en: "Citrus Shrub (NA)",
    base: "non-alcoholic",
    abv: 0,
    sweet: "semi_sweet",
    flavors: ["citrus", "sour"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "build",
    summary: "식초 기반 시럽으로 산미의 층을 만든 무알콜 항목.",
    tastingNote: "식초 기반 시럽으로 산미의 층을 만든 무알콜 항목.",
    ingredients: [
      {
        ko: "시트러스 슈럽 시럽",
        en: "Citrus Shrub Syrup",
        ml: 35,
        sub: "애플사이다 비니거 15ml + 설탕 시럽 20ml로 즉석 조합이 가능합니다.",
      },
      { ko: "탄산수", en: "Soda Water", ml: 140 },
      { ko: "자몽 주스", en: "Grapefruit Juice", ml: 30 },
      { ko: "로즈마리", en: "Rosemary", amount: "1줄기" },
    ],
    steps: [
      "잔에 얼음을 채우고 슈럽과 자몽을 붓는다.",
      "탄산수로 채우고 한 번 젓는다.",
      "로즈마리를 손바닥에 쳐 향을 내고 올린다.",
    ],
    origin: { year: "17세기 보존법", place: "유럽 → 현대 바", creator: "식초 보존 전통" },
    story: {
      title: "알코올 없는 골격",
      paragraphs: [
        "무알콜 음료가 심심해지는 이유는 대개 산미와 쓴맛이 없기 때문이다. 식초는 그 자리를 메운다.",
        "식초의 양이 3ml만 넘어도 균형이 무너진다. 슈럽 시럽으로 미리 배합해 쓰는 편이 안정적이다.",
      ],
    },
    profile: [3, 4, 2, 4, 0],
  },

  /* ─────────────────────────────────────────────────────────────────────────
   * 아래 25종은 에디터 본인의 블로그(Shaking Like Bartender)에 직접 만들어 보고
   * 쓴 기록에서 옮긴 것이다 (ISSUE-059). `summary`·`story`·`profile`은 전부
   * 그 글의 테이스팅 서술에 근거한다 — `PRIN-P03`(만들어보지 않은 것은 쓰지 않는다).
   * 사진은 같은 글의 대표 컷을 `apps/web/public/cocktails/{id}.webp`로 넣었다
   * (DECISIONS §1.12). 파일명이 곧 `id`라 나중에 레코드로 옮길 때 매핑이 자동이다.
   * ───────────────────────────────────────────────────────────────────────── */

  {
    id: "corpsereviver2",
    ko: "콥스 리바이버 넘버 2",
    en: "Corpse Reviver No.2",
    base: "gin",
    abv: 20,
    sweet: "semi_sweet",
    flavors: ["citrus", "herbal", "sour"],
    styles: ["sour"],
    stylePrimary: "sour",
    glass: "칵테일",
    method: "shake",
    summary:
      "시트러스의 새콤달콤함 위로 릴레의 와인 뉘앙스가 겹치고, 진의 보타니컬이 중심을 잡는다. 압생트는 끝에 미묘하게만 스친다.",
    tastingNote: "시트러스의 새콤달콤함 위로 릴레의 와인 뉘앙스가 겹치고, 진의 보타니컬이 중심을 잡는다. 압생트는 끝에 미묘하게만 스친다.",
    ingredients: [
      { ko: "진", en: "Dry Gin", ml: 22.5 },
      {
        ko: "릴레 블랑",
        en: "Lillet Blanc",
        ml: 22.5,
        sub: "코키 아메리카노 — 단종된 키나 릴레에 더 가깝다는 평이 있습니다. 쓴맛이 조금 올라옵니다.",
      },
      { ko: "코앵트로", en: "Cointreau", ml: 22.5 },
      { ko: "레몬 주스", en: "Lemon Juice", ml: 22.5 },
      { ko: "압생트", en: "Absinthe", amount: "1 dash (잔 린싱)" },
    ],
    steps: [
      "압생트 외의 재료를 셰이커에 넣는다.",
      "차게 해 둔 잔에 압생트를 뿌려 린싱한다. 스프레이가 없으면 1바스푼을 넣고 잔을 돌린 뒤 남은 것을 버린다.",
      "셰이커에 얼음을 채우고 셰이크한다.",
      "린싱한 잔에 따른다.",
    ],
    origin: {
      year: "1930년",
      place: "런던, 영국",
      creator: "해리 크래독 《The Savoy Cocktail Book》",
    },
    story: {
      title: "죽은 자를 깨우는 잔",
      paragraphs: [
        "콥스 리바이버라는 이름은 19세기부터 있었지만 특정 레시피가 아니라 '해장을 위한 술'이라는 뜻으로 쓰였다. 1861년 런던의 잡지 《펀치》가 슬링·스톤 월과 함께 이 이름을 언급한 것이 가장 오래된 기록이다.",
        "지금의 넘버 2는 1930년 사보이 칵테일 북에서 굳어졌다. 책은 레시피 아래에 '4잔을 연속으로 빠르게 마시면 되살아난 시체도 다시 죽을 것'이라고 적어 두었다. 원전은 동량 배합이지만 균형이 좋다고 보기 어려워 아카이브는 22.5ml 4등분을 기준으로 둔다.",
      ],
    },
    profile: [3, 4, 1, 4, 3],
  },
  {
    id: "vesper",
    ko: "베스퍼",
    en: "Vesper",
    base: "gin",
    abv: 30,
    sweet: "dry",
    flavors: ["citrus", "herbal"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "칵테일",
    method: "shake",
    summary:
      "잔을 입에 가져가면 레몬 향이 먼저 오고, 보타니컬과 술 자체의 단맛 뒤로 릴레의 와인스러운 뉘앙스가 살짝 남는다. 깔끔하지만 도수는 상당히 높다.",
    tastingNote: "잔을 입에 가져가면 레몬 향이 먼저 오고, 보타니컬과 술 자체의 단맛 뒤로 릴레의 와인스러운 뉘앙스가 살짝 남는다. 깔끔하지만 도수는 상당히 높다.",
    ingredients: [
      { ko: "진", en: "Dry Gin", ml: 45 },
      { ko: "보드카", en: "Vodka", ml: 15 },
      {
        ko: "릴레 블랑",
        en: "Lillet Blanc",
        ml: 8.75,
        sub: "코키 아메리카노 — 원전의 키나 릴레에 더 가깝습니다. 퀴닌의 쓴맛이 붙습니다.",
      },
      { ko: "레몬 필", en: "Lemon Peel", amount: "1조각" },
    ],
    steps: [
      "레몬 껍질 외의 재료를 셰이커에 넣는다.",
      "얼음을 채우고 셰이크한다.",
      "잔에 따른 뒤 레몬 껍질을 짜 오일을 뿌린다.",
      "껍질을 다듬어 장식한다.",
    ],
    origin: { year: "1953년", place: "소설 《카지노 로얄》", creator: "이언 플레밍" },
    story: {
      title: "본드가 직접 읊은 배합",
      paragraphs: [
        "제임스 본드가 바텐더에게 배합을 불러 주는 장면에서 등장한다. 고든스 3, 보드카 1, 키나 릴레 0.5를 얼음처럼 차가워질 때까지 셰이크하고 레몬 껍질을 크게 저민 것. 이름은 연인 베스퍼 린드에서 따왔고, 이유는 '한 번 맛보고 나면 다른 건 마실 수 없기 때문'이었다.",
        "원전의 키나 릴레는 1986년에 퀴닌을 줄이고 단맛을 올리며 릴레 블랑으로 바뀌었다. 술만 들어가는데 왜 셰이킹인가는 오래된 질문인데, 스터로 만들어도 문제는 없고 차이는 결국 잔에 들어가는 물의 양이다.",
      ],
    },
    profile: [1, 1, 1, 4, 5],
  },
  {
    id: "whitenegroni",
    ko: "화이트 네그로니",
    en: "White Negroni",
    base: "gin",
    abv: 24,
    sweet: "semi_dry",
    flavors: ["bitter", "herbal"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "올드 패션드",
    method: "stir",
    summary:
      "진의 보타니컬이 중심을 잡고 수즈의 달큰하면서 쌉쌀한 뿌리 식물 캐릭터가 은은하게 올라온다. 클래식 네그로니보다 한껏 가볍고 섬세하다.",
    tastingNote: "진의 보타니컬이 중심을 잡고 수즈의 달큰하면서 쌉쌀한 뿌리 식물 캐릭터가 은은하게 올라온다. 클래식 네그로니보다 한껏 가볍고 섬세하다.",
    ingredients: [
      { ko: "진", en: "Dry Gin", ml: 45 },
      { ko: "릴레 블랑", en: "Lillet Blanc", ml: 30 },
      {
        ko: "수즈",
        en: "Suze",
        ml: 22.5,
        sub: "아베즈 — 같은 용담(젠티아나) 리큐르입니다. 국내 수입되는 대안이지만 맛은 조금 다릅니다.",
      },
      { ko: "레몬 필", en: "Lemon Peel", amount: "1조각" },
    ],
    steps: [
      "락 글라스에 모든 재료를 넣는다.",
      "얼음을 채우고 스터한다.",
      "레몬 껍질의 오일을 잔 위에 뿌린다.",
      "껍질을 다듬어 잔에 넣는다. 자몽 껍질을 쓰면 쌉쌀함이 더 산다.",
    ],
    origin: { year: "2001년", place: "메독, 프랑스", creator: "웨인 콜린스 · 닉 블랙넬" },
    story: {
      title: "프랑스 재료로 만든 네그로니",
      paragraphs: [
        "2001년 여름, 플리머스 진의 디렉터 닉 블랙넬과 런던의 바텐더 웨인 콜린스는 각각 주류 박람회와 칵테일 대회 때문에 프랑스에 있었다. 보르도 근처 메독의 작은 마을에는 괜찮은 바가 없었고, 둘은 리쿼샵에서 네그로니 재료를 찾다가 '프랑스 재료로 만들어 보자'는 데 이르렀다.",
        "그래서 캄파리 자리에 수즈, 스위트 베르무트 자리에 릴레 블랑이 들어갔다. 이름은 블랙넬이 붙였는데, 네그로니의 어두운 적갈색과 정반대로 부르자는 뜻이었다. 실제 색은 금색에 가깝다.",
      ],
    },
    profile: [2, 1, 4, 4, 4],
  },
  {
    id: "stayuplate",
    ko: "스테이 업 레이트",
    en: "Stay Up Late",
    base: "gin",
    abv: 11,
    sweet: "semi_sweet",
    flavors: ["citrus", "spicy"],
    styles: ["sour", "highball"],
    stylePrimary: "sour",
    glass: "하이볼",
    method: "shake",
    summary:
      "진 피즈에 꼬냑을 얹은 구조라 사이드카와 진 피즈를 매시업한 듯한 맛이 난다. 유자는 전혀 들어가지 않는데 유자청 같은 인상이 남는다.",
    tastingNote: "진 피즈에 꼬냑을 얹은 구조라 사이드카와 진 피즈를 매시업한 듯한 맛이 난다. 유자는 전혀 들어가지 않는데 유자청 같은 인상이 남는다.",
    ingredients: [
      { ko: "진", en: "Dry Gin", ml: 40 },
      { ko: "꼬냑", en: "Cognac", ml: 10 },
      { ko: "레몬 주스", en: "Lemon Juice", ml: 20 },
      { ko: "리치 시럽", en: "Rich Simple Syrup", ml: 10, sub: "설탕 2 : 물 1로 끓여 만듭니다." },
      { ko: "탄산수", en: "Soda Water", ml: 80 },
      { ko: "레몬 필", en: "Lemon Peel", amount: "1조각" },
    ],
    steps: [
      "탄산수를 제외한 재료를 셰이커에 넣는다.",
      "얼음을 채우고 셰이크한다.",
      "긴 잔에 얼음을 채우고 셰이커에서 붓는다.",
      "탄산수를 적당량 붓고 얼음을 위아래로 살짝 들었다 놓는다.",
      "레몬 껍질의 오일을 짜 뿌리고 다듬어 넣는다.",
    ],
    origin: {
      year: "1946년",
      place: "뉴욕, 미국",
      creator: "《The Stork Club Bar Book》 부록 · 베로니카 해롤드",
    },
    story: {
      title: "나이트클럽에서 온 이름",
      paragraphs: [
        "언론인 루시우스 비비가 1946년에 쓴 《스토크 클럽 바 북》 부록에 실린 칵테일이다. 스토크 클럽은 1929년부터 1965년까지 뉴욕 맨해튼에서 운영된 나이트클럽으로 유명인이 자주 찾던 곳이었다.",
        "부록은 클럽 스태프를 취재해 덧붙인 목록인데, 이 잔은 모자 관리 부서의 베로니카 해롤드가 올린 것이다. 그녀가 만든 것인지는 알 수 없지만 이름만큼은 나이트클럽에 잘 어울린다. 원전은 비율이 꽤 달라 아카이브는 조정된 배합을 기준으로 둔다.",
      ],
    },
    profile: [3, 4, 1, 4, 2],
  },
  {
    id: "kaikanfizz",
    ko: "카이칸 피즈",
    en: "Kaikan Fizz",
    base: "gin",
    abv: 10,
    sweet: "semi_sweet",
    flavors: ["creamy", "citrus"],
    styles: ["sour", "creamy"],
    stylePrimary: "sour",
    glass: "하이볼",
    method: "shake",
    summary:
      "우유가 들어간 진 피즈. 일반 진 피즈보다 부드럽고 실키하며, 칼피스나 밀키스를 살짝 떠올리게 하는 맛이 난다.",
    tastingNote: "우유가 들어간 진 피즈. 일반 진 피즈보다 부드럽고 실키하며, 칼피스나 밀키스를 살짝 떠올리게 하는 맛이 난다.",
    ingredients: [
      { ko: "진", en: "Dry Gin", ml: 45 },
      { ko: "레몬 주스", en: "Lemon Juice", ml: 11 },
      { ko: "리치 시럽", en: "Rich Simple Syrup", ml: 9 },
      { ko: "우유", en: "Milk", ml: 45 },
      { ko: "탄산수", en: "Soda Water", ml: 60 },
    ],
    steps: [
      "탄산수를 제외한 재료를 셰이커에 넣는다.",
      "얼음을 넣고 다소 길게 셰이크한다.",
      "긴 잔에 얼음을 채우고 셰이커에서 붓는다.",
      "탄산수를 살살 붓는다. 세게 부으면 거품이 순식간에 넘친다.",
      "얼음을 위아래로 두세 번 들었다 놓는다.",
    ],
    origin: { year: "1945~1952년", place: "도쿄, 일본", creator: "도쿄카이칸 메인 바" },
    story: {
      title: "우유로 변장한 진 피즈",
      paragraphs: [
        "전후 도쿄카이칸은 연합군 최고사령부에 의해 '아메리칸 클럽 오브 도쿄'라는 장교 클럽으로 쓰였다. 가장 널리 알려진 이야기는 장교들이 낮부터 몰래 마시기 위해 진 피즈에 우유를 넣어 달라고 했다는 것이다. 마치 우유를 마시는 것처럼 보이도록.",
        "우유와 레몬을 같이 쓰는 건 사실 좋은 조합이 아니다. 레몬의 산이 우유의 단백질을 응고시키기 때문이다. 게다가 탄산수를 세게 부으면 거품이 넘친다. 그래서 일반 진 피즈보다 만들기 까다로운 잔으로 통한다.",
      ],
    },
    profile: [3, 3, 1, 3, 2],
  },
  {
    id: "caipiroska",
    ko: "카이피로스카",
    en: "Caipiroska",
    base: "vodka",
    abv: 22,
    sweet: "semi_sweet",
    flavors: ["citrus", "sour"],
    styles: ["sour"],
    stylePrimary: "sour",
    glass: "올드 패션드",
    method: "etc",
    summary: "달고 상큼해 리프레시하기 좋다. 맛이 비교적 직관적이고 단순한 편이다.",
    tastingNote: "달고 상큼해 리프레시하기 좋다. 맛이 비교적 직관적이고 단순한 편이다.",
    ingredients: [
      {
        ko: "보드카",
        en: "Vodka",
        ml: 55,
        sub: "카샤사 — 원형인 카이피리냐가 됩니다. 풀 같은 거친 향이 붙습니다.",
      },
      { ko: "라임", en: "Lime", amount: "반 개 (6~9등분 깍둑썰기)" },
      { ko: "설탕", en: "Sugar", amount: "3~4 barspoon" },
    ],
    steps: [
      "라임 반 개를 머들링하기 좋게 6~9등분으로 깍둑썬다.",
      "라임과 설탕을 잔에 넣고 머들링한다.",
      "크러시드 아이스를 넣고 다소 길게 젓는다.",
      "얼음을 더 채우고 라임을 올린다.",
    ],
    origin: { year: "1980~90년대 추정", place: "브라질", creator: "미상" },
    story: {
      title: "보드카로 바꾼 국민 칵테일",
      paragraphs: [
        "브라질의 국민 칵테일 카이피리냐에서 기주를 카샤사에서 보드카로 바꾼 변형이다. 카이피로브스카라고도 부른다. 언제 만들어졌는지는 정확히 알 수 없고, 대체로 보드카가 전 세계적으로 인기를 끌던 80~90년대로 본다.",
        "국내에서는 카샤사를 구할 수는 있지만 쓸 곳이 많지 않다. 보드카가 훨씬 구하기 쉬워 원형보다 이쪽을 권하게 된다. 딸기나 블루베리를 같이 넣는 변형도 많다.",
      ],
    },
    profile: [3, 5, 0, 3, 4],
  },
  {
    id: "robroy",
    ko: "롭 로이",
    en: "Rob Roy",
    base: "whisky",
    abv: 29,
    sweet: "semi_dry",
    flavors: ["herbal", "spicy"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "칵테일",
    method: "stir",
    summary:
      "바닐라 느낌이 적고 맨해튼보다 섬세하다. 생각보다 달큰하고 복잡하며, 쓰는 스카치에 따라 편차가 크다.",
    tastingNote: "바닐라 느낌이 적고 맨해튼보다 섬세하다. 생각보다 달큰하고 복잡하며, 쓰는 스카치에 따라 편차가 크다.",
    ingredients: [
      {
        ko: "스카치 위스키",
        en: "Scotch Whisky",
        ml: 45,
        sub: "블렌디드로 만들어도 무방하나, 캐릭터가 확실한 싱글 몰트를 권합니다.",
      },
      { ko: "스위트 베르무트", en: "Sweet Vermouth", ml: 15 },
      { ko: "앙고스투라 아로마틱 비터", en: "Angostura Aromatic Bitters", amount: "1~2 dash" },
      { ko: "오렌지 필", en: "Orange Peel", amount: "1조각" },
    ],
    steps: [
      "가니시 외의 재료를 믹싱 글라스에 넣는다.",
      "얼음을 채우고 스터한다.",
      "칵테일 잔에 따른다.",
      "오렌지 껍질을 짜 오일을 뿌린다. 마라스키노 체리를 하나 넣어도 좋다.",
    ],
    origin: { year: "1894년", place: "뉴욕, 미국", creator: "월도프-아스토리아 호텔 (구전)" },
    story: {
      title: "맨해튼의 스코틀랜드 형제",
      paragraphs: [
        "월도프-아스토리아 호텔의 한 바텐더가 1894년에 만들었다는 이야기가 가장 널리 알려져 있다. 이름은 오페레타 《롭 로이》의 초연을 기념해 붙였고, 주인공 로버트 로이 맥그리거의 약칭이기도 하다. 스코틀랜드의 민속 영웅이니 스카치가 들어가는 잔에는 그럴듯한 작명이다.",
        "다만 10년 앞선 1884년에 이미 스카치와 베르무트를 쓴 다른 이름의 칵테일이 있었고, 기주만 다른 맨해튼은 그보다도 먼저 있었다. 당시에는 스위트 베르무트가 들어간 칵테일이 크게 유행했다. 정말 이 호텔에서 '탄생'했는지에는 의문이 남는다.",
      ],
    },
    profile: [3, 1, 2, 4, 4],
  },
  {
    id: "bobbyburns",
    ko: "바비 번스",
    en: "Bobby Burns",
    base: "whisky",
    abv: 28,
    sweet: "semi_dry",
    flavors: ["herbal", "spicy"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "칵테일",
    method: "stir",
    summary:
      "아주 복합적이다. 스카치의 바닐라와 숙성감, 베르무트의 와인스러움과 향신료, 베네딕틴의 꿀과 허브까지 겹겹이 쌓인다. 나이트캡에 어울린다.",
    tastingNote: "아주 복합적이다. 스카치의 바닐라와 숙성감, 베르무트의 와인스러움과 향신료, 베네딕틴의 꿀과 허브까지 겹겹이 쌓인다. 나이트캡에 어울린다.",
    ingredients: [
      {
        ko: "스카치 위스키",
        en: "Scotch Whisky",
        ml: 45,
        sub: "피트가 없고 숙성감이 과하지 않은 것을 권합니다. 피트가 들어가면 균형이 무너집니다.",
      },
      { ko: "스위트 베르무트", en: "Sweet Vermouth", ml: 15 },
      { ko: "베네딕틴 돔", en: "Bénédictine DOM", ml: 6.5 },
      { ko: "레몬 필", en: "Lemon Peel", amount: "1조각" },
    ],
    steps: [
      "믹싱 글라스에 모든 재료를 넣는다.",
      "얼음을 채우고 스터한다.",
      "차게 해 둔 잔에 붓는다.",
      "레몬 껍질을 짜 오일을 뿌린다.",
    ],
    origin: {
      year: "1930년",
      place: "런던, 영국",
      creator: "해리 크래독 《The Savoy Cocktail Book》",
    },
    story: {
      title: "시인인가, 시가 판매원인가",
      paragraphs: [
        "스코틀랜드의 국민 시인 로버트 번스를 기린 잔으로 알려져 있고 그의 생일을 기념하는 번스 나이트에서 자주 언급된다. 다만 이름이 같다는 것 말고는 근거가 빈약하다. 알버트 크로켓은 《Old Waldorf-Astoria Bar Days》(1931)에서 옛 월도프 호텔 바의 단골이던 시가 판매원의 이름일 가능성도 함께 적었다.",
        "가장 오래된 기록은 1899년까지 올라가지만 진저 코디얼이 들어가는 전혀 다른 배합이다. 1900년대 초에 베이비 번스라는 이름으로 지금과 비슷한 것이 기록됐고, 현재의 형태는 1930년 사보이 칵테일 북에서 굳어졌다. 재료와 시기를 보면 롭 로이의 변형으로 읽는 편이 자연스럽다.",
      ],
    },
    profile: [3, 1, 2, 5, 4],
  },
  {
    id: "oldpal",
    ko: "올드 팔",
    en: "Old Pal",
    base: "whisky",
    abv: 24,
    sweet: "semi_dry",
    flavors: ["bitter", "herbal"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "칵테일",
    method: "stir",
    summary:
      "네그로니 특유의 묵직한 단맛 대신 가볍고 화사하다. 쌉쌀함·달달함·허브감이 화사하게 겹치고 라이의 바닐라가 살짝 비친다.",
    tastingNote: "네그로니 특유의 묵직한 단맛 대신 가볍고 화사하다. 쌉쌀함·달달함·허브감이 화사하게 겹치고 라이의 바닐라가 살짝 비친다.",
    ingredients: [
      { ko: "라이 위스키", en: "Rye Whiskey", ml: 20 },
      { ko: "캄파리", en: "Campari", ml: 20 },
      {
        ko: "드라이 베르무트",
        en: "Dry Vermouth",
        ml: 20,
        sub: "스위트 베르무트로 바꾸면 불바디에에 가까워집니다. 무게가 확 붙습니다.",
      },
      { ko: "레몬 필", en: "Lemon Peel", amount: "1조각" },
    ],
    steps: [
      "믹싱 글라스에 모든 재료를 넣는다.",
      "얼음을 채우고 스터한다.",
      "차게 해 둔 잔에 붓는다.",
      "레몬 껍질을 짜 오일을 뿌린다. 라이 위스키와 레몬 껍질은 의외로 궁합이 좋다.",
    ],
    origin: {
      year: "1927년",
      place: "파리, 프랑스",
      creator: "해리 맥켈혼 《Barflies and Cocktails》",
    },
    story: {
      title: "누구의 오랜 친구인가",
      paragraphs: [
        "파리 해리스 뉴욕 바의 주인 해리 맥켈혼이 낸 《Barflies and Cocktails》 마지막에 실린 아서 모스의 에세이 〈Cocktails Round Town〉에서 처음 등장한다. 개정판에 따르면 만든 사람은 윌리엄 로빈슨이고, 오랜 친구인 '저자'를 위해 '오랜 친구'라는 뜻의 이름을 붙였다.",
        "그 '저자'가 맥켈혼인지 모스인지는 논란이 있는데, 이 대목이 메인 칵테일 목록이 아니라 모스의 에세이에 있다는 점에서 최근에는 모스로 보는 쪽이 힘을 얻는다. 에세이의 배합은 '이탈리안 베르무트'라고만 적혀 있어 지금의 드라이 베르무트 배합과는 거리가 있다. 원전에서 꽤 많이 변한 잔이다.",
      ],
    },
    profile: [2, 1, 4, 4, 4],
  },
  {
    id: "bananaboulevardier",
    ko: "바나나 불바디에",
    en: "Banana Boulevardier",
    base: "whisky",
    abv: 23,
    sweet: "sweet",
    flavors: ["fruity", "bitter", "nutty"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "올드 패션드",
    method: "stir",
    summary:
      "쨍하고 확실한 바나나 맛에 캄파리의 쌉쌀함과 버무스의 허브감이 붙고, 버번의 견과류가 중심을 잡는다. 단맛이 꽤 강한 편이다.",
    tastingNote: "쨍하고 확실한 바나나 맛에 캄파리의 쌉쌀함과 버무스의 허브감이 붙고, 버번의 견과류가 중심을 잡는다. 단맛이 꽤 강한 편이다.",
    ingredients: [
      { ko: "버번 위스키", en: "Bourbon Whiskey", ml: 30 },
      { ko: "캄파리", en: "Campari", ml: 15 },
      { ko: "바나나 리큐르", en: "Banana Liqueur", ml: 15 },
      { ko: "스위트 베르무트", en: "Sweet Vermouth", ml: 30 },
      { ko: "오렌지 필", en: "Orange Peel", amount: "1조각" },
    ],
    steps: [
      "가니시 외의 재료를 락 잔에 넣는다.",
      "얼음을 채우고 스터한다.",
      "오렌지 껍질을 짜 오일을 뿌린다.",
      "사용한 껍질과 말린 바나나를 올린다.",
    ],
    origin: { year: "2015년 8월", place: "휴스턴, 미국", creator: "테리 윌리엄스 (Anvil Bar & Refuge)" },
    story: {
      title: "남은 리큐르를 비우려다",
      paragraphs: [
        "계기가 단순하다. 앤빌 바 & 레퓨지의 총괄 매니저 테리 윌리엄스는 메뉴 개발 후 소량 남은 바나나 리큐르를 비우고 싶었고, 캄파리와 동량으로 샷 잔에 섞어 마셔 봤다가 엄청나게 맛있다는 걸 알았다. 그 조합이 이 잔이 됐다.",
        "바나나 리큐르와 캄파리의 비율을 조금 낮춰 잡는 편이 낫다. 그대로 만들면 단맛이 앞선다. 기주를 버번이 아니라 오버프루프 자메이칸 럼으로 바꾸면 훨씬 펑키한 네그로니 변형이 된다.",
      ],
    },
    profile: [4, 1, 3, 4, 4],
  },
  {
    id: "rumsoda",
    ko: "럼앤소다",
    en: "Rum & Soda",
    base: "rum",
    abv: 10,
    sweet: "dry",
    flavors: ["fruity"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "build",
    summary:
      "열대과일과 바닐라, 후숙된 바나나가 은은하게 느껴지는 부담 없는 한 잔. 결국 럼 하이볼이라 어떤 럼을 쓰느냐가 맛의 전부를 정한다.",
    tastingNote: "열대과일과 바닐라, 후숙된 바나나가 은은하게 느껴지는 부담 없는 한 잔. 결국 럼 하이볼이라 어떤 럼을 쓰느냐가 맛의 전부를 정한다.",
    ingredients: [
      {
        ko: "바베이도스 럼",
        en: "Barbados Rum",
        ml: 40,
        sub: "숙성감이 과하지 않은 골드 럼이면 무엇이든 됩니다. 자메이카 럼을 쓰면 펑키함이 확 올라옵니다.",
      },
      { ko: "탄산수", en: "Soda Water", ml: 120 },
    ],
    steps: [
      "하이볼 글라스에 럼과 얼음을 채우고 럼을 식힐 정도로만 스터한다.",
      "탄산수를 채운다.",
      "얼음을 위아래로 몇 번 들었다 놓는다.",
    ],
    origin: { year: "미상", place: "바하마 (《카지노 로얄》 배경)", creator: "미상" },
    story: {
      title: "본드가 처음 주문한 잔",
      paragraphs: [
        "다니엘 크레이그가 새 제임스 본드로 데뷔한 《카지노 로얄》(2006)에서 그가 가장 먼저 주문하는 술은 마티니가 아니라 마운트 게이 럼으로 만든 럼앤소다다. 배경인 바하마는 아열대 기후이고 마운트 게이는 이웃 섬 바베이도스에서 만들어진다. 현지 술을 기후에 맞춰 고른 셈이다.",
        "럼은 오랫동안 선원과 노동자의 술이라는 이미지가 강했다. 이전 세대의 본드가 샴페인과 마티니처럼 상류층의 코드를 공유하는 술을 마셨다는 걸 생각하면, 첫 잔을 럼으로 고른 것은 거칠고 대담한 본드로 바뀌었다는 신호로 읽을 수 있다.",
      ],
    },
    profile: [1, 0, 0, 3, 2],
  },
  {
    id: "kingstonnegroni",
    ko: "킹스톤 네그로니",
    en: "Kingston Negroni",
    base: "rum",
    abv: 28,
    sweet: "semi_dry",
    flavors: ["fruity", "bitter", "spicy"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "올드 패션드",
    method: "stir",
    summary:
      "클래식보다 허브감이 훨씬 덜하고 단순한데, 후숙된 바나나와 흑당·초콜릿·향신료가 통통 튄다. 복합성은 떨어져도 자극적인 매력이 있다.",
    tastingNote: "클래식보다 허브감이 훨씬 덜하고 단순한데, 후숙된 바나나와 흑당·초콜릿·향신료가 통통 튄다. 복합성은 떨어져도 자극적인 매력이 있다.",
    ingredients: [
      {
        ko: "자메이카 럼",
        en: "Jamaican Rum",
        ml: 30,
        sub: "애플턴 — 도수는 아쉽지만 구하기 쉽습니다. 다른 지역 럼을 쓰면 그냥 럼 네그로니가 됩니다.",
      },
      { ko: "캄파리", en: "Campari", ml: 30 },
      {
        ko: "스위트 베르무트",
        en: "Sweet Vermouth",
        ml: 30,
        sub: "코키 디 토리노 · 안티카 포뮬라 — 기주와 캄파리가 둘 다 세니 캐릭터가 강한 쪽이 낫습니다.",
      },
      { ko: "오렌지 필", en: "Orange Peel", amount: "1조각" },
    ],
    steps: [
      "락 글라스에 모든 재료를 넣는다.",
      "얼음을 넣고 스터한다.",
      "오렌지 껍질의 오일을 잔 위에 뿌린다.",
      "쓴 껍질을 다듬어 넣는다.",
    ],
    origin: { year: "2010년경", place: "뉴욕, 미국", creator: "호아킨 시모" },
    story: {
      title: "5분 만에 나온 변주",
      paragraphs: [
        "바텐더 호아킨 시모가 주류 수입업자에게서 스미스 앤 크로스라는 럼을 건네받고 5분 만에 만들어 냈다고 한다. 이 럼은 자메이카 럼 중에서도 오버프루프에 해당해 캐릭터가 아주 강렬하다.",
        "자메이카 럼의 특징은 '펑키함'이다. 과하게 후숙된 바나나, 열대 과일, 따뜻한 계열의 향신료가 삐죽삐죽 튀어나오는 캐릭터다. 클래식과 궁합이 나쁠 것 같지만 오히려 캄파리처럼 센 재료와 잘 맞물린다. 마실 때 도수감이 크게 느껴지지 않으니 주의해야 한다.",
      ],
    },
    profile: [3, 1, 4, 4, 5],
  },
  {
    id: "bostoncooler",
    ko: "보스턴 쿨러",
    en: "Boston Cooler",
    base: "rum",
    abv: 10,
    sweet: "semi_sweet",
    flavors: ["spicy", "citrus"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "shake",
    summary:
      "상큼 달달하고 생강의 알싸한 맛이 매력적이다. 약간의 탄산감이 붙어 모스코 뮬에 럼의 풍미가 더해진 느낌이 난다.",
    tastingNote: "상큼 달달하고 생강의 알싸한 맛이 매력적이다. 약간의 탄산감이 붙어 모스코 뮬에 럼의 풍미가 더해진 느낌이 난다.",
    ingredients: [
      {
        ko: "럼",
        en: "Rum",
        ml: 45,
        sub: "골드 럼이나 자메이카 럼을 쓰면 맛의 폭이 넓어집니다.",
      },
      { ko: "레몬 주스", en: "Lemon Juice", ml: 19 },
      { ko: "설탕 시럽", en: "Simple Syrup", ml: 10 },
      { ko: "진저비어", en: "Ginger Beer", ml: 90, sub: "진저에일 — 알싸한 맛이 크게 줄어듭니다." },
      { ko: "레몬 휠", en: "Lemon Wheel", amount: "1조각" },
    ],
    steps: [
      "진저비어 외의 모든 재료를 셰이커에 넣는다.",
      "얼음을 채우고 셰이크한다.",
      "긴 잔에 얼음을 채우고 셰이커에서 붓는다.",
      "진저비어를 더하고 얼음을 위아래로 두세 번 움직인다.",
      "레몬 휠을 넣는다.",
    ],
    origin: { year: "미상", place: "미국 (추정)", creator: "미상 (쿨러 계열)" },
    story: {
      title: "이름만 서양에 남은 잔",
      paragraphs: [
        "쿨러는 지금은 대체로 '기주 + 시트러스 즙 + 당 + 탄산 믹서'의 형태를 가리킨다. 피즈·콜린스·벅 계열과 구분에 큰 의미가 없다시피 하다. 데이비드 엠버리는 《The Fine Art of Mixing Drinks》(1948)에서 쿨러를 근본적으로 홀시스 넥의 변형이라고 적기도 했다.",
        "흥미로운 건 인지도다. 분명 서양에서 만들어진 것으로 보이는데 지금 서양에서는 거의 알려져 있지 않고 일본과 한국 정도에서만 통한다. 영어로 검색하면 진저에일에 바닐라 아이스크림을 얹은 디트로이트의 음료가 주로 나온다.",
      ],
    },
    profile: [3, 4, 1, 3, 2],
  },
  {
    id: "bnb",
    ko: "비앤비",
    en: "B&B",
    base: "brandy",
    abv: 35,
    sweet: "semi_sweet",
    flavors: ["herbal", "spicy"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "올드 패션드",
    method: "build",
    summary:
      "베네딕틴의 사프란·꿀·허브 캐릭터에 꼬냑의 풍미가 겹친다. 재료가 둘뿐이고 베네딕틴의 맛은 정해져 있으니 어떤 꼬냑을 쓰느냐로 맛이 갈린다.",
    tastingNote: "베네딕틴의 사프란·꿀·허브 캐릭터에 꼬냑의 풍미가 겹친다. 재료가 둘뿐이고 베네딕틴의 맛은 정해져 있으니 어떤 꼬냑을 쓰느냐로 맛이 갈린다.",
    ingredients: [
      { ko: "꼬냑", en: "Cognac", ml: 30 },
      { ko: "베네딕틴 돔", en: "Bénédictine DOM", ml: 30 },
      { ko: "오렌지 필", en: "Orange Peel", amount: "1조각 (선택)" },
    ],
    steps: [
      "잔에 재료를 넣고 충분히 젓는다.",
      "얼음을 채우고 조금만 스터한다. 70~80%의 맛만 내면 된다.",
      "큰 얼음으로 교체해 서브한다. 나머지는 마시는 동안 녹으며 희석된다.",
    ],
    origin: { year: "1910년 이전", place: "미국", creator: "미상 (1930년대 21 Club 설)" },
    story: {
      title: "두 글자짜리 배합",
      paragraphs: [
        "이름은 Benedictine & Brandy의 약자다. 가장 잘 알려진 이야기는 1930년대 뉴욕의 사교클럽 '21 Club'의 바텐더가 만들었다는 것인데, 이미 1910년 레이먼드 설리번의 《The Barkeeper's Manual》에 푸스카페 스타일로 기록돼 있다. 그때는 베네딕틴과 꼬냑이 2:1이었다.",
        "베네딕틴이 19세기에 상품화된 것을 생각하면 1910년 이전에 이미 이 조합이 있었을 가능성도 무리한 추측은 아니다. 인기가 있었던 모양인지 1937년에는 베네딕틴이 직접 베네딕틴 60%와 프랑스 브랜디 40%를 섞은 RTD 제품을 내놓기도 했다.",
      ],
    },
    profile: [3, 0, 2, 5, 5],
  },
  {
    id: "betweenthesheets",
    ko: "비트윈 더 시츠",
    en: "Between the Sheets",
    base: "brandy",
    abv: 24,
    sweet: "semi_sweet",
    flavors: ["citrus", "sour"],
    styles: ["sour"],
    stylePrimary: "sour",
    glass: "쿠페",
    method: "shake",
    summary:
      "원형인 사이드카보다 오렌지와 알코올에서 오는 단맛이 강하게 난다. 다만 복합성 자체는 사이드카보다 꽤 많이 떨어진다.",
    tastingNote: "원형인 사이드카보다 오렌지와 알코올에서 오는 단맛이 강하게 난다. 다만 복합성 자체는 사이드카보다 꽤 많이 떨어진다.",
    ingredients: [
      { ko: "꼬냑", en: "VSOP Cognac", ml: 22.5 },
      { ko: "화이트 럼", en: "White Rum", ml: 22.5, sub: "숙성 럼을 써도 무방합니다. 색과 맛이 달라집니다." },
      { ko: "레몬 주스", en: "Lemon Juice", ml: 20 },
      { ko: "코앵트로", en: "Cointreau", ml: 15, sub: "오렌지 큐라소로 대체할 수 있습니다." },
    ],
    steps: [
      "모든 재료를 셰이커에 넣는다.",
      "얼음을 채우고 셰이크한다.",
      "차게 해 둔 잔에 붓는다.",
    ],
    origin: { year: "1920~30년대", place: "파리, 프랑스", creator: "해리 맥켈혼 (구전)" },
    story: {
      title: "사이드카의 못난 형제",
      paragraphs: [
        "파리 해리스 뉴욕 바의 해리 맥켈혼이 만들었다는 설이 가장 널리 알려져 있다. 1921년 런던 버클리 호텔의 매니저 폴리가 만들었다는 설도 있는데, 맥켈혼의 저서에 실린 칵테일 중 그가 만들지 않은 것이 그의 것으로 잘못 알려진 경우가 여럿이라 확실하지 않다.",
        "구조상 사이드카에서 꼬냑을 줄이고 그만큼 럼을 채운 것이다. 이름은 '침대 안에서'라는 뜻이고 나이트캡으로 마시는 잔이라고 하는데, 이런 신맛의 칵테일을 나이트캡으로 잘 마시지 않는 걸 보면 이름 때문에 붙은 설명 같다. 원전 배합은 알코올감이 너무 강해 아카이브는 레몬을 늘린 배합을 기준으로 둔다.",
      ],
    },
    profile: [3, 4, 1, 3, 4],
  },
  {
    id: "calvadostonic",
    ko: "칼바도스 토닉",
    en: "Calvados Tonic",
    base: "brandy",
    abv: 9,
    sweet: "semi_sweet",
    flavors: ["fruity", "bitter"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "build",
    summary:
      "잔을 입으로 가져가면 향긋한 레몬과 달큰한 사과 향이 먼저 온다. 사과의 단맛과 토닉의 쌉쌀함이 청량하게 균형을 잡는다.",
    tastingNote: "잔을 입으로 가져가면 향긋한 레몬과 달큰한 사과 향이 먼저 온다. 사과의 단맛과 토닉의 쌉쌀함이 청량하게 균형을 잡는다.",
    ingredients: [
      { ko: "칼바도스", en: "Calvados", ml: 40 },
      { ko: "토닉워터", en: "Tonic Water", ml: 120, sub: "토닉 100ml + 탄산수 20ml — 탄산감이 살아납니다." },
      { ko: "앙고스투라 비터", en: "Angostura Bitters", amount: "1 dash (선택)" },
      { ko: "레몬 필", en: "Lemon Peel", amount: "1조각" },
    ],
    steps: [
      "긴 잔에 얼음을 채운다.",
      "칼바도스와 비터를 붓고 살짝 스터한다.",
      "토닉워터를 붓는다.",
      "바 스푼으로 얼음을 살짝 들었다 놓는다.",
      "레몬 껍질의 오일을 짜 넣고 껍질도 넣는다.",
    ],
    origin: { year: "미상", place: "노르망디, 프랑스 (칼바도스 산지)", creator: "미상" },
    story: {
      title: "지역 이름이 곧 술 이름",
      paragraphs: [
        "칼바도스는 사과(또는 배)를 발효한 시드르를 증류해 숙성한 브랜디다. 프랑스 노르망디의 칼바도스 지역에서 수확한 것으로 만들어야 하는데, 지역명이 곧 술 이름이 되는 것은 꼬냑과 같은 이치다. 꼬냑처럼 AOC로 보호받으며 세 등급으로 나뉜다.",
        "구조는 진토닉과 같지만 칼바도스가 달달한 사과 캐릭터를 가지고 있어 훨씬 달고 가볍다. 앙고스투라를 한 방울 넣으면 맛이 풍부해지고, 토닉을 줄이고 탄산수를 더하면 탄산감이 산다.",
      ],
    },
    profile: [3, 1, 2, 4, 2],
  },
  {
    id: "bamboo",
    ko: "뱀부",
    en: "Bamboo",
    base: "wine",
    abv: 13,
    sweet: "dry",
    flavors: ["nutty", "herbal"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "쿠페",
    method: "stir",
    summary:
      "드라이하고 깔끔하며 약간 떫고 독특한 짠맛이 있다. 피노 셰리의 견과류와 베르무트의 허브·향신료가 조용히 겹친다.",
    tastingNote: "드라이하고 깔끔하며 약간 떫고 독특한 짠맛이 있다. 피노 셰리의 견과류와 베르무트의 허브·향신료가 조용히 겹친다.",
    ingredients: [
      {
        ko: "피노 셰리",
        en: "Fino Sherry",
        ml: 45,
        sub: "다른 셰리를 써도 되지만, 드라이하고 깔끔한 맛을 원하면 피노를 권합니다.",
      },
      { ko: "드라이 베르무트", en: "Dry Vermouth", ml: 15 },
      { ko: "오렌지 비터", en: "Orange Bitters", amount: "1 dash (8~10 drops)" },
    ],
    steps: [
      "모든 재료를 믹싱 글라스에 넣는다.",
      "얼음을 채운다.",
      "스터한 뒤 잔에 따른다.",
    ],
    origin: { year: "1880년대 중반", place: "요코하마, 일본 (통설)", creator: "루이스 에핑어 (통설)" },
    story: {
      title: "누가 먼저였는지 알 수 없는 잔",
      paragraphs: [
        "1890년대 요코하마 그랜드 호텔에서 독일계 미국인 바텐더 루이스 에핑어가 만들었다는 것이 통설이고, 출처는 윌리엄 부스비의 《The World's Drinks and How to Mix Them》(1908)으로 보인다. 그런데 1886년 9월 11일자 《Western Kansas World》는 뱀부가 이미 영국인들에 의해 소개되어 뉴욕에서 인기를 끌고 있다고 적었다.",
        "주목할 점은 '만들어졌다'가 아니라 '소개되어 왔다'는 표현이다. 에핑어는 1880년대 초중반 미국 북서부 항구도시에서 술집을 운영했다. 그 영국인들이 거기서 맛보고 다른 도시로 옮겼을 가능성을 상상하게 하지만 더 이상의 기록은 없다. 1890~1910년대에 베르무트만 다른 아도니스가 나온 걸 보면 셰리와 베르무트의 조합 자체가 당시 새로운 것은 아니었다.",
      ],
    },
    profile: [1, 1, 2, 3, 2],
  },
  {
    id: "adonis",
    ko: "아도니스",
    en: "Adonis",
    base: "wine",
    abv: 12,
    sweet: "semi_dry",
    flavors: ["nutty", "herbal", "spicy"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "칵테일",
    method: "stir",
    summary:
      "감칠맛이 압도적이다. 와인스러움에 한약 같은 뉘앙스가 겹치고, 약간의 오렌지와 견과류 뒤로 향신료·허브·차의 캐릭터가 훅 지나간다.",
    tastingNote: "감칠맛이 압도적이다. 와인스러움에 한약 같은 뉘앙스가 겹치고, 약간의 오렌지와 견과류 뒤로 향신료·허브·차의 캐릭터가 훅 지나간다.",
    ingredients: [
      { ko: "피노 셰리", en: "Fino Sherry", ml: 45 },
      { ko: "스위트 베르무트", en: "Sweet Vermouth", ml: 22.5 },
      { ko: "오렌지 비터", en: "Orange Bitters", amount: "1~2 dash" },
    ],
    steps: [
      "모든 재료를 믹싱 글라스에 넣는다.",
      "얼음을 채우고 스터한다.",
      "칵테일 잔에 따른다.",
    ],
    origin: { year: "1913년 이전", place: "뉴욕, 미국", creator: "월도프-아스토리아 호텔 (추정)" },
    story: {
      title: "뮤지컬에서 온 이름",
      paragraphs: [
        "1884년 초연 후 브로드웨이에서 500회 넘게 공연된 동명의 뮤지컬에서 이름을 따왔다는 것이 공통된 설명이다. 1887년 뉴욕의 한 신문 칼럼에 '아도니스'라는 이름의 칵테일 일화가 처음 나오지만 그것이 지금의 아도니스인지는 알 수 없다. 지금의 형태는 자크 스트라우브의 《Straub's Manual of Mixed Drinks》(1913)에서 확인된다.",
        "월도프-아스토리아 호텔 설이 그럴듯한 이유는 여럿이다. 당시 셰리와 베르무트는 상류층이 즐기던 값비싼 술이라 고급 호텔에서나 취급했고, 이 호텔은 사교의 중심지였으며, 1931년과 1935년에 호텔의 레시피집이 따로 출판될 정도였다. 뱀부에서 드라이 베르무트를 스위트로 바꾼 잔이라 캐릭터를 어느 정도 공유한다.",
      ],
    },
    profile: [2, 1, 2, 4, 2],
  },
  {
    id: "pompier",
    ko: "폼피에",
    en: "Pompier",
    base: "wine",
    abv: 7,
    sweet: "semi_sweet",
    flavors: ["fruity", "herbal"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "build",
    summary:
      "덜 달지만 허브감과 와인스러움이 붙은, 약간은 복잡한 카시스 소다 같다. 복합미가 있는 잔은 아니고 맛 자체는 단순한 편이다.",
    tastingNote: "덜 달지만 허브감과 와인스러움이 붙은, 약간은 복잡한 카시스 소다 같다. 복합미가 있는 잔은 아니고 맛 자체는 단순한 편이다.",
    ingredients: [
      {
        ko: "드라이 베르무트",
        en: "Dry Vermouth",
        ml: 45,
        sub: "프랑스 베르무트를 권합니다. 이탈리안은 깔끔한 편이라 달고 밋밋해질 수 있습니다.",
      },
      { ko: "크렘 드 카시스", en: "Crème de Cassis", ml: 17.5 },
      { ko: "탄산수", en: "Soda Water", ml: 110 },
    ],
    steps: [
      "긴 잔에 얼음을 채운다.",
      "카시스와 베르무트를 넣고 잘 섞이게 스터한다.",
      "탄산수를 넣고 얼음을 살짝 위아래로 움직인다.",
    ],
    origin: { year: "미상", place: "미상", creator: "미상" },
    story: {
      title: "베르무트가 기주인 잔",
      paragraphs: [
        "폼피에는 프랑스어로 '소방관'을 뜻한다. 어떻게 생겨났는지, 왜 이런 이름이 붙었는지는 전혀 알려진 바가 없다. 국내에는 인지도가 없다시피 하고 일본과 서양에서 조금 알려져 있는 정도다.",
        "기주가 드라이 베르무트라는 점이 특이하다. 베르무트를 사 두고 쓸 곳이 없어 애를 먹는 경우에 특히 쓸모가 있다. 크렘 드 카시스는 제품마다 품질 차이가 극명하니 좋은 것을 고르는 편이 낫다.",
      ],
    },
    profile: [3, 1, 1, 3, 1],
  },
  {
    id: "beeramericano",
    ko: "비어 아메리카노",
    en: "Beer Americano",
    base: "liqueur",
    abv: 11,
    sweet: "semi_sweet",
    flavors: ["bitter", "creamy"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "build",
    summary:
      "맥주가 들어간 것치고 쓴맛이 안 나고 오히려 들큰하면서 산뜻하다. 탄산 대신 폭신폭신한 질감이 캄파리의 쓴맛을 감싼다.",
    tastingNote: "맥주가 들어간 것치고 쓴맛이 안 나고 오히려 들큰하면서 산뜻하다. 탄산 대신 폭신폭신한 질감이 캄파리의 쓴맛을 감싼다.",
    ingredients: [
      { ko: "캄파리", en: "Campari", ml: 30 },
      { ko: "스위트 베르무트", en: "Sweet Vermouth", ml: 30 },
      {
        ko: "맥주",
        en: "Lager Beer",
        amount: "적당량 (휘핑해 거품으로)",
        sub: "가볍고 깔끔한 라거를 권합니다. 캐릭터가 센 맥주는 캄파리와 부딪힙니다.",
      },
      { ko: "오렌지 필", en: "Orange Peel", amount: "1조각" },
    ],
    steps: [
      "얼음을 채운 하이볼 잔에 맥주 외의 재료를 넣고 스터한다.",
      "맥주를 휘핑해 거품을 만든다.",
      "거품을 잔에 따른다.",
      "바 스푼으로 잘 섞는다.",
      "오렌지 껍질로 가니시한다.",
    ],
    origin: { year: "2010년대 중반", place: "밀라노, 이탈리아", creator: "토마소 세카 (Cafe Trussardi)" },
    story: {
      title: "탄산 대신 거품",
      paragraphs: [
        "클래식 칵테일 아메리카노에서 탄산수를 맥주로 바꾼 잔이다. 캄파리와 베르무트에 맥주를 섞거나 맥주에 캄파리를 조금 넣어 마시는 방식은 이전부터 있었다. 이 잔의 특별함은 다른 데 있다.",
        "토마소 세카는 맥주를 휘핑해 실질적으로 거품을 넣었다. 맥주 하면 청량한 탄산을 떠올리게 되지만 여기에는 탄산이 없고 폭신한 질감이 대신 들어간다. 클래식 아메리카노가 쓴맛을 가볍고 청량하게 즐기는 잔이라면, 이쪽은 무게감을 잃지 않으면서 부드럽게 가는 잔이다.",
      ],
    },
    profile: [3, 1, 3, 3, 2],
  },
  {
    id: "sloeginfizz",
    ko: "슬로 진 피즈",
    en: "Sloe Gin Fizz",
    base: "liqueur",
    abv: 7,
    sweet: "semi_sweet",
    flavors: ["fruity", "sour"],
    styles: ["sour", "highball"],
    stylePrimary: "sour",
    glass: "하이볼",
    method: "shake",
    summary:
      "베리류와 핵과류의 달큰한 과실감에 레몬의 상큼함이 붙어 새콤달콤함이 주를 이룬다. 슬로 진이 26%라 알코올감이 거의 느껴지지 않는다.",
    tastingNote: "베리류와 핵과류의 달큰한 과실감에 레몬의 상큼함이 붙어 새콤달콤함이 주를 이룬다. 슬로 진이 26%라 알코올감이 거의 느껴지지 않는다.",
    ingredients: [
      { ko: "슬로 진", en: "Sloe Gin", ml: 45 },
      { ko: "레몬 주스", en: "Lemon Juice", ml: 20 },
      { ko: "설탕 시럽", en: "Simple Syrup", ml: 10 },
      { ko: "탄산수", en: "Soda Water", ml: 90 },
    ],
    steps: [
      "탄산수 외의 재료를 셰이커에 넣는다.",
      "얼음을 넣고 셰이크한다.",
      "얼음을 채운 하이볼 잔에 붓는다.",
      "탄산수를 2~3번에 나누어 조심히 붓는다. 한 번에 부으면 거품이 확 올라온다.",
      "바 스푼으로 얼음을 위아래로 들어 섞는다.",
    ],
    origin: { year: "19세기 말 추정", place: "영국", creator: "미상" },
    story: {
      title: "가정집에서 바로",
      paragraphs: [
        "슬로 진은 오래전부터 영국 가정에서 만들어지던 리큐르다. 토지 경계를 나누는 울타리에 흔한 블랙손 가지의 열매 슬로베리는 그대로 먹기에는 떫고 시어서 진에 설탕과 함께 담가 침출했다. 병에 담아 두면 되니 가정에서 만들기 쉬웠다.",
        "이 술은 19세기 말~20세기 초 영국에서 상업적으로 생산되기 시작했고, 같은 시기에 이미 여러 저서가 다양한 기주의 피즈를 소개하고 있었다. 두 흐름을 겹쳐 보면 이 잔은 19세기 말쯤 생겼을 것으로 짐작된다. 가정에서 만들던 술이 상업화를 거쳐 바의 문화로 편입된 독특한 이력이다.",
      ],
    },
    profile: [4, 4, 0, 3, 1],
  },
  {
    id: "shoyojurin",
    ko: "조엽수림",
    en: "Shoyojurin",
    base: "liqueur",
    abv: 5,
    sweet: "semi_sweet",
    flavors: ["bitter", "herbal"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "build",
    summary:
      "쌉쌀하고 달큰한 차의 맛이 이어지다 끝에 살짝 쌉쌀하고 텁텁한 여운이 남는다. 도수가 낮아 식후나 마지막 한 잔에 어울린다.",
    tastingNote: "쌉쌀하고 달큰한 차의 맛이 이어지다 끝에 살짝 쌉쌀하고 텁텁한 여운이 남는다. 도수가 낮아 식후나 마지막 한 잔에 어울린다.",
    ingredients: [
      { ko: "녹차 리큐르", en: "Green Tea Liqueur", ml: 45 },
      {
        ko: "우롱차",
        en: "Oolong Tea",
        ml: 127,
        sub: "맛이 너무 연한 것은 피합니다. 변화를 줄 재료가 적어 우롱차가 맛의 절반입니다.",
      },
    ],
    steps: [
      "차게 식혀 둔 긴 잔에 얼음을 채운다.",
      "녹차 리큐르를 넣고 살짝 스터한다.",
      "우롱차를 넣고 얼음을 몇 번 올렸다 내린다.",
    ],
    origin: { year: "미상", place: "일본", creator: "후쿠니시 에이조 (구전)" },
    story: {
      title: "차의 길을 따라 걷는 이름",
      paragraphs: [
        "기원이 잘 알려지지 않은 잔이다. 만화 《바텐더》에 따르면 산토리 스쿨에서 강사를 했던 바텐더 후쿠니시 에이조가 만들었다고 한다. 녹차 리큐르를 단독 기주로 쓰는데, 산토리가 만드는 헤르메스 그린 티 리큐르가 원조로 보인다. 1950년대에 처음 발매됐을 만큼 오래된 리큐르다.",
        "조엽수림은 습기 많은 곳에 분포하는 상록 활엽수 중심의 삼림 군계를 가리킨다. 만화는 그 분포가 히말라야 중턱에서 동남아시아·중국·한반도를 거쳐 일본에 이르는 차의 길과 겹친다고 표현한다. 색과 이야기에 딱 맞는 이름이라는 뜻이다.",
      ],
    },
    profile: [3, 0, 3, 3, 1],
  },
  {
    id: "chinablue",
    ko: "차이나 블루",
    en: "China Blue",
    base: "liqueur",
    abv: 5,
    sweet: "sweet",
    flavors: ["fruity", "citrus"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "build",
    summary:
      "리치의 달달하고 화려한 맛에 자몽의 신맛, 블루 큐라소의 달달한 시트러스가 겹친다. 간단하지만 복합적이고 비주얼이 화려하다.",
    tastingNote: "리치의 달달하고 화려한 맛에 자몽의 신맛, 블루 큐라소의 달달한 시트러스가 겹친다. 간단하지만 복합적이고 비주얼이 화려하다.",
    ingredients: [
      { ko: "리치 리큐르", en: "Lychee Liqueur", ml: 30, sub: "콰이페 또는 디타." },
      { ko: "자몽 주스", en: "Grapefruit Juice", ml: 45 },
      { ko: "토닉워터", en: "Tonic Water", ml: 67 },
      { ko: "블루 큐라소", en: "Blue Curaçao", ml: 10 },
    ],
    steps: [
      "긴 잔에 얼음을 채운다.",
      "리치 리큐르와 자몽즙을 넣고 잠시 스터한다.",
      "토닉워터를 얼음에 닿지 않게 넣는다.",
      "바 스푼으로 얼음을 위아래로 들어 준다.",
      "블루 큐라소를 붓는다. 마실 때 다시 저어 섞는다.",
    ],
    origin: { year: "미상", place: "도야마현, 일본", creator: "우치다 테루히로 (Bar Hakubakan)" },
    story: {
      title: "중국이 아니라 도자기",
      paragraphs: [
        "이름의 '차이나'는 중국이 아니라 도자기(china)를 뜻한다. 풀어 보면 '청색의 아름다운 도자기'를 본떠 만든 잔이다. 이름 때문에 중국에서 만들어졌을 것 같지만 실제로는 일본에서 만들어졌다. 도야마현의 노포 바 하쿠바칸에서 우치다 테루히로 바텐더가 만들었다고 하며, 이 바는 3대에 걸쳐 지금도 운영되고 있다.",
        "원래는 토닉이 들어가지 않고 자몽즙이 더 많이 들어가는 쇼트 스타일이었지만 지금은 롱 스타일이 더 대중적이다. 토닉 외의 재료를 셰이킹하는 바텐더도 있고 그편이 일체감은 낫다. 다만 연한 핑크색 위로 파란 큐라소를 부어 색이 변하는 연출은 이 방식에서만 나온다.",
      ],
    },
    profile: [4, 3, 1, 3, 1],
  },
  {
    id: "spumoni",
    ko: "스푸모니",
    en: "Spumoni",
    base: "liqueur",
    abv: 5,
    sweet: "semi_sweet",
    flavors: ["bitter", "citrus"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "build",
    summary:
      "캄파리의 쌉쌀하고 시트러시한 맛이 자몽과 정말 잘 어울린다. 과하게 달지 않고 약간 쌉쌀하며 도수가 낮아 부담이 없다.",
    tastingNote: "캄파리의 쌉쌀하고 시트러시한 맛이 자몽과 정말 잘 어울린다. 과하게 달지 않고 약간 쌉쌀하며 도수가 낮아 부담이 없다.",
    ingredients: [
      { ko: "캄파리", en: "Campari", ml: 30 },
      { ko: "자몽 주스", en: "Grapefruit Juice", ml: 45 },
      { ko: "토닉워터", en: "Tonic Water", ml: 67 },
      { ko: "자몽 조각", en: "Grapefruit Slice", amount: "1조각 (선택)" },
    ],
    steps: [
      "긴 잔에 얼음을 채운다.",
      "캄파리와 자몽즙을 넣고 잠시 스터한다.",
      "얼음에 닿지 않게 토닉워터를 따른다.",
      "바 스푼으로 얼음을 위아래로 들어 준다.",
      "자몽 조각으로 장식한다.",
    ],
    origin: { year: "미상", place: "미상", creator: "미상" },
    story: {
      title: "이탈리아인가 일본인가",
      paragraphs: [
        "어디에서 누가 만들었는지 단서가 없다시피 한 잔이다. 한국과 일본에서는 '거품이 나다'라는 뜻의 이탈리아어 spumare에서 유래한 이탈리아 칵테일로 알려져 있다. 그런데 서양에서는 오히려 일본 칵테일로 인식한다. 한 이탈리아 음식·문화 전문가는 이탈리아에서 스푸모니라는 이름의 칵테일을 들어 본 적이 없다고 할 정도다.",
        "이탈리아에서 스푸모니는 전통적인 아이스크림 디저트를 가리키고 이 칵테일과는 상관이 없어 보인다. 결국 이탈리아와의 접점은 캄파리 하나뿐이다. 이름의 어원이 이탈리아어라는 사실이 와전되어 칵테일 자체가 이탈리아에서 유래했다고 받아들여진 것 아닐까 싶다.",
      ],
    },
    profile: [3, 3, 4, 3, 1],
  },
  {
    id: "romewithaview",
    ko: "롬 윗 어 뷰",
    en: "Rome with a View",
    base: "liqueur",
    abv: 7,
    sweet: "semi_sweet",
    flavors: ["bitter", "citrus", "herbal"],
    styles: ["sour", "highball"],
    stylePrimary: "sour",
    glass: "하이볼",
    method: "shake",
    summary:
      "살짝 찌르는 듯한 상큼함에 쌉쌀함과 좋은 허브감이 얹힌다. 캄파리에서 자몽 같은 시트러스 캐릭터가 나오고, 저도수라 아주 편하게 마신다.",
    tastingNote: "살짝 찌르는 듯한 상큼함에 쌉쌀함과 좋은 허브감이 얹힌다. 캄파리에서 자몽 같은 시트러스 캐릭터가 나오고, 저도수라 아주 편하게 마신다.",
    ingredients: [
      { ko: "캄파리", en: "Campari", ml: 30 },
      { ko: "드라이 베르무트", en: "Dry Vermouth", ml: 30 },
      { ko: "라임 주스", en: "Lime Juice", ml: 30 },
      { ko: "설탕 시럽", en: "Simple Syrup", ml: 15 },
      { ko: "탄산수", en: "Soda Water", ml: 60 },
      { ko: "오렌지 휠", en: "Orange Wheel", amount: "1조각" },
    ],
    steps: [
      "탄산수 외의 재료를 셰이커에 넣는다.",
      "얼음을 넣고 셰이크한다.",
      "얼음을 채운 긴 잔에 붓는다.",
      "탄산수를 적당량 채운다.",
      "오렌지 휠을 올린다.",
    ],
    origin: { year: "2008년", place: "뉴욕, 미국", creator: "마이클 매킬로이 (Milk & Honey)" },
    story: {
      title: "쓴맛이 싫다는 손님에게",
      paragraphs: [
        "현대 칵테일계에 큰 영향을 미친 뉴욕의 바 밀크앤허니에서 마이클 매킬로이가 만들었다. 당시 미국에는 쓴맛의 아페리티프나 저도수 술에 대한 유행이 아직 오지 않았고, 캄파리나 네그로니도 큰 인기를 끌지 못하던 시기였다.",
        "매킬로이는 쓴맛 나는 칵테일이 싫다는 손님에게 '그럴 리가요, 이것 한 번 드셔 보세요' 하며 이 잔을 내줬다고 한다. 구조는 피즈 계열과 아메리카노를 매시업한 느낌이다. 캄파리와 라임을 함께 쓰는 칵테일이 많지 않은데, 이 잔만 마셔 봐도 둘의 궁합을 알 수 있다.",
      ],
    },
    profile: [3, 4, 4, 4, 1],
  },
];

export function getCocktail(id: string): Cocktail | undefined {
  return COCKTAILS.find((c) => c.id === id);
}

/* ─────────────────  코퍼스에 실제로 존재하는 값  ─────────────────
   enum은 PRD 기준으로 완전하지만, 필터 UI에 항목 0건짜리 값을 늘어놓으면
   고장난 것처럼 보인다. 화면은 이 목록을 돌고, 그 안에서 조합 때문에 0이 된
   값만 비활성 처리한다 (PRD R-F2.1-2가 말하는 0건은 후자다).                */

export const BASES_IN_CORPUS: BaseSpirit[] = BASES.filter((b) =>
  COCKTAILS.some((c) => c.base === b)
);

export const STYLES_IN_CORPUS: StyleKey[] = STYLE_KEYS.filter((s) =>
  COCKTAILS.some((c) => c.styles.includes(s))
);

export const FLAVORS_IN_CORPUS: FlavorKey[] = FLAVOR_KEYS.filter((f) =>
  COCKTAILS.some((c) => c.flavors.includes(f))
);

/**
 * 배리에이션 추천 — `stylePrimary` 일치가 1순위, 기주 일치가 2순위 (PRD R-C-3).
 * 둘 다 없으면 태그 매칭으로 퇴화하므로 그 상태를 만들지 않는다.
 */
export function relatedCocktails(cocktail: Cocktail, limit = 4): Cocktail[] {
  const rank = (x: Cocktail) =>
    (x.stylePrimary === cocktail.stylePrimary ? 0 : 2) + (x.base === cocktail.base ? 0 : 1);

  return COCKTAILS.filter((x) => x.id !== cocktail.id)
    .map((x) => ({ x, r: rank(x) }))
    .filter(({ r }) => r < 3)
    .sort((a, b) => a.r - b.r || a.x.abv - b.x.abv)
    .slice(0, limit)
    .map(({ x }) => x);
}
