import Link from "next/link";
import { FLAVOR_LABELS, type Cocktail } from "@kca/domain";
import { PhotoSlot } from "./photo-slot";
import { SweetTag } from "./sweet-tag";

export function CocktailCard({ cocktail }: { cocktail: Cocktail }) {
  return (
    <Link href={`/cocktails/${cocktail.id}`} className="cocktail-card">
      <PhotoSlot ratio="4x5" caption={cocktail.en} label="IMAGE 4:5" />
      <div className="cocktail-card__body">
        <div className="cocktail-card__title">
          <div>
            <div className="cocktail-card__ko">{cocktail.ko}</div>
            <div className="cocktail-card__en">{cocktail.en}</div>
          </div>
          <div className="cocktail-card__abv">
            <b>{cocktail.abv}</b>
            <span>% ABV</span>
          </div>
        </div>
        <div className="tag-row">
          <span className="tag tag-neutral tag-bordered">{cocktail.base}</span>
          <SweetTag level={cocktail.sweet} />
        </div>
        <div className="cocktail-card__flavors">
          {cocktail.flavors.slice(0, 3).map((f) => (
            <span key={f}>{FLAVOR_LABELS[f]}</span>
          ))}
        </div>
        <div className="cocktail-card__foot">
          <span>
            {cocktail.method} · {cocktail.glass}
          </span>
          <em>상세 →</em>
        </div>
      </div>
    </Link>
  );
}
