package kr.mut.common.web.page

import kr.mut.common.web.error.BadRequestException
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * `PageQuery` 파라미터를 쿼리스트링에서 만든다 (SPEC-07 §1.5).
 *
 * 컨트롤러는 `fun list(@SortableBy("name", "abv") page: PageQuery)` 라고만 쓴다.
 * 상한 절삭과 허용목록 검사가 여기 한 곳에 있다.
 */
class PageQueryArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter) =
        parameter.parameterType == PageQuery::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): PageQuery {
        val page = webRequest.intParam("page", PageQuery.DEFAULT_PAGE)
        if (page < 0) throw BadRequestException("page 는 0 이상이어야 합니다")

        val requested = webRequest.intParam("size", PageQuery.DEFAULT_SIZE)
        if (requested < 1) throw BadRequestException("size 는 1 이상이어야 합니다")

        val allowed = parameter.getParameterAnnotation(SortableBy::class.java)?.value.orEmpty().toSet()

        return PageQuery(
            page = page,
            size = requested.coerceAtMost(PageQuery.MAX_SIZE), // 400 이 아니라 절삭
            sort = webRequest.getParameterValues("sort").orEmpty().map { parseSort(it, allowed) },
        )
    }

    private fun parseSort(raw: String, allowed: Set<String>): SortOrder {
        val parts = raw.split(',')
        val property = parts[0].trim()
        val direction = parts.getOrNull(1)?.trim()?.lowercase() ?: "asc"

        if (property !in allowed) {
            // 허용된 목록을 그대로 알려 준다. 무엇이 되는지 모르면 클라이언트가 추측한다.
            throw BadRequestException(
                "정렬할 수 없는 항목입니다: $property (가능: ${allowed.sorted().joinToString(", ")})",
            )
        }
        if (direction !in setOf("asc", "desc")) {
            throw BadRequestException("정렬 방향은 asc 또는 desc 입니다: $direction")
        }
        return SortOrder(property, direction == "asc")
    }

    private fun NativeWebRequest.intParam(name: String, default: Int): Int {
        val raw = getParameter(name) ?: return default
        return raw.toIntOrNull() ?: throw BadRequestException("$name 은 정수여야 합니다: $raw")
    }
}
