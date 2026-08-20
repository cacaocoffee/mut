"use client";

import { useEffect, useId, useState } from "react";
import type { components } from "@mut/domain/generated/api";

type SearchHit = components["schemas"]["SearchHit"];

/**
 * 검색 입력 + 자동완성 (ISSUE-042 · `FR-SEARCH-006`·`007` · SPEC-07 §2.4).
 *
 * ## 300ms 를 기다렸다가 부른다
 *
 * `/search/suggest` 는 60 req/min · IP 기준이다 (SPEC-08 §6). 글자마다 부르면 한 낱말을
 * 치는 동안 한도를 다 쓴다. 입력이 멎고 300ms 뒤에 한 번 부른다 — 초당 1회 상한보다 넉넉하다.
 *
 * ## 실패하면 조용히 접는다
 *
 * 자동완성이 429 나 502 를 받아도 **검색 자체는 된다.** 드롭다운만 닫고 입력을 막지 않는다
 * (RED 25) — 부가 기능 하나가 화면 전체를 세우지 않는다.
 *
 * ## 제안을 **질의와 함께** 들고 있는다
 *
 * 응답에 질의를 붙여 두고 지금 입력과 같을 때만 보여 준다. 늦게 도착한 앞 글자의 제안이
 * 뒤 글자의 목록을 덮는 일이 없고, 입력을 지우면 그리지 않는 것으로 끝난다.
 */
const DEBOUNCE_MS = 300;

/** 드롭다운 상한. 서버도 8 로 자르지만(`SUGGEST_LIMIT`) 계약이 늘어도 화면은 그대로여야 한다. */
const MAX_SUGGESTIONS = 8;

export function SearchBox({
  initialQuery = "",
  onSubmit,
}: {
  initialQuery?: string;
  onSubmit: (query: string) => void;
}) {
  const [value, setValue] = useState(initialQuery);
  const [answered, setAnswered] = useState<{ q: string; hits: SearchHit[] }>({ q: "", hits: [] });
  const [active, setActive] = useState(-1);
  const [closed, setClosed] = useState(false);
  const listId = useId();

  // 주소로 들어온 질의가 바뀌면 입력도 따라간다 (뒤로가기·링크 진입).
  // 렌더 중에 맞춘다 — 효과로 하면 한 프레임 동안 옛 값이 보인다.
  const [lastFromUrl, setLastFromUrl] = useState(initialQuery);
  if (lastFromUrl !== initialQuery) {
    setLastFromUrl(initialQuery);
    setValue(initialQuery);
  }

  const term = value.trim();
  const suggestions = !closed && answered.q === term ? answered.hits : [];

  useEffect(() => {
    if (term.length === 0) return;

    // 입력이 멎기를 기다린다. 다음 글자가 오면 이 타이머는 버려진다.
    const timer = setTimeout(async () => {
      try {
        const res = await fetch(`/api/search/suggest?q=${encodeURIComponent(term)}`);
        // 429·502 면 빈 목록이다 — 자동완성만 접고 검색은 그대로 된다.
        const hits = res.ok ? ((await res.json()) as SearchHit[]) : [];
        setAnswered({ q: term, hits: hits.slice(0, MAX_SUGGESTIONS) });
      } catch {
        setAnswered({ q: term, hits: [] });
      }
    }, DEBOUNCE_MS);

    return () => clearTimeout(timer);
  }, [term]);

  const close = () => {
    setClosed(true);
    setActive(-1);
  };

  const choose = (hit: SearchHit) => {
    setValue(hit.nameKo);
    close();
    onSubmit(hit.nameKo);
  };

  const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      if (suggestions.length === 0) return;
      e.preventDefault();
      const delta = e.key === "ArrowDown" ? 1 : -1;
      setActive((i) => (i + delta + suggestions.length) % suggestions.length);
      return;
    }
    if (e.key === "Escape") {
      close();
      return;
    }
    if (e.key === "Enter") {
      e.preventDefault();
      if (active >= 0 && suggestions[active]) choose(suggestions[active]);
      else {
        close();
        onSubmit(term);
      }
    }
  };

  return (
    <div className="search-box">
      <label className="filter-label" htmlFor={`${listId}-input`}>
        검색
      </label>
      <input
        id={`${listId}-input`}
        className="input search-box__input"
        type="search"
        role="combobox"
        autoComplete="off"
        // 초성 검색은 아는 사람만 쓴다 — 플레이스홀더가 그것을 말한다 (RED 8)
        placeholder="올드패션드 · Old Fashioned · ㅁㄹㄱㄹㅌ (초성)"
        value={value}
        aria-label="칵테일 · 재료 통합 검색. 초성으로도 찾을 수 있습니다"
        aria-expanded={suggestions.length > 0}
        aria-controls={listId}
        aria-activedescendant={active >= 0 ? `${listId}-${active}` : undefined}
        onChange={(e) => {
          setValue(e.target.value);
          setClosed(false);
          setActive(-1);
        }}
        onKeyDown={onKeyDown}
      />

      {suggestions.length > 0 && (
        <ul className="search-box__list" id={listId} role="listbox" aria-label="검색 제안">
          {suggestions.map((hit, i) => (
            <li
              key={`${hit.entityType}:${hit.slug}`}
              id={`${listId}-${i}`}
              role="option"
              aria-selected={i === active}
              className="search-box__option"
              data-active={i === active}
              // 마우스로 고를 때도 키보드와 같은 자리를 거친다
              onMouseEnter={() => setActive(i)}
              onMouseDown={(e) => {
                e.preventDefault();
                choose(hit);
              }}
            >
              <span className="ko">{hit.nameKo}</span>
              {hit.nameEn && <span className="en">{hit.nameEn}</span>}
              <span className="type">{ENTITY_LABELS[hit.entityType] ?? hit.entityType}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** 그룹 이름과 같은 말을 쓴다 — 제안과 결과에서 다르게 부르면 같은 것인지 알 수 없다. */
export const ENTITY_LABELS: Record<string, string> = {
  cocktail: "칵테일",
  ingredient: "재료",
  bar: "바",
  article: "아티클",
};
