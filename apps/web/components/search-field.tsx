"use client";

/**
 * 탐색 화면의 이름 검색 칸.
 *
 * ## 필터보다 먼저 온다
 *
 * 예전에는 필터 패널 **맨 아래** 한 칸이었다. 가장 자주 손이 가는 것이 가장 눈에 안 띄는
 * 자리에 있었고, 그래서 통합 검색이 탭으로 따로 있어야 할 것처럼 보였다. 두 컬럼 위에
 * 전폭으로 두면 이것이 아래 전체(그리드 · 필터 카운트)에 걸린다는 것이 형태로 읽힌다.
 *
 * ## 지우는 버튼이 있다
 *
 * 검색어는 주소에 실려 남는다. 지우려면 글자를 하나씩 지워야 했는데, 그동안 매 글자마다
 * 결과가 다시 계산됐다. 한 번에 비우는 길을 둔다 — 값이 있을 때만 나온다.
 *
 * `type="search"` 가 브라우저 기본 ✕ 를 함께 그린다. 둘이 나란히 서면 무엇을 눌러야
 * 하는지 알 수 없어 기본 쪽을 CSS 로 감춘다 — 그쪽은 이름이 없어 스크린리더가 못 읽고
 * 모양도 브라우저마다 다르다.
 *
 * ## 초성은 색인이 붙어야 된다
 *
 * `ㄴㄱㄹㄴ` → `네그로니` 는 서버 색인의 일이라 `MUT_API_URL` 이 없으면 안 된다
 * (`lib/use-name-index.ts`). 되지 않는 것을 placeholder 로 약속하지 않는다 —
 * 색인이 붙으면 그때 예시에 넣는다.
 *
 * ## 찾는 중을 소리로도 알린다
 *
 * 색인을 부르는 사이 결과 수가 잠깐 예전 값이다. 화면을 보는 사람은 곧 바뀌는 것을 알지만
 * 스크린리더는 다시 읽지 않으므로 상태를 한 줄로 읽어 준다 (`NFR-A-07`).
 */
export function SearchField({
  value,
  pending,
  onChange,
}: {
  value: string;
  pending: boolean;
  onChange: (next: string) => void;
}) {
  return (
    <div className="search-field">
      <label className="search-field__label" htmlFor="cocktail-q">
        검색
      </label>

      <div className="search-field__box">
        <input
          id="cocktail-q"
          className="input search-field__input"
          type="search"
          placeholder="네그로니 / Negroni"
          value={value}
          autoComplete="off"
          aria-label="칵테일 이름 검색"
          aria-describedby="cocktail-q-note"
          onChange={(e) => onChange(e.target.value)}
        />
        {value && (
          <button
            type="button"
            className="btn btn-ghost search-field__clear"
            aria-label="검색어 지우기"
            onClick={() => onChange("")}
          >
            지움
          </button>
        )}
      </div>

      <p id="cocktail-q-note" className="search-field__note" aria-live="polite">
        {pending ? "찾는 중…" : "이름과 아래 필터가 함께 걸립니다."}
      </p>
    </div>
  );
}
