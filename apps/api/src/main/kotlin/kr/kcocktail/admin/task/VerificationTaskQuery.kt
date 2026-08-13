package kr.kcocktail.admin.task

import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * 검증 태스크 한 건 (ISSUE-028 · `FR-ADMIN-004`).
 *
 * ## `adminPath` 를 서버가 만든다
 *
 * 프론트가 `entityType` → 경로 매핑을 따로 들면 어긋난다. 엔티티 종류가 늘 때
 * 서버는 태스크를 만들고 프론트는 링크를 못 만드는 상태가 생기는데, 그러면
 * **큐에는 보이는데 갈 수가 없다** — 큐의 쓸모가 "고치러 가는 것" 하나뿐인데 그게 막힌다.
 */
data class VerificationTaskItem(
    val id: Long,
    val taskType: String,
    val entityType: String,
    val entityId: Long,

    /** `INV-COCKTAIL-02` · `GATE-COCKTAIL-01` 같은 SPEC-02 ID. */
    val code: String,

    /** 어느 필드가 왜 걸렸는지. 배치가 넣은 그대로다. */
    val detail: JsonNode?,

    /** `"/api/v1/admin/cocktails/123"` — UI 가 그대로 링크한다. */
    val adminPath: String?,

    val status: String,
    val detectedAt: Instant,
    val resolvedAt: Instant?,

    /** 배치가 자동으로 닫았으면 `null` 이다 (RED 22). 사람이 닫았으면 그 사람 id. */
    val resolvedBy: Long?,

    /** `dismissed` 일 때의 사유. 무시한 이유가 없으면 무시한 것이 아니라 잊은 것이다. */
    val resolution: String?,
)

/**
 * 해소 요청 (SPEC-07 §2.7).
 *
 * @param dismiss `true` 면 "고치지 않고 넘긴다". **사유가 필수다** — 이슈 028 RED 18.
 *   무시를 아예 막으면 오탐 하나가 큐를 영원히 더럽히고, 사유 없이 열어 두면
 *   큐가 조용히 비워진다. 남길 것을 요구하는 쪽이 둘 다 피한다.
 */
data class ResolveTaskRequest(
    val dismiss: Boolean = false,

    @field:Size(max = 500)
    @Schema(description = "dismiss=true 면 필수. 왜 고치지 않고 넘기는지.")
    val reason: String? = null,
)
