package kr.mut.cocktail.publish

import kr.mut.cocktail.api.CocktailIdentity
import kr.mut.cocktail.api.PublishInspectionFacade
import kr.mut.cocktail.domain.CocktailInvariants
import kr.mut.cocktail.domain.CocktailStatus
import kr.mut.cocktail.repository.CocktailRepository
import kr.mut.common.web.error.Violation
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [PublishInspectionFacade] 의 구현. **새 규칙을 쓰지 않는다** (이슈 016 RED 6).
 *
 * 판정은 [PublishService.inspect] 에 그대로 넘긴다 — 그쪽이 발행 시점과 **똑같은**
 * 후보를 조립하고 **똑같은** [kr.mut.cocktail.api.PublishGate] 를 부른다.
 * 여기서 한 줄이라도 다르게 조립하면 배치와 발행이 서로 다른 것을 보게 되고,
 * `NFR-D-02` 가 잡아야 할 어긋남을 배치 자신이 만들어 낸다.
 */
@Service
class PublishInspectionService(
    private val cocktails: CocktailRepository,
    private val publish: PublishService,
) : PublishInspectionFacade {

    @Transactional(readOnly = true)
    override fun publishedIds(): List<Long> =
        cocktails.findByStatusSlug(CocktailStatus.PUBLISHED.slug).map { it.id }

    override fun inspectGate(cocktailId: Long): List<Violation> = publish.inspect(cocktailId)

    @Transactional(readOnly = true)
    override fun inspectInvariants(cocktailId: Long): List<Violation> =
        cocktails.findById(cocktailId)
            .map { CocktailInvariants.check(it) }
            .orElse(emptyList())

    @Transactional(readOnly = true)
    override fun identify(cocktailId: Long): CocktailIdentity? =
        cocktails.findById(cocktailId)
            .map { CocktailIdentity(it.id, it.slug, it.nameKo) }
            .orElse(null)
}
