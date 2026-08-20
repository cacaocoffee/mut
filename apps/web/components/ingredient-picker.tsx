"use client";

import { useId, useMemo, useState } from "react";
import { CATEGORY_LABELS, STOCKABLE, type Ingredient } from "@mut/domain";

/** 드롭다운 상한. 더 내려가면 목록을 훑는 것이 스크롤과 다를 바 없어진다. */
const MAX_SUGGESTIONS = 8;

/**
 * 재료를 이름으로 찾아 담는다.
 *
 * ## 왜 필요한가
 *
 * 고를 수 있는 재료가 59종이다. 카테고리로 묶어 두었지만 "스위트 베르무트" 하나를
 * 담으려고 술 스물넷을 훑게 된다. 이름을 아는 사람에게는 **치는 편이 빠르다.**
 *
 * ## `SearchBox` 와 따로 있는 이유
 *
 * 조작 문법(화살표 · Esc · Enter · `aria-activedescendant`)과 생김새(`.search-box*`)는
 * [SearchBox](./search-box.tsx) 에서 그대로 가져온다 — 같은 사이트에서 두 자동완성이
 * 다르게 움직이면 안 된다.
 *
 * **다른 것은 고른 뒤다.** 저쪽은 한 번 골라 그 화면으로 떠나고, 여기는 **연달아 담는다** —
 * 고르면 입력만 비우고 목록은 열어 둔다. 진 · 캄파리 · 베르무트를 한 호흡에 담게 하려는
 * 것이고, 그래서 서버도 부르지 않는다 (재료 71종은 이미 화면에 있다).
 *
 * ## 별칭으로도 찾는다
 *
 * `코앵트로` 를 쳐도 `쿠앵트로` 가 나온다. 마스터가 그 흔들림을 이미 들고 있으므로
 * (`ingredients.ts` 의 `aliases`) 여기서 다시 목록을 만들지 않는다.
 */
export function IngredientPicker({
  have,
  onToggle,
}: {
  have: ReadonlySet<string>;
  onToggle: (slug: string) => void;
}) {
  const [value, setValue] = useState("");
  const [active, setActive] = useState(-1);
  const listId = useId();

  const term = value.trim().toLowerCase();

  const matches = useMemo(() => {
    if (!term) return [];
    return STOCKABLE.filter(
      (i) =>
        i.nameKo.toLowerCase().includes(term) ||
        i.nameEn.toLowerCase().includes(term) ||
        (i.aliases ?? []).some((a) => a.toLowerCase().includes(term))
    ).slice(0, MAX_SUGGESTIONS);
  }, [term]);

  const choose = (ing: Ingredient) => {
    onToggle(ing.slug);
    // 담고 나면 비운다 — 다음 재료를 바로 칠 수 있다. 목록은 닫지 않는다.
    setValue("");
    setActive(-1);
  };

  const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      if (matches.length === 0) return;
      e.preventDefault();
      const delta = e.key === "ArrowDown" ? 1 : -1;
      setActive((i) => (i + delta + matches.length) % matches.length);
      return;
    }
    if (e.key === "Escape") {
      setValue("");
      setActive(-1);
      return;
    }
    if (e.key === "Enter") {
      e.preventDefault();
      // 고른 것이 없으면 첫 줄이다 — 치고 바로 Enter 가 가장 흔한 손놀림이다
      const pick = active >= 0 ? matches[active] : matches[0];
      if (pick) choose(pick);
    }
  };

  return (
    <div className="search-box ingredient-picker">
      <label className="filter-label" htmlFor={`${listId}-input`}>
        재료 찾기 FIND
      </label>
      <input
        id={`${listId}-input`}
        className="input search-box__input"
        type="search"
        role="combobox"
        autoComplete="off"
        placeholder="베르무트 · Gin"
        value={value}
        aria-label="담을 재료를 이름으로 찾습니다"
        aria-expanded={matches.length > 0}
        aria-controls={listId}
        aria-activedescendant={active >= 0 ? `${listId}-${active}` : undefined}
        onChange={(e) => {
          setValue(e.target.value);
          setActive(-1);
        }}
        onKeyDown={onKeyDown}
      />

      {term.length > 0 && (
        <ul className="search-box__list" id={listId} role="listbox" aria-label="재료 제안">
          {matches.length === 0 ? (
            // 없는 것을 쳤을 때 목록이 그냥 사라지면 "찾는 중인가" 와 구분이 안 된다
            <li className="search-box__option search-box__option--empty">
              그런 이름의 재료가 없습니다
            </li>
          ) : (
            matches.map((ing, i) => (
              <li
                key={ing.slug}
                id={`${listId}-${i}`}
                role="option"
                aria-selected={i === active}
                className="search-box__option"
                data-active={i === active}
                // 마우스로 고를 때도 키보드와 같은 자리를 거친다
                onMouseEnter={() => setActive(i)}
                onMouseDown={(e) => {
                  e.preventDefault();
                  choose(ing);
                }}
              >
                <span className="ko">{ing.nameKo}</span>
                <span className="en">{ing.nameEn}</span>
                {/* 이미 담은 것을 다시 고르면 빠진다. 그것을 미리 알린다 */}
                <span className="type">
                  {have.has(ing.slug) ? "담음 — 빼기" : CATEGORY_LABELS[ing.category]}
                </span>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
}
