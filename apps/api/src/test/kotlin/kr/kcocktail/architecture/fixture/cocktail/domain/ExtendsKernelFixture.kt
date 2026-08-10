package kr.kcocktail.architecture.fixture.cocktail.domain

import kr.kcocktail.architecture.fixture.common.entity.KernelBaseFixture

/**
 * **위반이 아니다.** 실체 테이블을 만드는 모든 이슈가 이 모양을 쓴다 (SPEC-06 §1.2).
 *
 * 규칙 1~3 이 `common` 을 예외로 두지 않으면 여기가 "api 경유가 아니다"로 잡히고,
 * 그 순간 경계 테스트가 정상 설계를 막는 물건이 된다.
 */
@Suppress("unused")
class ExtendsKernelFixture : KernelBaseFixture()
