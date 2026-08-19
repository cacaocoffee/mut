package kr.mut.cocktail.lifecycle

import kr.mut.cocktail.api.CocktailRenamed
import kr.mut.cocktail.domain.Cocktail
import kr.mut.cocktail.repository.CocktailRepository
import kr.mut.common.audit.AuditAction
import kr.mut.common.audit.RejectedAttemptRecorder
import kr.mut.common.web.error.ResourceNotFoundException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 발행 상태를 건드리지 않는 변경 (ISSUE-014).
 *
 * 상태 전이는 `PublishService` 다 (이슈 013) — 게이트와 붙어 있어야 하고,
 * 그쪽에 우회 경로가 없다는 것이 `NFR-D-02` 의 근거다. 여기서 다시 열지 않는다.
 */
@Service
class CocktailLifecycleService(
    private val cocktails: CocktailRepository,
    private val rejected: RejectedAttemptRecorder,
    private val events: ApplicationEventPublisher,
) {

    /**
     * `slug` 변경. **최초 발행 이후에는 반드시 실패한다** (`INV-COCKTAIL-05` · `PRIN-D02`).
     *
     * 성공 경로가 감사에 남지 않는 이유: `draft` 는 아직 노출된 적이 없어 URL 이 아니다.
     * `PRIN-T08` 의 감사 대상 4종에도 없다. **남기는 것은 거부된 시도 쪽**이다 —
     * `NFR-D-04` 가 "변경 이력 0건, 발견 시 즉시 조사"라고 했고, 조사 대상은 시도다.
     *
     * @throws kr.mut.cocktail.domain.SlugLockedException 발행 이후 (422)
     */
    @Transactional
    fun changeSlug(cocktailId: Long, newSlug: String) {
        val cocktail = load(cocktailId)

        if (cocktail.isSlugLocked) {
            // 던지기 **전에** 남긴다. 이 트랜잭션은 곧 롤백되므로 별도 트랜잭션으로 나간다.
            rejected.record(
                entityType = ENTITY_TYPE,
                entityId = cocktail.id,
                action = AuditAction.SLUG_CHANGE_ATTEMPT,
                before = mapOf("slug" to cocktail.slug),
                after = mapOf("slug" to newSlug),
            )
        }

        cocktail.changeSlug(newSlug) // 잠겨 있으면 여기서 던진다
    }

    /**
     * 이름·별칭 변경. 발행 상태는 그대로다.
     *
     * 그래도 [CocktailRenamed] 를 낸다 — 검색어가 이름과 별칭이라 (`R-F2.1-3`)
     * 색인을 갱신하지 않으면 **바뀐 이름으로 찾을 수 없다.**
     * 감사에는 남기지 않는다. `PRIN-T08` 의 4종에 없고, DECISIONS §1.3 이 대상을 넓히지 않았다.
     */
    @Transactional
    fun rename(
        cocktailId: Long,
        nameKo: String,
        nameEn: String,
        aliases: List<String>,
    ) {
        val cocktail = load(cocktailId)

        cocktail.nameKo = nameKo
        cocktail.nameEn = nameEn
        cocktail.aliases = aliases.toTypedArray()

        events.publishEvent(
            CocktailRenamed(
                entityId = cocktail.id,
                slug = cocktail.slug,
                nameKo = cocktail.nameKo,
                nameEn = cocktail.nameEn,
                aliases = aliases,
            ),
        )
    }

    private fun load(id: Long): Cocktail =
        cocktails.findById(id).orElseThrow { ResourceNotFoundException() }

    companion object {
        /** `audit_log.entity_type`. 테이블 이름을 쓴다. */
        const val ENTITY_TYPE = "cocktail"
    }
}
