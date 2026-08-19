package kr.mut.cocktail.internal

import kr.mut.cocktail.api.AdminCocktailResponse
import kr.mut.cocktail.api.CocktailAdminFacade
import kr.mut.cocktail.api.CreateCocktailRequest
import kr.mut.cocktail.api.UpdateCocktailRequest
import kr.mut.cocktail.domain.Cocktail
import kr.mut.cocktail.publish.PublishService
import kr.mut.cocktail.repository.CocktailRepository
import kr.mut.common.taxonomy.BaseSpirit
import kr.mut.common.taxonomy.FlavorKey
import kr.mut.common.taxonomy.Slugged
import kr.mut.common.taxonomy.StyleKey
import kr.mut.common.taxonomy.SweetLevel
import kr.mut.common.taxonomy.Technique
import kr.mut.common.web.error.BadRequestException
import kr.mut.common.web.error.ConflictException
import kr.mut.common.web.error.ResourceNotFoundException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 어드민 칵테일 CRUD (ISSUE-025 · `FR-ADMIN-001`·`002`).
 *
 * ## 발행을 여기서 하지 않는다
 *
 * 상태 전이는 `PublishService` 다 (이슈 013·014). 이 서비스가 `status` 를 건드리면
 * 게이트를 우회하는 두 번째 길이 생기고, `NFR-D-02`("게이트를 우회한 published 0건")를
 * 지킬 방법이 없어진다.
 *
 * ## 게이트 로직도 여기 없다
 *
 * 컨트롤러는 `PublishService.publish` 를 부르고, 실패는 `DomainViolationException` 으로
 * 올라와 이슈 003 의 핸들러가 **422 + violations 전부**로 옮긴다 (`FR-ADMIN-003`).
 * 게이트를 다시 구현하면 어드민과 배치 검증(016)이 다른 답을 낸다.
 */
@Service
class CocktailAdminService(
    private val cocktails: CocktailRepository,
    private val publish: PublishService,
) : CocktailAdminFacade {

    @Transactional
    override fun create(request: CreateCocktailRequest): AdminCocktailResponse {
        if (cocktails.existsBySlug(request.slug)) {
            throw ConflictException("이미 쓰이는 슬러그입니다: ${request.slug}")
        }

        val cocktail = Cocktail(
            slug = request.slug,
            nameKo = request.nameKo,
            nameEn = request.nameEn,
            summary = request.summary,
            baseSpiritSlug = lookup(request.baseSpirit, "baseSpirit", BaseSpirit.entries).slug,
            stylePrimarySlug = lookup(request.stylePrimary, "stylePrimary", StyleKey.entries).slug,
            methodSlug = lookup(request.method, "method", Technique.entries).slug,
            sweetnessSlug = lookup(request.sweetness, "sweetness", SweetLevel.entries).slug,
            glassType = request.glassType,
            aliases = request.aliases.toTypedArray(),
            tastingNote = request.tastingNote,
            story = request.story,
            isClassic = request.isClassic,
            prepTimeMin = request.prepTimeMin,
            abvOverride = request.abvOverride,
            originYear = request.originYear,
            originPlace = request.originPlace,
            originCreator = request.originCreator,
        )

        applyAxes(cocktail, request.styles, request.aromaTags, request.stylePrimary)
        return save(cocktail).toResponse()
    }

    /**
     * `null` 인 필드는 건드리지 않는다.
     *
     * `status` · `publishedAt` 은 요청 타입에 아예 없다 (RED 33·35) —
     * **타입에 없으면 우회할 수 없다.** 서비스에서 걸러 내는 것보다 강하다.
     */
    @Transactional
    override fun update(id: Long, request: UpdateCocktailRequest): AdminCocktailResponse {
        val cocktail = load(id)

        // 잠겨 있으면 엔티티가 던진다 (INV-COCKTAIL-05). 거부된 시도의 감사는 이슈 014 다
        request.slug?.takeIf { it != cocktail.slug }?.let { cocktail.changeSlug(it) }

        request.nameKo?.let { cocktail.nameKo = it }
        request.nameEn?.let { cocktail.nameEn = it }
        request.summary?.let { cocktail.summary = it }
        request.glassType?.let { cocktail.glassType = it }
        request.aliases?.let { cocktail.aliases = it.toTypedArray() }
        request.tastingNote?.let { cocktail.tastingNote = it }
        request.story?.let { cocktail.story = it }
        request.isClassic?.let { cocktail.isClassic = it }
        request.prepTimeMin?.let { cocktail.prepTimeMin = it }
        request.abvOverride?.let { cocktail.abvOverride = it }

        request.baseSpirit?.let { cocktail.baseSpirit = lookup(it, "baseSpirit", BaseSpirit.entries) }
        request.method?.let { cocktail.method = lookup(it, "method", Technique.entries) }
        request.sweetness?.let { cocktail.sweetness = lookup(it, "sweetness", SweetLevel.entries) }

        val stylePrimary = request.stylePrimary ?: cocktail.stylePrimary.slug
        applyAxes(cocktail, request.styles, request.aromaTags, stylePrimary)

        return save(cocktail).toResponse()
    }

    /**
     * **DTO 로 바꿔서 돌려준다.** 엔티티를 트랜잭션 밖으로 내보내면 컨트롤러가
     * 지연 컬렉션(`styles`·`aromaTags`)을 건드리는 순간 터진다 —
     * `open-in-view: false` 라 세션이 이미 닫혀 있다.
     *
     * 생성 직후에는 방금 채운 컬렉션이 메모리에 있어 **우연히** 동작한다.
     * 조회에서만 터지는 종류라 눈으로는 안 잡힌다.
     */
    @Transactional(readOnly = true)
    override fun find(id: Long): AdminCocktailResponse = load(id).toResponse()

    /**
     * 상태 전이는 **전부 `PublishService` 에 넘긴다** (이슈 013·014).
     *
     * 여기서 status 를 건드리면 게이트를 우회하는 두 번째 길이 생기고,
     * `NFR-D-02`("게이트를 우회한 published 0건")를 지킬 방법이 없어진다.
     */
    @Transactional
    override fun publish(id: Long): AdminCocktailResponse {
        publish.publish(id)
        return load(id).toResponse()
    }

    @Transactional
    override fun unpublish(id: Long): AdminCocktailResponse {
        publish.unpublish(id)
        return load(id).toResponse()
    }

    @Transactional
    override fun archive(id: Long): AdminCocktailResponse {
        publish.archive(id)
        return load(id).toResponse()
    }

    // ── 조립 ───────────────────────────────────────────────────────────────

    /**
     * 스타일·향 태그를 맞춘다.
     *
     * `stylePrimary` 를 `styles` 에 **자동으로 넣지 않는다.** `INV-COCKTAIL-03` 은
     * 복합 FK 로 DB 가 강제하는데, 여기서 조용히 채워 주면 에디터가 빠뜨린 사실을
     * 영영 모른다 — 어드민 화면(SCREENS-06)이 알려 줘야 할 것을 서버가 덮는 셈이다.
     */
    private fun applyAxes(
        cocktail: Cocktail,
        styles: List<String>?,
        aromaTags: List<String>?,
        stylePrimary: String,
    ) {
        styles?.let {
            cocktail.stylePrimary = lookup(stylePrimary, "stylePrimary", StyleKey.entries)
            cocktail.setStyles(it.map { slug -> lookup(slug, "styles", StyleKey.entries) }.toSet())
        }
        aromaTags?.let {
            cocktail.setAromaTags(it.map { slug -> lookup(slug, "aromaTags", FlavorKey.entries) }.toSet())
        }
    }

    /**
     * DB 제약 위반을 409 로 옮긴다.
     *
     * `INV-COCKTAIL-03`(복합 FK) · `INV-COCKTAIL-06`(무알콜 ⟺ abv 0) 은 DB 가 막는다.
     * 여기서 미리 세어 보지 않는 이유는 `PRIN-T05` 다 — 앱이 검사하고 DB 도 검사하면
     * 두 규칙이 갈릴 수 있고, 갈리는 순간 어느 쪽이 맞는지 알 수 없다.
     */
    private fun save(cocktail: Cocktail): Cocktail = try {
        cocktails.saveAndFlush(cocktail)
    } catch (e: DataIntegrityViolationException) {
        throw ConflictException("도메인 제약을 어겼습니다: ${e.mostSpecificCause.message}")
    }

    private fun load(id: Long): Cocktail =
        cocktails.findById(id).orElseThrow { ResourceNotFoundException() }

    /** 모르는 슬러그는 **400** 이다. 조용히 무시하면 에디터가 잘못 저장된 줄 모른다. */
    private fun <T : Slugged> lookup(slug: String, axis: String, all: List<T>): T =
        all.firstOrNull { it.slug == slug }
            ?: throw BadRequestException(
                "알 수 없는 $axis 값입니다: $slug (가능: ${all.joinToString(", ") { it.slug }})",
            )
}

/**
 * **트랜잭션 안에서만 부른다.** `styles` · `aromaTags` 가 지연 컬렉션이라
 * 밖에서 부르면 `open-in-view: false` 와 만나 터진다.
 */
private fun Cocktail.toResponse() = AdminCocktailResponse(
    id = id,
    slug = slug,
    nameKo = nameKo,
    nameEn = nameEn,
    summary = summary,
    status = status.slug,
    publishedAt = publishedAt,
    baseSpirit = baseSpirit.slug,
    stylePrimary = stylePrimary.slug,
    styles = styles.map { it.slug }.sorted(),
    method = method.slug,
    sweetness = sweetness.slug,
    aromaTags = aromaTags.map { it.slug }.sorted(),
    glassType = glassType,
    aliases = aliases.toList(),
    // 공개 응답은 표시값 하나뿐이다. 어드민만 나눠 본다 (DECISIONS §1.5)
    abvCalculated = abvCalculated,
    abvOverride = abvOverride,
    tastingNote = tastingNote,
    story = story,
    isClassic = isClassic,
    prepTimeMin = prepTimeMin,
)
