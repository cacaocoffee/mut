"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { adminWrite } from "@/lib/admin-csrf";
import { useToast } from "@/components/toast";
import type { AdminArticleDetail } from "@/lib/admin-api";
import { ArticleBlockEditor, type Block } from "@/components/admin/article-block-editor";

/**
 * 아티클 편집 (ADR-0011 5단계). 칵테일 폼과 같은 규약:
 * 저장(기본 정보+본문)과 발행·삭제를 시각적으로 나누고, 결과는 토스트로 알린다.
 *
 * 칵테일과 다른 점: 발행 게이트가 없다 — 존재하면 발행할 수 있다.
 */
const CATEGORIES: { value: string; label: string }[] = [
  { value: "cocktail", label: "칵테일" },
  { value: "bar", label: "바" },
  { value: "spirits", label: "스피릿" },
];

type Status = "draft" | "published" | "archived";

interface Props {
  article: AdminArticleDetail | null;
}

export function ArticleForm({ article }: Props) {
  const router = useRouter();
  const isNew = article === null;
  const toast = useToast();

  const [form, setForm] = useState(() => ({
    slug: article?.slug ?? "",
    category: article?.category ?? "cocktail",
    title: article?.title ?? "",
    dek: article?.dek ?? "",
    hero: article?.hero ?? "",
    sourceUrl: article?.sourceUrl ?? "",
    isSponsored: article?.isSponsored ?? false,
    relatedCocktailSlugs: (article?.relatedCocktails ?? []).map((c) => c.slug).join(", "),
  }));
  const [blocks, setBlocks] = useState<Block[]>(() => (article?.body as unknown as Block[]) ?? []);
  const [busy, setBusy] = useState(false);

  const status = (article?.status ?? "draft") as Status;
  const slugLocked = status !== "draft";
  const set = <K extends keyof typeof form>(k: K, v: (typeof form)[K]) => setForm((f) => ({ ...f, [k]: v }));

  const canSave =
    form.slug.trim() !== "" && form.title.trim() !== "" && form.dek.trim() !== "" && form.hero.trim() !== "";

  async function call(path: string, init: RequestInit): Promise<Response | null> {
    setBusy(true);
    try {
      return await adminWrite(`/api/admin/${path}`, {
        headers: { "Content-Type": "application/json" },
        ...init,
      });
    } catch {
      toast.error("서버를 부르지 못했습니다");
      return null;
    } finally {
      setBusy(false);
    }
  }

  /**
   * 사진을 GCS 에 올리고 공개 주소를 돌려준다 (ADR-0011 이미지 업로드).
   * multipart 라 Content-Type 을 직접 넣지 않는다 — 브라우저가 boundary 를 붙인다.
   * slug 를 함께 보내 글 폴더 아래에 담기게 한다.
   */
  async function uploadImage(file: File): Promise<string | null> {
    const data = new FormData();
    data.append("file", file);
    if (form.slug.trim()) data.append("slug", form.slug.trim());
    try {
      const res = await adminWrite("/api/admin/media", { method: "POST", body: data });
      if (!res.ok) {
        toast.error(res.status === 413 ? "사진이 너무 큽니다" : `사진을 올리지 못했습니다 (HTTP ${res.status})`);
        return null;
      }
      const { url } = (await res.json()) as { url: string };
      toast.success("사진을 올렸습니다");
      return url;
    } catch {
      toast.error("사진을 올리지 못했습니다");
      return null;
    }
  }

  async function save() {
    const payload = {
      slug: form.slug.trim(),
      category: form.category,
      title: form.title.trim(),
      dek: form.dek.trim(),
      hero: form.hero.trim(),
      sourceUrl: form.sourceUrl.trim() || undefined,
      isSponsored: form.isSponsored,
      body: blocks,
      relatedCocktailSlugs: form.relatedCocktailSlugs
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean),
    };
    const res = await call(isNew ? "articles" : `articles/${article!.id}`, {
      method: isNew ? "POST" : "PATCH",
      body: JSON.stringify(payload),
    });
    if (!res) return;
    if (!res.ok) {
      toast.error(
        res.status === 409
          ? "이미 있는 slug 입니다"
          : res.status === 404
            ? "권한이 없거나 없는 항목입니다"
            : `저장하지 못했습니다 (HTTP ${res.status})`,
      );
      return;
    }
    const saved = (await res.json()) as AdminArticleDetail;

    // 저장 = 저장하고 바로 공개. 초안 개념을 두지 않는다 — 아직 발행 전이면 발행까지 한다.
    // (서버는 상태 전이를 별도 경로로 두지만, 화면은 "저장" 하나로 합쳐 보여 준다.)
    if (saved.status !== "published") {
      const pub = await call(`articles/${saved.id}/publish`, { method: "POST" });
      if (pub && !pub.ok) {
        toast.error(`공개하지 못했습니다 (HTTP ${pub.status})`);
        return;
      }
    }

    toast.success("저장했습니다");
    if (isNew) router.replace(`/admin/articles/${saved.id}`);
    else router.refresh();
  }

  async function remove() {
    if (!window.confirm("이 아티클을 삭제합니다. 목록에서 사라집니다.")) return;
    const res = await call(`articles/${article!.id}/archive`, { method: "POST" });
    if (!res) return;
    if (res.ok) {
      toast.success("삭제했습니다");
      router.push("/admin/articles");
      return;
    }
    toast.error(`삭제하지 못했습니다 (HTTP ${res.status})`);
  }

  return (
    <div className="admin-form">
      <div className="admin-form__head">
        <h2>{isNew ? "새 아티클" : article!.title}</h2>
      </div>

      <div className="admin-form__grid">
        <label className="admin-field">
          <span>제목</span>
          <input className="input" value={form.title} onChange={(e) => set("title", e.target.value)} />
        </label>
        <label className="admin-field">
          <span>주소 (slug){slugLocked ? " · 발행 뒤 잠김" : ""}</span>
          <input className="input" value={form.slug} disabled={slugLocked} onChange={(e) => set("slug", e.target.value)} />
        </label>
        <label className="admin-field">
          <span>카테고리</span>
          <select className="input" value={form.category} onChange={(e) => set("category", e.target.value)}>
            {CATEGORIES.map((c) => (
              <option key={c.value} value={c.value}>
                {c.label}
              </option>
            ))}
          </select>
        </label>
        <label className="admin-field">
          <span>대표 사진</span>
          {form.hero ? (
            // eslint-disable-next-line @next/next/no-img-element -- 편집기 미리보기라 next/image 를 쓰지 않는다
            <img className="block-editor__preview" src={form.hero} alt="" />
          ) : null}
          <div className="admin-field__row">
            <input className="input" value={form.hero} onChange={(e) => set("hero", e.target.value)} placeholder="올리거나 주소를 붙여넣기" />
            <label className="btn">
              사진 올리기
              <input
                type="file"
                accept="image/*"
                hidden
                onChange={async (e) => {
                  const file = e.target.files?.[0];
                  e.target.value = "";
                  if (!file) return;
                  const url = await uploadImage(file);
                  if (url) set("hero", url);
                }}
              />
            </label>
          </div>
        </label>
        <label className="admin-field admin-field--wide">
          <span>요약 (dek) — 카드·검색에 쓰는 한두 문장</span>
          <textarea className="input" rows={2} value={form.dek} onChange={(e) => set("dek", e.target.value)} />
        </label>
        <label className="admin-field">
          <span>원문 주소 (선택)</span>
          <input className="input" value={form.sourceUrl} onChange={(e) => set("sourceUrl", e.target.value)} />
        </label>
        <label className="admin-field">
          <span>관련 칵테일 slug (쉼표로)</span>
          <input
            className="input"
            value={form.relatedCocktailSlugs}
            placeholder="negroni, vesper"
            onChange={(e) => set("relatedCocktailSlugs", e.target.value)}
          />
        </label>
        <label className="admin-field admin-checkbox">
          <input type="checkbox" checked={form.isSponsored} onChange={(e) => set("isSponsored", e.target.checked)} />
          <span>제휴 콘텐츠 (라벨이 붙고 끌 수 없습니다)</span>
        </label>
      </div>

      <h3 className="section-head">본문</h3>
      <ArticleBlockEditor blocks={blocks} onChange={setBlocks} uploadImage={uploadImage} />

      {/* 저장 = 저장하고 바로 공개. 초안·발행을 나누지 않는다 (사장님 결정 2026-08-25). */}
      <div className="admin-form__actions">
        <button type="button" className="btn btn-primary" disabled={!canSave || busy} onClick={save}>
          저장
        </button>
        {!isNew && (
          <button type="button" className="btn btn-danger" disabled={busy} onClick={remove}>
            삭제
          </button>
        )}
      </div>
    </div>
  );
}
