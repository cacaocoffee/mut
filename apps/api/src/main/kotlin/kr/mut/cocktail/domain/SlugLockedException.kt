package kr.mut.cocktail.domain

import kr.mut.common.web.error.DomainViolationException
import kr.mut.common.web.error.Violation
import kr.mut.common.web.error.ViolationCode

/**
 * `INV-COCKTAIL-05` — 최초 발행 이후 `slug` 변경 시도 (`PRIN-D02`, `FR-COCKTAIL-014`).
 *
 * `422` 로 나간다. `violations` 를 실어 나르므로 클라이언트가 문구가 아니라 코드로 분기한다.
 *
 * 시도한 값을 [attempted] 에 들고 있는 이유는 **감사에 남기기 위해서**다 —
 * `NFR-D-04` 가 "슬러그 변경 이력 0건, 발견 시 즉시 조사"를 요구하는데,
 * 무엇으로 바꾸려 했는지가 없으면 조사할 것이 없다.
 */
class SlugLockedException(
    val current: String,
    val attempted: String,
) : DomainViolationException(
    listOf(
        Violation.of(
            ViolationCode.INV_COCKTAIL_05,
            "발행된 칵테일의 주소는 바꿀 수 없습니다. 이미 공개된 링크가 끊깁니다.",
            "slug",
        ),
    ),
    "slug 는 최초 발행 이후 불변입니다 ($current → $attempted)",
)
