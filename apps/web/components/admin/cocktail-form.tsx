"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  BASE_SPIRIT_LABELS,
  FLAVOR_KEY_LABELS,
  STYLE_KEY_LABELS,
  SWEET_LEVEL_LABELS,
  TECHNIQUE_LABELS,
} from "@kca/domain";
import type { AdminCocktail, Violation } from "@/lib/admin-api";

/**
 * 칵테일 편집 (ISSUE-047 · `FR-ADMIN-002`·`003` · `FR-COCKTAIL-002`·`008`·`014`).
 *
 * ## 입력 강제는 **편의**다
 *
 * `PRIN-T05` — "프론트 검증은 UX 용 중복이다. 없어도 데이터가 깨지지 않아야 한다."
 * 스타일 후보 좁히기·향 4개째 차단은 손이 덜 가게 하는 것이고, **막는 것은 서버**다.
 * 그래서 여기서 게이트를 흉내 내지 않는다 — 최종 판정은 발행 요청의 응답이다.
 *
 * ## 실패는 전부 한 번에 보여 준다
 *
 * `FR-ADMIN-003` — **하나씩 고치게 하지 않는다.** 서버가 `violations` 를 통째로 주므로
 * (SPEC-07 §1.4) 화면은 자르지 않고 전부 그린다.
 */
const FLAVOR_MAX = 3;

type Status = "draft" | "published" | "archived";

export function CocktailForm({ cocktail }: { cocktail: AdminCocktail | null }) {
  const router = useRouter();
  const isNew = cocktail === null;

  const [form, setForm] = useState(() => initial(cocktail));
  const [violations, setViolations] = useState<Violation[]>([]);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const status = (cocktail?.status ?? "draft") as Status;
  // `FR-COCKTAIL-014` — 발행된 뒤에는 주소가 굳는다. 바꾸면 링크가 끊긴다.
  const slugLocked = status !== "draft";

  const set = <K extends keyof typeof form>(key: K, value: (typeof form)[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  /** 저장에 필요한 최소치. **게이트가 아니다** — 서버가 정본이다 (`PRIN-T05`). */
  const canSave =
    form.nameKo.trim() !== "" &&
    form.slug.trim() !== "" &&
    form.styles.length > 0 &&
    form.styles.includes(form.stylePrimary) &&
    form.aromaTags.length > 0 &&
    form.aromaTags.length <= FLAVOR_MAX;

  async function call(path: string, init: RequestInit): Promise<Response | null> {
    setBusy(true);
    setMessage(null);
    try {
      return await fetch(`/api/admin/${path}`, {
        headers: { "Content-Type": "application/json" },
        ...init,
      });
    } catch {
      setMessage("서버를 부르지 못했습니다");
      return null;
    } finally {
      setBusy(false);
    }
  }

  async function save() {
    const res = await call(isNew ? "cocktails" : `cocktails/${cocktail!.id}`, {
      method: isNew ? "POST" : "PATCH",
      body: JSON.stringify(form),
    });
    if (!res) return;

    if (res.ok) {
      const saved = (await res.json()) as AdminCocktail;
      setViolations([]);
      setMessage("저장했습니다");
      if (isNew) router.replace(`/admin/cocktails/${saved.id}`);
      else router.refresh();
      return;
    }
    await showFailure(res);
  }

  /** `FR-ADMIN-002` — `draft ↔ published`, 그리고 보관. 되돌릴 수 있다. */
  async function transition(action: "publish" | "unpublish" | "archive") {
    const res = await call(`cocktails/${cocktail!.id}/${action}`, { method: "POST" });
    if (!res) return;

    if (res.ok) {
      setViolations([]);
      setMessage(action === "publish" ? "발행했습니다" : "상태를 바꿨습니다");
      router.refresh();
      return;
    }
    await showFailure(res);
  }

  /** 게이트 실패(422)는 `violations` 를 전부 받아 그린다. 나머지는 한 줄로 알린다. */
  async function showFailure(res: Response) {
    if (res.status === 422) {
      const body = (await res.json()) as { violations?: Violation[] };
      setViolations(body.violations ?? []);
      setMessage("아직 발행할 수 없습니다 — 아래를 모두 채워 주세요");
      return;
    }
    setViolations([]);
    setMessage(
      res.status === 409
        ? "이미 그 상태입니다"
        : res.status === 404
          ? "권한이 없거나 없는 항목입니다"
          : `저장하지 못했습니다 (HTTP ${res.status})`,
    );
  }

  return (
    <div className="admin-form">
      <div className="admin-form__head">
        <h2>{isNew ? "새 칵테일" : cocktail!.nameKo}</h2>
        <span className={`admin-status admin-status--${status}`}>{STATUS_LABELS[status]}</span>
      </div>

      {/* 발행 조건 패널 — 실패를 **전부 한 번에** (FR-ADMIN-003) */}
      {violations.length > 0 && (
        <section className="gate-panel" role="alert" aria-label="발행 조건">
          <h3>발행 조건 {violations.length}건이 남았습니다</h3>
          <ul>
            {violations.map((v) => (
              <li key={`${v.code}-${v.field ?? ""}`} className="gate-panel__item">
                <b>{v.field ?? "전체"}</b>
                <span>{v.message}</span>
                {/* 코드를 보여 준다 — 문구는 바뀌어도 코드는 스펙 ID 다 (SPEC-07 §1.4) */}
                <code>{v.code}</code>
              </li>
            ))}
          </ul>
        </section>
      )}

      {message && (
        <p className="admin-form__message" role="status">
          {message}
        </p>
      )}

      <div className="admin-form__grid">
        <Field label="한국어 이름">
          <input
            className="input"
            name="nameKo"
            value={form.nameKo}
            onChange={(e) => set("nameKo", e.target.value)}
          />
        </Field>

        <Field label="영문 이름">
          <input
            className="input"
            name="nameEn"
            value={form.nameEn}
            onChange={(e) => set("nameEn", e.target.value)}
          />
        </Field>

        <Field
          label="주소 (slug)"
          hint={slugLocked ? "발행 뒤에는 바꿀 수 없습니다 — 링크가 끊깁니다" : undefined}
        >
          <input
            className="input"
            name="slug"
            value={form.slug}
            disabled={slugLocked}
            onChange={(e) => set("slug", e.target.value)}
          />
        </Field>

        <Field label="기주">
          <Select
            name="baseSpirit"
            value={form.baseSpirit}
            options={BASE_SPIRIT_LABELS}
            onChange={(v) => set("baseSpirit", v)}
          />
        </Field>

        <Field label="메이킹">
          <Select
            name="method"
            value={form.method}
            options={TECHNIQUE_LABELS}
            onChange={(v) => set("method", v)}
          />
        </Field>

        <Field label="당도">
          <Select
            name="sweetness"
            value={form.sweetness}
            options={SWEET_LEVEL_LABELS}
            onChange={(v) => set("sweetness", v)}
          />
        </Field>

        <Field label="잔">
          <input
            className="input"
            name="glassType"
            value={form.glassType}
            onChange={(e) => set("glassType", e.target.value)}
          />
        </Field>
      </div>

      {/* `FR-COCKTAIL-002` — 대표 스타일은 고른 스타일 중에서만 */}
      <Field label="스타일" hint="복수 선택. 대표 스타일은 여기서 고른 것 중에서만 정할 수 있습니다">
        <div className="chip-row">
          {Object.entries(STYLE_KEY_LABELS).map(([slug, label]) => (
            <button
              type="button"
              key={slug}
              className="btn chip"
              aria-pressed={form.styles.includes(slug)}
              onClick={() => toggleStyle(slug)}
            >
              {label}
            </button>
          ))}
        </div>
      </Field>

      <Field label="대표 스타일">
        <Select
          name="stylePrimary"
          value={form.stylePrimary}
          // 고른 스타일만 후보다. 이것이 `FR-COCKTAIL-002` 의 UI 쪽 구현이다.
          options={Object.fromEntries(
            form.styles.map((s) => [s, STYLE_KEY_LABELS[s as keyof typeof STYLE_KEY_LABELS]]),
          )}
          onChange={(v) => set("stylePrimary", v)}
        />
      </Field>

      {/* `FR-COCKTAIL-008` — 1~3개. 4개째는 눌리지 않는다 */}
      <Field label="향 · 맛" hint={`1~${FLAVOR_MAX}개 (${form.aromaTags.length}개 선택)`}>
        <div className="chip-row">
          {Object.entries(FLAVOR_KEY_LABELS).map(([slug, label]) => {
            const on = form.aromaTags.includes(slug);
            return (
              <button
                type="button"
                key={slug}
                className="chip-tag"
                aria-pressed={on}
                disabled={!on && form.aromaTags.length >= FLAVOR_MAX}
                onClick={() =>
                  set(
                    "aromaTags",
                    on ? form.aromaTags.filter((t) => t !== slug) : [...form.aromaTags, slug],
                  )
                }
              >
                {label}
              </button>
            );
          })}
        </div>
      </Field>

      <Field label="한 줄 소개">
        <input
          className="input"
          name="summary"
          value={form.summary}
          onChange={(e) => set("summary", e.target.value)}
        />
      </Field>

      <Field label="향·맛 서술" hint="발행 필수입니다 (GATE-COCKTAIL-01)">
        <textarea
          className="input"
          name="tastingNote"
          rows={4}
          value={form.tastingNote}
          onChange={(e) => set("tastingNote", e.target.value)}
        />
      </Field>

      <div className="admin-form__actions">
        <button type="button" className="btn btn-primary" disabled={!canSave || busy} onClick={save}>
          저장 SAVE
        </button>

        {/* `FR-ADMIN-002` · DECISIONS §1.4 — 현재 상태에서 갈 수 있는 곳만 보인다.
            draft 에서 archived 로 바로 가는 버튼은 없다. */}
        {!isNew && status === "draft" && (
          <button type="button" className="btn" disabled={busy} onClick={() => transition("publish")}>
            발행 PUBLISH
          </button>
        )}
        {!isNew && status === "published" && (
          <>
            <button
              type="button"
              className="btn"
              disabled={busy}
              onClick={() => transition("unpublish")}
            >
              발행 취소
            </button>
            <button
              type="button"
              className="btn"
              disabled={busy}
              onClick={() => transition("archive")}
            >
              보관 ARCHIVE
            </button>
          </>
        )}
      </div>
    </div>
  );

  function toggleStyle(slug: string) {
    const next = form.styles.includes(slug)
      ? form.styles.filter((s) => s !== slug)
      : [...form.styles, slug];

    setForm((f) => ({
      ...f,
      styles: next,
      // 대표가 목록에서 빠지면 비운다 — 목록에 없는 값을 대표로 둘 수 없다.
      stylePrimary: next.includes(f.stylePrimary) ? f.stylePrimary : "",
    }));
  }
}

const STATUS_LABELS: Record<Status, string> = {
  draft: "초안",
  published: "발행됨",
  archived: "보관됨",
};

function initial(c: AdminCocktail | null) {
  return {
    nameKo: c?.nameKo ?? "",
    nameEn: c?.nameEn ?? "",
    slug: c?.slug ?? "",
    baseSpirit: c?.baseSpirit ?? "gin",
    method: c?.method ?? "build",
    sweetness: c?.sweetness ?? "dry",
    glassType: c?.glassType ?? "",
    styles: c?.styles ?? [],
    stylePrimary: c?.stylePrimary ?? "",
    aromaTags: c?.aromaTags ?? [],
    summary: c?.summary ?? "",
    tastingNote: c?.tastingNote ?? "",
    aliases: c?.aliases ?? [],
    isClassic: c?.isClassic ?? false,
  };
}

function Field({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="admin-field">
      <span className="admin-field__label">{label}</span>
      {children}
      {hint && <span className="admin-field__hint">{hint}</span>}
    </label>
  );
}

function Select({
  name,
  value,
  options,
  onChange,
}: {
  name: string;
  value: string;
  options: Record<string, string>;
  onChange: (value: string) => void;
}) {
  return (
    <select
      className="input"
      name={name}
      value={value}
      onChange={(e) => onChange(e.target.value)}
    >
      <option value="">—</option>
      {Object.entries(options).map(([slug, label]) => (
        <option key={slug} value={slug}>
          {label}
        </option>
      ))}
    </select>
  );
}
