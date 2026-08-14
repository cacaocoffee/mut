import Link from "next/link";
import {
  BASE_SPIRIT_LABELS,
  FLAVOR_LABELS,
  TECHNIQUES,
  type SearchItem,
} from "@kca/domain";
import { PhotoSlot } from "./photo-slot";
import { SweetTag } from "./sweet-tag";

/**
 * 목록 카드.
 *
 * 받는 것이 [SearchItem] 이다 — 목록 API 한 줄이 그대로 들어온다. 상세 레코드를 받게 두면
 * 카드를 그리자고 상세를 받아야 하고, 그 순간 500종 목록이 상세 500벌이 된다.
 *
 * 축은 **슬러그**라 그대로 찍으면 `gin` · `stir` 이 보인다 (`PRIN-T02`). 한국어는 계약의
 * 레이블 표에서 온다.
 */
export function CocktailCard({ cocktail }: { cocktail: SearchItem }) {
  return (
    <Link href={`/cocktails/${cocktail.slug}`} className="cocktail-card">
      <PhotoSlot ratio="4x5" caption={cocktail.nameEn} label="IMAGE 4:5" />
      <div className="cocktail-card__body">
        <div className="cocktail-card__title">
          <div>
            <div className="cocktail-card__ko">{cocktail.nameKo}</div>
            <div className="cocktail-card__en">{cocktail.nameEn}</div>
          </div>
          <div className="cocktail-card__abv">
            <b>{cocktail.abv ?? "—"}</b>
            <span>% ABV</span>
          </div>
        </div>
        <div className="tag-row">
          <span className="tag tag-neutral tag-bordered">
            {BASE_SPIRIT_LABELS[cocktail.base]}
          </span>
          <SweetTag level={cocktail.sweet} />
        </div>
        <div className="cocktail-card__flavors">
          {cocktail.flavors.slice(0, 3).map((f) => (
            <span key={f}>{FLAVOR_LABELS[f]}</span>
          ))}
        </div>
        <div className="cocktail-card__foot">
          <span>
            {TECHNIQUES[cocktail.method].ko} · {cocktail.glass}
          </span>
          <em>상세 →</em>
        </div>
      </div>
    </Link>
  );
}
