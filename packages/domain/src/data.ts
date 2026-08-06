import type { AbvBand, BaseSpirit, Cocktail, FlavorKey, StyleKey, Technique } from "./types";

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

/** 메이킹 방법 — 표시명, 슬러그, 필요 도구. 이 축은 사실상 난이도 프록시다 (PRD 5.3). */
export const TECHNIQUES: Record<Technique, { ko: string; slug: string; tools: string }> = {
  Build: { ko: "잔에서 조립", slug: "build", tools: "잔, 바스푼" },
  Shake: { ko: "흔들어 섞기", slug: "shake", tools: "셰이커" },
  Stir: { ko: "휘저어 섞기", slug: "stir", tools: "믹싱글라스" },
  Blend: { ko: "블렌드", slug: "blend", tools: "블렌더" },
  Etc: { ko: "기타", slug: "etc", tools: "스로잉 · 머들링 · 인퓨징 · 직화" },
};

/** 당도 4단계 — [한글, 영문] */
export const SWEETNESS: ReadonlyArray<readonly [string, string]> = [
  ["드라이", "Dry"],
  ["세미 드라이", "Semi-Dry"],
  ["세미 스위트", "Semi-Sweet"],
  ["스위트", "Sweet"],
];

/** PRD 5.1의 10종 전부. 카테고리 URL의 정본이라 아카이브에 항목이 없어도 유지한다. */
export const BASES: BaseSpirit[] = [
  "진",
  "보드카",
  "위스키",
  "럼",
  "데킬라 · 메즈칼",
  "브랜디",
  "리큐르",
  "와인 · 스파클링",
  "전통주",
  "무알콜",
];

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
    base: "진",
    abv: 24,
    sweet: 0,
    flavors: ["bitter", "herbal", "citrus"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "올드 패션드",
    method: "Stir",
    summary:
      "동량 배합의 교본. 캄파리의 쓴맛과 베르무트의 단맛이 진의 주니퍼 위에서 정확히 상쇄된다.",
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
    base: "진",
    abv: 30,
    sweet: 0,
    flavors: ["herbal", "citrus"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "칵테일",
    method: "Stir",
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
    base: "진",
    abv: 26,
    sweet: 1,
    flavors: ["citrus", "sour"],
    styles: ["sour"],
    stylePrimary: "sour",
    glass: "칵테일",
    method: "Shake",
    summary: "라임 코디얼의 단맛과 진의 골격이 만나는 가장 단순한 사워.",
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
    base: "진",
    abv: 12,
    sweet: 1,
    flavors: ["citrus", "bitter"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "Build",
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
    base: "진",
    abv: 22,
    sweet: 1,
    flavors: ["herbal", "citrus"],
    styles: ["sour"],
    stylePrimary: "sour",
    glass: "칵테일",
    method: "Shake",
    summary: "민트를 넣은 진 사워. 모히토의 진 버전으로 읽어도 된다.",
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
    base: "보드카",
    abv: 12,
    sweet: 2,
    flavors: ["spicy", "citrus"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "구리 머그",
    method: "Build",
    summary: "진저비어의 매운맛이 중심. 보드카는 뼈대만 세운다.",
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
    base: "보드카",
    abv: 20,
    sweet: 2,
    flavors: ["bitter", "nutty", "fruity"],
    styles: ["creamy"],
    stylePrimary: "creamy",
    glass: "칵테일",
    method: "Shake",
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
    base: "보드카",
    abv: 12,
    sweet: 0,
    flavors: ["spicy", "sour"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "Build",
    summary: "짠맛·감칠맛 계열. 단맛이 거의 없는 유일한 브런치 잔.",
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
    base: "보드카",
    abv: 22,
    sweet: 2,
    flavors: ["fruity", "citrus"],
    styles: ["sour"],
    stylePrimary: "sour",
    glass: "칵테일",
    method: "Shake",
    summary: "크랜베리의 색과 트리플 섹의 오렌지 향. 산미가 축이 된다.",
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
    base: "위스키",
    abv: 32,
    sweet: 1,
    flavors: ["spicy", "bitter"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "올드 패션드",
    method: "Stir",
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
    base: "위스키",
    abv: 30,
    sweet: 1,
    flavors: ["bitter", "fruity"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "칵테일",
    method: "Stir",
    summary: "라이의 스파이스와 스위트 베르무트. 네그로니와 마티니 사이.",
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
    base: "위스키",
    abv: 20,
    sweet: 2,
    flavors: ["sour", "citrus", "creamy"],
    styles: ["sour"],
    stylePrimary: "sour",
    glass: "쿠페",
    method: "Shake",
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
    base: "위스키",
    abv: 22,
    sweet: 2,
    flavors: ["smoky", "spicy"],
    styles: ["sour"],
    stylePrimary: "sour",
    glass: "올드 패션드",
    method: "Shake",
    summary: "생강·꿀·레몬에 아일라 위스키의 연기를 얹은 현대 고전.",
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
    base: "위스키",
    abv: 26,
    sweet: 1,
    flavors: ["bitter", "fruity"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "올드 패션드",
    method: "Stir",
    summary: "네그로니의 위스키 버전. 진보다 무게가 있고 단맛이 길다.",
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
    base: "럼",
    abv: 24,
    sweet: 1,
    flavors: ["citrus", "sour"],
    styles: ["sour"],
    stylePrimary: "sour",
    glass: "쿠페",
    method: "Shake",
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
    base: "럼",
    abv: 13,
    sweet: 2,
    flavors: ["herbal", "citrus"],
    styles: ["highball", "sour"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "Build",
    summary: "민트 향, 라임 산미, 탄산의 세 층이 분리되어 있어야 한다.",
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
    base: "럼",
    abv: 26,
    sweet: 2,
    flavors: ["fruity", "citrus", "nutty"],
    styles: ["tiki", "sour"],
    stylePrimary: "tiki",
    glass: "올드 패션드",
    method: "Shake",
    summary: "오르자(아몬드 시럽)가 향의 중심. 과일 주스는 들어가지 않는다.",
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
    base: "럼",
    abv: 14,
    sweet: 2,
    flavors: ["spicy", "fruity"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "Build",
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
    base: "데킬라 · 메즈칼",
    abv: 24,
    sweet: 1,
    flavors: ["citrus", "sour"],
    styles: ["sour"],
    stylePrimary: "sour",
    glass: "쿠페",
    method: "Shake",
    summary: "소금·산·단맛 세 축의 균형. 데킬라는 100% 아가베를 쓴다.",
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
    base: "데킬라 · 메즈칼",
    abv: 14,
    sweet: 2,
    flavors: ["citrus", "fruity"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "Build",
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
    base: "전통주",
    abv: 9,
    sweet: 1,
    flavors: ["citrus"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "Build",
    summary: "증류식 소주의 곡물 향을 탄산으로 늘린 구조. 희석률이 관건.",
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
    base: "전통주",
    abv: 28,
    sweet: 1,
    flavors: ["smoky", "spicy"],
    styles: ["spirit-forward"],
    stylePrimary: "spirit-forward",
    glass: "올드 패션드",
    method: "Stir",
    summary: "문배주의 배·수수 향을 올드 패션드 구조에 넣은 응용 배합.",
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
    base: "전통주",
    abv: 8,
    sweet: 3,
    flavors: ["fruity", "creamy"],
    styles: ["creamy", "tiki"],
    stylePrimary: "creamy",
    glass: "하이볼",
    method: "Shake",
    summary: "막걸리의 유산 향과 코코넛·파인애플. 가장 단 항목.",
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
    base: "무알콜",
    abv: 0,
    sweet: 2,
    flavors: ["citrus", "sour"],
    styles: ["highball"],
    stylePrimary: "highball",
    glass: "하이볼",
    method: "Build",
    summary: "식초 기반 시럽으로 산미의 층을 만든 무알콜 항목.",
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
