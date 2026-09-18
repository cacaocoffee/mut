"use client";

import { useToast } from "@/components/toast";
import { adminWrite } from "@/lib/admin-csrf";
import type { IngredientProductMatch } from "@/lib/admin-api";
import { MATCH_STATUS_LABELS, label } from "@/lib/ingredient-labels";
import { useState } from "react";
import { useRouter } from "next/navigation";

/**
 * 재료 ↔ 유통 제품 매핑 패널 (#195).
 *
 * "제안 만들기" 는 브랜드 검색어·별칭으로 식약처 신고 제품을 찾아 `제안` 상태로 쌓는다.
 * 제품명 표기가 제각각이라 자동은 여기까지다 — 승인·거절은 사람이 한 줄씩 누른다.
 * 승인된 줄이 유통 여부 제안의 근거가 된다 (위 폼의 "제안:" 줄).
 *
 * 구매 링크·가격은 없다 — `NFR-L-05`(주류광고 자문) 뒤다. 제품명·수입사는 사실 정보라 보여 준다.
 */
export function IngredientMatches({
  ingredientId,
  matches,
}: {
  ingredientId: number;
  matches: IngredientProductMatch[];
}) {
  const router = useRouter();
  const toast = useToast();
  const [busy, setBusy] = useState<string | null>(null);

  async function call(path: string, key: string, done: string) {
    setBusy(key);
    try {
      const res = await adminWrite(`/api/admin/ingredients/${ingredientId}${path}`, { method: "POST" });
      if (res.ok) {
        toast.success(done);
        router.refresh();
        return;
      }
      toast.error(
        res.status === 409
          ? "이미 그 상태입니다"
          : res.status === 403
            ? "권한이 없습니다"
            : `실패했습니다 (HTTP ${res.status})`,
      );
    } catch {
      toast.error("서버를 부르지 못했습니다");
    } finally {
      setBusy(null);
    }
  }

  const approved = matches.filter((m) => m.status === "approved").length;
  const suggested = matches.filter((m) => m.status === "suggested").length;

  return (
    <div className="admin-form">
      <div className="admin__section-head admin__section-head--row">
        <span>
          유통 제품 매핑 — 승인 {approved} · 제안 {suggested} · 전체 {matches.length}
        </span>
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => call("/matches/suggest", "suggest", "제안을 다시 만들었습니다")}
          disabled={busy !== null}
        >
          {busy === "suggest" ? "찾는 중…" : "제안 만들기"}
        </button>
      </div>
      <p className="admin-field__hint">
        브랜드 검색어와 별명으로 식약처 신고 제품을 찾습니다. 검색어가 비어 있으면 아무것도 안 나옵니다.
        &ldquo;신고가 있었다&rdquo;까지만 아는 데이터라 재고·판매 중단은 모릅니다.
      </p>

      {matches.length === 0 ? (
        <p className="admin__empty">매핑이 없습니다. 브랜드 검색어를 적고 &ldquo;제안 만들기&rdquo;를 누르세요.</p>
      ) : (
        <ul className="admin__list">
          {matches.map((m) => (
            <li key={m.id}>
              <div className="admin__section-head--row">
                <b>
                  {m.product.nameKo}
                  {m.product.nameEn ? <span className="en"> {m.product.nameEn}</span> : null}
                </b>
                <span className="admin-inline">
                  <span className="admin-field__hint">{label(MATCH_STATUS_LABELS, m.status)}</span>
                  {m.status !== "approved" ? (
                    <button
                      type="button"
                      className="btn btn-primary"
                      disabled={busy !== null}
                      onClick={() => call(`/matches/${m.id}/approve`, `a${m.id}`, "승인했습니다")}
                    >
                      승인<span className="visually-hidden"> — {m.product.nameKo}</span>
                    </button>
                  ) : null}
                  {m.status !== "rejected" ? (
                    <button
                      type="button"
                      className="btn"
                      disabled={busy !== null}
                      onClick={() => call(`/matches/${m.id}/reject`, `r${m.id}`, "거절했습니다")}
                    >
                      거절<span className="visually-hidden"> — {m.product.nameKo}</span>
                    </button>
                  ) : null}
                </span>
              </div>
              <span>
                {m.product.importerOrMaker ?? "수입사 미상"}
                {m.product.originCountry ? ` · ${m.product.originCountry}` : ""} · {m.product.foodType}
                {m.product.lastReportedOn ? ` · 마지막 신고 ${m.product.lastReportedOn}` : " · 신고일 없음"}
              </span>
              <span className="admin-field__hint">
                검색어 &ldquo;{m.matchedKeyword}&rdquo; · 신뢰도 {m.confidence}
                {m.product.manufacturer ? ` · 제조 ${m.product.manufacturer}` : ""}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
