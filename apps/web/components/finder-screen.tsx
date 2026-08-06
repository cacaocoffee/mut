"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import {
  FLAVOR_LABELS,
  QUESTIONS,
  SWEETNESS,
  quizCandidates,
  rankResults,
  type Answers,
  type FlavorKey,
} from "@kca/domain";
import { PhotoSlot } from "./photo-slot";
import { SweetTag } from "./sweet-tag";

export function FinderScreen() {
  const [step, setStep] = useState(0);
  const [answers, setAnswers] = useState<Answers>({});

  const candidates = useMemo(() => quizCandidates(answers), [answers]);
  const results = useMemo(
    () => (step >= QUESTIONS.length ? rankResults(answers) : []),
    [answers, step]
  );

  const done = step >= QUESTIONS.length;
  const question = QUESTIONS[Math.min(step, QUESTIONS.length - 1)];

  const pick = (value: number | string) => {
    setAnswers((prev) => ({ ...prev, [question.key]: value }));
    setStep((s) => s + 1);
  };

  const reset = () => {
    setAnswers({});
    setStep(0);
  };

  const answerSummary = QUESTIONS.map((q) => {
    const v = answers[q.key];
    return q.options.find((o) => o.value === v)?.ko;
  })
    .filter(Boolean)
    .join(" · ");

  return (
    <main className="shell finder">
      <header className="page-head" style={{ gridTemplateColumns: "1fr" }}>
        <div>
          <h6 style={{ margin: "0 0 10px", color: "var(--color-accent-700)" }}>Interactive finder</h6>
          <h1>
            취향 파인더<span className="sub">{QUESTIONS.length} questions</span>
          </h1>
        </div>
      </header>

      {!done ? (
        <div className="quiz-layout">
          <aside>
            <div
              style={{
                fontSize: 11,
                letterSpacing: "0.1em",
                textTransform: "uppercase",
                color: "var(--color-neutral-700)",
                marginBottom: 10,
              }}
            >
              진행 PROGRESS
            </div>
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
            <div style={{ marginTop: 20, fontSize: 11, color: "var(--color-neutral-700)" }}>
              현재 후보{" "}
              <b style={{ fontSize: 15, color: "var(--color-text)" }}>{candidates.length}</b>종
            </div>
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
              {question.options.map((o) => (
                <button
                  type="button"
                  key={String(o.value)}
                  className="btn quiz-option"
                  aria-pressed={answers[question.key] === o.value}
                  onClick={() => pick(o.value)}
                >
                  <span>
                    <span className="ko">{o.ko}</span>
                    <span className="en">{o.en}</span>
                  </span>
                </button>
              ))}
            </div>
            <div className="quiz-foot">
              <button
                type="button"
                className="btn btn-secondary"
                disabled={step === 0}
                onClick={() => setStep((s) => Math.max(0, s - 1))}
              >
                ← 이전
              </button>
              <button type="button" className="btn btn-ghost" onClick={reset}>
                처음부터 RESTART
              </button>
            </div>
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
                <article className="result-card" key={cocktail.id} data-rank={i + 1}>
                  <div className="result-card__head">
                    <span className="rank">RANK {i + 1}</span>
                    <span className="match">
                      {match}
                      <span>%</span>
                    </span>
                  </div>
                  <PhotoSlot ratio="4x3" caption={cocktail.en} />
                  <div className="result-card__body">
                    <h3>{cocktail.ko}</h3>
                    <div className="cocktail-card__en" style={{ marginBottom: 10 }}>
                      {cocktail.en}
                    </div>
                    <div className="tag-row" style={{ marginBottom: 10 }}>
                      <span
                        className="tag tag-neutral"
                        style={{ border: "1px solid var(--color-divider)" }}
                      >
                        {cocktail.base}
                      </span>
                      <SweetTag level={cocktail.sweet} en />
                      <span
                        className="tag tag-neutral"
                        style={{ border: "1px solid var(--color-divider)" }}
                      >
                        {cocktail.abv}% ABV
                      </span>
                    </div>
                    <p className="result-card__why">
                      선택한{" "}
                      {answers.flavor
                        ? `${FLAVOR_LABELS[answers.flavor as FlavorKey].split(" ")[1]} 계열`
                        : "조건"}
                      과 {SWEETNESS[cocktail.sweet][1]} 당도, {cocktail.abv}% 도수가 조건 범위에
                      들어옵니다.
                    </p>
                    <Link href={`/cocktails/${cocktail.id}`} className="btn btn-primary btn-block">
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
