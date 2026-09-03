"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  BASE_SPIRIT_LABELS,
  FLAVOR_LABELS,
  QUESTIONS,
  SWEETNESS,
  parseAnswers,
  quizCandidates,
  rankResults,
  toAnswerQuery,
  type Answers,
  type FlavorKey,
  type SearchItem,
} from "@mut/domain";
import { FINDER_PATH } from "@/lib/routes";
import { finderStep } from "@/lib/analytics/events";
import { cocktailPhotoSrc } from "@/lib/cocktail-photos";
import { PhotoSlot } from "./photo-slot";
import { SweetTag } from "./sweet-tag";

/**
 * 취향 파인더 (ISSUE-041 · `FR-SEARCH-004` · ADR-0003).
 *
 * ## 도수 구간이 탐색 필터와 같은 정의다
 *
 * 질문 1의 선택지가 `ABV_BANDS` 를 그대로 편다 — **파인더 전용 도수 상수가 없다.**
 * ADR-0003 이 "탐색 필터와 취향 파인더가 이 정의를 공유한다" 고 못박은 이유는 따로 두면
 * 반드시 어긋나서다. 파인더가 "가볍게" 로 추천한 것을 탐색의 "저 ~10%" 에서 못 찾으면
 * 두 화면이 서로를 부정한다.
 *
 * ## 답과 단계가 주소에 남는다
 *
 * 답을 고르면 쿼리스트링이 바뀐다 (RED 9). 새로고침해도 그 자리이고, 링크를 주면 같은
 * 화면이 열린다. 탐색 화면과 같은 이유로 `useSearchParams()` 대신 주소창을 직접 읽는다 —
 * 미리 그려 두는 경로에서 그 훅은 빈 값을 준다.
 *
 * ## 서버를 다시 부르지 않는다
 *
 * 코퍼스는 페이지가 한 번 받아 넘긴다. 단계 전환은 전부 브라우저 안에서 끝난다
 * (SPEC-05 §4 · `NFR-P-02`).
 */
export function FinderScreen({ corpus }: { corpus: SearchItem[] }) {
  const router = useRouter();

  const queryRef = useRef("");
  const [query, setQueryState] = useState("");
  /** 브라우저에서 붙었는가 — 탐색 화면과 같은 이유다 (`search-screen.tsx`). */
  const [ready, setReady] = useState(false);

  const setQuery = useCallback((q: string) => {
    queryRef.current = q;
    setQueryState(q);
  }, []);

  useEffect(() => {
    const read = () => {
      setQuery(window.location.search.replace(/^\?/, ""));
      setReady(true);
    };
    read();
    window.addEventListener("popstate", read);
    return () => window.removeEventListener("popstate", read);
  }, [setQuery]);

  const params = useMemo(() => new URLSearchParams(query), [query]);
  const answers = useMemo(() => parseAnswers(params), [params]);

  // 주소의 단계가 답변 수보다 앞서면 아직 답하지 않은 질문을 결과로 친다.
  // 답변 수로 상한을 걸어 링크를 손으로 고쳐도 흐름이 어긋나지 않게 한다.
  const answered = QUESTIONS.filter((q) => answers[q.key] !== undefined).length;
  const step = Math.min(Math.max(Number(params.get("step") ?? 0) || 0, 0), answered);

  const go = useCallback(
    (next: Answers, nextStep: number) => {
      const q = toAnswerQuery(next, nextStep).toString();
      setQuery(q);
      router.replace(q ? `${FINDER_PATH}?${q}` : FINDER_PATH, { scroll: false });
    },
    [router, setQuery]
  );

  const candidates = useMemo(() => quizCandidates(corpus, answers), [corpus, answers]);
  const done = step >= QUESTIONS.length;
  const results = useMemo(
    () => (done ? rankResults(corpus, answers) : []),
    [corpus, answers, done]
  );

  const question = QUESTIONS[Math.min(step, QUESTIONS.length - 1)];

  const pick = (value: number | string) => {
    const next = { ...answers, [question.key]: value };
    // 뒤로 갔다가 다시 고르면 그 뒤의 답은 지운다 — 화면에 남은 답과 주소가 갈리지 않게 한다.
    for (const later of QUESTIONS.slice(step + 1)) delete next[later.key];
    go(next, step + 1);

    // SPEC-10 §4.4 — 어느 질문에서 이탈하나. **`step = 4` 도달이 완주다** (완주 이벤트를
    // 따로 두지 않는다). `candidateCount` 가 1~2로 급감하면 질문이 너무 좁게 거른다는 뜻이다.
    finderStep({
      step: (step + 1) as 1 | 2 | 3 | 4,
      answered: String(value),
      candidateCount: quizCandidates(corpus, next).length,
    });
  };

  const reset = () => go({}, 0);

  const back = () => {
    if (step === 0) return;
    go(answers, step - 1);
  };

  const answerSummary = QUESTIONS.map((q) => {
    const v = answers[q.key];
    return q.options.find((o) => o.value === v)?.ko;
  })
    .filter(Boolean)
    .join(" · ");

  return (
    <main className="shell finder" data-ready={ready || undefined}>
      <header className="page-head" style={{ gridTemplateColumns: "1fr" }}>
        <div>
          <h1>
            취향 파인더<span className="sub">{QUESTIONS.length} questions</span>
          </h1>
        </div>
      </header>

      {!done ? (
        <div className="quiz-layout">
          <aside>
            {/* .filter-label 을 인라인으로 다시 만들고 있었다 — 같은 것이면 같은 클래스를 쓴다 */}
            <div className="filter-label">진행 PROGRESS</div>
            <div className="quiz-nav">
              {QUESTIONS.map((q, i) => {
                const v = answers[q.key];
                const picked = v !== undefined ? q.options.find((o) => o.value === v)?.ko : "—";
                const state = i === step ? "current" : v === undefined ? "pending" : "answered";
                return (
                  <div className="quiz-nav-item" key={q.key} data-state={state}>
                    <span className="n">{String(i + 1).padStart(2, "0")}</span>
                    <span className="label">
                      {q.title.replace(/\?$/, "")}
                      <span className="answer" style={{ display: "block" }}>
                        {picked}
                      </span>
                    </span>
                  </div>
                );
              })}
            </div>

            {/*
              후보 수는 답을 고를 때마다 바뀐다. 화면을 보는 사람은 숫자가 줄어드는 것을
              보지만 스크린리더는 알 수 없어 `aria-live` 로 읽어 준다 (RED 22·23).
              `polite` 인 이유: 조작을 끊지 않고 다음 안내 자리에 끼워 읽는 편이 낫다.
            */}
            <div className="quiz-count" aria-live="polite">
              현재 후보 <b>{candidates.length}</b>종
              <span className="visually-hidden">
                {` · 질문 ${Math.min(step + 1, QUESTIONS.length)} / ${QUESTIONS.length}`}
              </span>
            </div>

            {/*
              RED 11 — 후보가 0이면 남은 질문에 답해도 결과가 없다. 막다른 길로 계속
              걸어가게 두지 않고 그 자리에서 알린다.
            */}
            {candidates.length === 0 && (
              <div className="quiz-dead-end" role="status">
                <b>조건에 맞는 항목이 없습니다.</b>
                <span>이전 단계로 돌아가 조건을 넓히거나 처음부터 다시 시작하세요.</span>
              </div>
            )}
          </aside>

          <section className="quiz-card">
            <div className="quiz-kicker">
              QUESTION {Math.min(step + 1, QUESTIONS.length)} / {QUESTIONS.length}
            </div>
            <h2>{question.title}</h2>
            <p style={{ fontSize: 13, color: "var(--color-neutral-700)", marginBottom: 28 }}>
              {question.hint}
            </p>
            <div className="quiz-options">
              {question.options.map((o) => {
                const on = answers[question.key] === o.value;
                return (
                  <button
                    type="button"
                    key={String(o.value)}
                    className="btn quiz-option"
                    aria-pressed={on}
                    onClick={() => pick(o.value)}
                  >
                    <span>
                      <span className="ko">{o.ko}</span>
                      <span className="en">{o.en}</span>
                    </span>
                    {/* 고른 것을 색 말고도 알아볼 수 있게 표시한다 (`NFR-A-08`) */}
                    {on && <span className="quiz-option__mark">선택함</span>}
                  </button>
                );
              })}
            </div>
            {/* 1번 질문에는 돌아갈 곳도 되돌릴 것도 없다 — 비활성 버튼 둘을 보이지 않는다 (#183) */}
            {step > 0 && (
              <div className="quiz-foot">
                <button type="button" className="btn btn-secondary" onClick={back}>
                  ← 이전
                </button>
                <button type="button" className="btn btn-ghost" onClick={reset}>
                  처음부터 RESTART
                </button>
              </div>
            )}
          </section>
        </div>
      ) : (
        <section style={{ paddingTop: 32 }}>
          <div className="rule-head">
            <h4 style={{ margin: 0 }}>
              추천 결과 TOP {results.length}
              <span
                style={{
                  fontWeight: 400,
                  fontSize: 13,
                  color: "var(--color-neutral-700)",
                  marginLeft: 12,
                }}
              >
                {answerSummary}
              </span>
            </h4>
            <button type="button" className="btn btn-secondary" onClick={reset}>
              다시 하기 RESTART
            </button>
          </div>

          {results.length > 0 ? (
            <div className="result-grid">
              {results.map(({ cocktail, match }, i) => (
                <article className="result-card" key={cocktail.slug} data-rank={i + 1}>
                  <div className="result-card__head">
                    <span className="rank">RANK {i + 1}</span>
                    <span className="match">
                      {match}
                      <span>%</span>
                    </span>
                  </div>
                  <PhotoSlot
                    ratio="4x3"
                    caption={cocktail.nameEn}
                    src={cocktailPhotoSrc(cocktail.slug)}
                    alt={`${cocktail.nameKo} 칵테일 사진`}
                  />
                  <div className="result-card__body">
                    <h3>{cocktail.nameKo}</h3>
                    <div className="cocktail-card__en" style={{ marginBottom: 10 }}>
                      {cocktail.nameEn}
                    </div>
                    <div className="tag-row" style={{ marginBottom: 10 }}>
                      <span className="tag tag-neutral tag-bordered">
                        {BASE_SPIRIT_LABELS[cocktail.base]}
                      </span>
                      <SweetTag level={cocktail.sweet} en />
                      <span className="tag tag-neutral tag-bordered">{cocktail.abv}% ABV</span>
                    </div>
                    <p className="result-card__why">
                      선택한{" "}
                      {answers.flavor
                        ? `${FLAVOR_LABELS[answers.flavor as FlavorKey].split(" ")[1]} 계열`
                        : "조건"}
                      과 {SWEETNESS[cocktail.sweet][1]} 당도, {cocktail.abv}% 도수가 조건 범위에
                      들어옵니다.
                    </p>
                    <Link
                      href={`/cocktails/${cocktail.slug}`}
                      className="btn btn-primary btn-block"
                    >
                      레시피 보기 →
                    </Link>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <div className="empty-state">
              <h3>조건을 모두 만족하는 항목이 없습니다</h3>
              <p>도수나 당도 조건을 넓혀 다시 시도해보세요.</p>
            </div>
          )}
        </section>
      )}
    </main>
  );
}
