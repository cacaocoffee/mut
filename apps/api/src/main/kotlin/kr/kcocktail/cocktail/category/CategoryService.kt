package kr.kcocktail.cocktail.category

import org.springframework.stereotype.Service

/**
 * 3축 카테고리 조회 (ISSUE-022).
 *
 * 이슈 022 의 GREEN 범위는 여기까지다 — **카테고리 페이지 렌더링(039)과 사이트맵 생성(039·044)은
 * 프론트 몫**이다. 이 서비스는 그 둘이 읽을 목록을 낸다.
 */
@Service
class CategoryService {

    /**
     * @param includeAll `true` 면 enum 전체(건수 0 포함). 필터 UI 가 쓸 목록이다.
     *   기본값 `false` 는 **발행분이 있는 값만** — `generateStaticParams` 가 빈 페이지를
     *   만들면 안 된다 (DECISIONS §1.11).
     */
    fun categories(includeAll: Boolean): CategoriesResponse = TODO("ISSUE-022 GREEN")
}
