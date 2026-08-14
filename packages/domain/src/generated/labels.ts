/* eslint-disable */
/**
 * AUTO-GENERATED — DO NOT EDIT
 *
 * 정본은 apps/api/openapi.json 이고, 그 정본은 Kotlin 코드다 (PRIN-T02).
 * 이 파일을 손으로 고치면 CI 가 되돌린다.
 *
 *   cd apps/api && ./gradlew generateOpenApiDocs   계약 갱신
 *   npm run generate:types                          이 파일 갱신
 */

/** 축 1 · 기주 (단일값 필수, R-C-1) */
export const BASE_SPIRIT_LABELS = {
  "gin": "진",
  "vodka": "보드카",
  "whisky": "위스키",
  "rum": "럼",
  "agave": "데킬라 · 메즈칼",
  "brandy": "브랜디",
  "liqueur": "리큐르",
  "wine": "와인 · 스파클링",
  "korean": "전통주",
  "non-alcoholic": "무알콜",
} as const;

/** 축 2 · 스타일 (복수, style_primary 필수) */
export const STYLE_KEY_LABELS = {
  "highball": "하이볼",
  "sour": "사워",
  "spirit-forward": "스피릿 포워드",
  "spritz": "스프리츠",
  "tiki": "티키",
  "creamy": "크리미",
  "hot": "핫",
  "frozen": "프로즌",
  "shot": "샷",
} as const;

/** 향 태그 1~3개 (R-F1.2-1). 카테고리가 아니다 */
export const FLAVOR_KEY_LABELS = {
  "citrus": "시트러스",
  "sour": "산미",
  "fruity": "프루티",
  "floral": "플로럴",
  "herbal": "허브",
  "spicy": "스파이시",
  "smoky": "스모키",
  "bitter": "쓴맛",
  "nutty": "너티",
  "creamy": "크리미",
} as const;

/** 당도 4단계 */
export const SWEET_LEVEL_LABELS = {
  "dry": "드라이",
  "semi_dry": "세미 드라이",
  "semi_sweet": "세미 스위트",
  "sweet": "스위트",
} as const;

/** 축 3 · 메이킹 방법 (단일값 필수) */
export const TECHNIQUE_LABELS = {
  "build": "빌드",
  "shake": "셰이크",
  "stir": "스터",
  "blend": "블렌드",
  "etc": "기타",
} as const;
