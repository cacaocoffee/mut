/**
 * 재료별 브랜드 검색어 (#213 · G-41).
 *
 * ## 정본은 여기다
 *
 * 식약처 신고 제품명에서 이 재료를 찾을 때 쓰는 말. 배치가 제품명(한/영)에 이 말이 들어 있으면
 * 그 제품을 이 재료로 **승인**한다 — "탱커레이" 가 제품명에 있으면 진이지 사람이 판단할 게 없다.
 * 어드민 화면은 이 값을 보여만 준다. 두 곳에서 고치면 어긋난다.
 *
 * `scripts/seed-from-prototype.ts` 가 `R__seed_05_brand_keywords.sql` 로 내려보내고, 키가 DB 재료
 * 마스터(`R__seed_01`)의 슬러그에 없으면 그 스크립트가 멈춘다.
 *
 * ## 넓은 말은 적지 않는다
 *
 * "진"·"럼"·"위스키" 같은 말은 제품명 어디에나 있다 — "진로", "진저" 까지 잡힌다. 그런 건 별칭
 * (`aliases`) 자리이고, 별칭 일치는 제안까지만 간다. 여기는 **상표**만.
 *
 * 시럽·주스·가니시·탄산은 식약처 주류 신고 대상이 아니라 비워 둔다.
 */
export const BRAND_KEYWORDS: Record<string, readonly string[]> = {
  // ── 진 ────────────────────────────────────────────────────────────────
  "dry-gin": [
    "탱커레이", "tanqueray",
    "봄베이", "bombay",
    "고든스", "고든", "gordon",
    "헨드릭스", "hendrick",
    "비피터", "beefeater",
    "몽키 47", "몽키47", "monkey 47",
    "플리머스", "plymouth",
    "로쿠", "roku gin",
    "시트러스 진", "no.3 gin", "넘버쓰리",
    "에비에이션", "aviation gin",
    "봄베이 사파이어",
    "더 보타니스트", "botanist",
    "시프스미스", "sipsmith",
    "말피", "malfy",
    "포드 진", "fords gin",
    "브로커스", "broker's",
    "핸드릭스",
  ],
  "sloe-gin": ["슬로 진", "슬로진", "sloe gin"],

  // ── 보드카 ────────────────────────────────────────────────────────────
  vodka: [
    "앱솔루트", "absolut",
    "스미노프", "smirnoff",
    "그레이구스", "그레이 구스", "grey goose",
    "벨루가", "beluga",
    "케텔원", "케텔 원", "ketel one",
    "티토스", "tito's",
    "시락", "ciroc", "cîroc",
    "스톨리치나야", "stolichnaya", "스톨리",
    "핀란디아", "finlandia",
    "벨베데레", "belvedere",
    "러스키 스탄다드", "russian standard",
    "하쿠 보드카", "haku vodka",
  ],
  "citron-vodka": ["앱솔루트 시트론", "absolut citron", "시트론 보드카", "citron vodka"],

  // ── 럼 ────────────────────────────────────────────────────────────────
  rum: [
    "바카디", "bacardi",
    "하바나 클럽", "하바나클럽", "havana club",
    "캡틴 모건", "captain morgan",
    "마운트 게이", "mount gay",
    "플랜테이션", "plantation rum",
    "디플로마티코", "diplomatico",
    "아프렐턴", "애플턴", "appleton",
    "마이어스", "myers",
    "론 자카파", "자카파", "zacapa",
    "엘 도라도", "el dorado",
    "브루갈", "brugal",
    "고슬링", "gosling",
  ],
  "white-rum": ["바카디 카르타 블랑카", "bacardi superior", "하바나 클럽 3", "havana club 3", "화이트 럼", "white rum"],
  "dark-rum": ["다크 럼", "dark rum", "마이어스", "myers", "고슬링 블랙", "gosling black", "크라켄", "kraken"],
  "jamaican-rum": ["애플턴", "appleton", "자메이카 럼", "jamaica rum", "jamaican rum", "레이 & 네퓨", "wray & nephew", "스미스 앤 크로스", "smith & cross"],
  "barbados-rum": ["마운트 게이", "mount gay", "플랜테이션 바베이도스", "plantation barbados", "폴리 앤 마운트"],
  "rhum-agricole": ["아그리콜", "agricole", "클레망", "clement", "라 파보리트", "trois rivieres", "트루아 리비에르", "네이슨", "neisson"],

  // ── 데킬라 ────────────────────────────────────────────────────────────
  "blanco-tequila": [
    "패트론 실버", "patron silver", "patrón silver",
    "돈 훌리오 블랑코", "don julio blanco",
    "호세 쿠엘보", "jose cuervo",
    "올메카", "olmeca",
    "에스폴론", "espolon", "espolòn",
    "1800 실버", "1800 silver",
    "카사미고스", "casamigos",
    "엘 히마도르", "el jimador",
    "포르탈레자", "fortaleza",
    "블랑코 데킬라", "tequila blanco", "blanco tequila",
  ],

  // ── 위스키 ────────────────────────────────────────────────────────────
  bourbon: [
    "버번", "bourbon",
    "짐빔", "jim beam",
    "메이커스 마크", "maker's mark", "makers mark",
    "와일드 터키", "와일드터키", "wild turkey",
    "우드포드", "woodford",
    "버팔로 트레이스", "buffalo trace",
    "불렛", "bulleit",
    "포로지스", "four roses",
    "이글 레어", "eagle rare",
    "노브 크릭", "knob creek",
    "에반 윌리엄스", "evan williams",
    "엘라이자 크레이그", "elijah craig",
  ],
  "bourbon-whiskey": ["버번", "bourbon", "짐빔", "jim beam", "메이커스 마크", "maker's mark", "와일드 터키", "wild turkey", "우드포드", "woodford", "버팔로 트레이스", "buffalo trace", "불렛", "bulleit"],
  "bourbon-rye": ["버번", "bourbon", "라이 위스키", "rye whiskey", "짐빔", "jim beam", "불렛", "bulleit", "리튼하우스", "rittenhouse", "와일드 터키", "wild turkey"],
  "rye-whiskey": ["라이 위스키", "라이위스키", "rye whiskey", "rye whisky", "불렛 라이", "bulleit rye", "리튼하우스", "rittenhouse", "사제락 라이", "sazerac rye", "템플턴", "templeton", "와일드 터키 라이", "wild turkey rye", "미크터스 라이", "michter"],
  "scotch-whisky": [
    "조니워커", "조니 워커", "johnnie walker",
    "발렌타인", "ballantine",
    "시바스 리갈", "chivas regal", "시바스",
    "듀어스", "dewar",
    "몽키 숄더", "monkey shoulder",
    "페이머스 그라우스", "famous grouse",
    "글렌피딕", "glenfiddich",
    "글렌리벳", "glenlivet",
    "맥캘란", "macallan",
    "글렌모렌지", "glenmorangie",
    "탈리스커", "talisker",
    "스카치", "scotch",
  ],
  "blended-scotch": ["조니워커", "조니 워커", "johnnie walker", "발렌타인", "ballantine", "시바스 리갈", "chivas regal", "듀어스", "dewar", "몽키 숄더", "monkey shoulder", "페이머스 그라우스", "famous grouse", "블렌디드 스카치", "blended scotch"],
  "islay-single-malt": [
    "라프로익", "laphroaig",
    "아드벡", "ardbeg",
    "라가불린", "lagavulin",
    "보모어", "bowmore",
    "브룩라디", "bruichladdich",
    "포트샬롯", "port charlotte",
    "킬호만", "kilchoman",
    "부나하벤", "bunnahabhain",
    "쿨일라", "caol ila",
    "아일라", "islay",
  ],

  // ── 브랜디 · 그 외 증류주 ─────────────────────────────────────────────
  "vsop-cognac": ["꼬냑", "코냑", "cognac", "헤네시", "hennessy", "레미 마틴", "레미마틴", "remy martin", "rémy martin", "마르텔", "martell", "까뮈", "camus", "쿠르부아지에", "courvoisier"],
  calvados: ["칼바도스", "calvados", "페르 마글루아", "pere magloire", "père magloire", "불라르", "boulard", "크리스티앙 드루앙", "drouin"],
  absinthe: ["압생트", "absinthe", "페르노 압생트", "pernod absinthe", "라 페", "la fée", "생 조르주"],
  "distilled-soju": ["화요", "hwayo", "일품진로", "일품 진로", "안동소주", "안동 소주", "증류식 소주", "문배주", "고소리술", "토끼소주", "tokki", "원소주", "won soju"],
  "munbaeju-40": ["문배주", "munbaeju"],

  // ── 리큐르 · 아페리티프 ──────────────────────────────────────────────
  campari: ["캄파리", "깜빠리", "campari"],
  suze: ["수즈", "suze"],
  cointreau: ["쿠앵트로", "코앵트로", "꾸앵트로", "cointreau"],
  "orange-curacao": ["오렌지 큐라소", "orange curacao", "orange curaçao", "드라이 큐라소", "dry curacao", "그랑 마니에르", "grand marnier", "트리플 섹", "triple sec", "볼스 트리플섹", "피에르 페랑", "pierre ferrand"],
  "blue-curacao": ["블루 큐라소", "블루큐라소", "blue curacao", "blue curaçao"],
  "benedictine-dom": ["베네딕틴", "benedictine", "bénédictine", "b&b"],
  "coffee-liqueur": ["깔루아", "칼루아", "kahlua", "kahlúa", "티아 마리아", "tia maria", "미스터 블랙", "mr black", "커피 리큐르", "coffee liqueur", "커피 리큐어"],
  "banana-liqueur": ["바나나 리큐르", "banana liqueur", "크렘 드 바난", "creme de banane", "crème de banane", "볼스 바나나", "bols banana", "지파드 바나나", "giffard banane"],
  "green-tea-liqueur": ["녹차 리큐르", "green tea liqueur", "젠 그린티", "zen green tea", "마츠차 리큐르", "matcha liqueur"],
  "lychee-liqueur": ["리치 리큐르", "리치 리큐어", "lychee liqueur", "디타", "소호 리치", "soho lychee", "볼스 리치", "bols lychee"],
  "creme-de-cassis": ["크렘 드 카시스", "크림 드 카시스", "creme de cassis", "crème de cassis", "카시스", "cassis", "르제 라그루트", "lejay"],
  "sweet-vermouth": ["스위트 베르무트", "sweet vermouth", "베르무트 로소", "vermouth rosso", "마티니 로소", "martini rosso", "친자노 로소", "cinzano rosso", "카르파노 안티카", "carpano antica", "코키 토리노", "cocchi torino", "돌린 루즈", "dolin rouge", "푼트 에 메스", "punt e mes"],
  "dry-vermouth": ["드라이 베르무트", "dry vermouth", "베르무트 드라이", "vermouth dry", "마티니 엑스트라 드라이", "martini extra dry", "노일리 프랏", "noilly prat", "돌린 드라이", "dolin dry", "친자노 엑스트라 드라이", "cinzano extra dry"],
  "lillet-blanc": ["릴레 블랑", "릴레", "lillet"],
  "fino-sherry": ["피노 셰리", "fino sherry", "티오 페페", "tio pepe", "만사니야", "manzanilla", "라 이나", "la ina"],
  "lager-beer": [],
  makgeolli: [],

  // ── 비터스 ────────────────────────────────────────────────────────────
  "angostura-bitters": ["앙고스투라", "angostura"],
  "orange-bitters": ["오렌지 비터", "orange bitters", "리건스", "regans", "피 브라더스 오렌지", "fee brothers orange", "앙고스투라 오렌지", "angostura orange"],

  // ── 신고 대상이 아닌 것 — 비워 둔다 ───────────────────────────────────
  cherry: [],
  "citrus-shrub-syrup": [],
  "coconut-cream": [],
  "cranberry-juice": [],
  "egg-white": [],
  espresso: [],
  "ginger-beer": [],
  "grapefruit-juice": [],
  "grapefruit-slice": [],
  "grapefruit-soda": [],
  "honey-ginger-syrup": [],
  "jocheong-syrup": [],
  "lemon-juice": [],
  "lemon-peel": [],
  "lemon-wheel": [],
  lime: [],
  "lime-juice": [],
  "lime-wedge": [],
  milk: [],
  mint: [],
  "oolong-tea": [],
  "orange-peel": [],
  "orange-wheel": [],
  orgeat: [],
  "pear-peel": [],
  "pineapple-juice": [],
  "rich-simple-syrup": [],
  rosemary: [],
  salt: [],
  "simple-syrup": [],
  "soda-water": [],
  sugar: [],
  "tomato-juice": [],
  "tonic-water": [],
  "worcestershire-tabasco": [],
};
