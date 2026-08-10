package probe.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import kr.kcocktail.common.entity.BaseEntity

/**
 * `BaseEntity` 검증용 프로브다. 도메인 엔티티가 아니다 (ISSUE-002).
 *
 * ## 왜 `kr.kcocktail` 밖에 있나
 *
 * 앱 패키지 안에 두면 **모든 `@SpringBootTest` 의 엔티티 스캔에 걸린다.**
 * `ddl-auto: validate` 가 `entity_probe.probe` 를 찾다가 실패하고,
 * 이 프로브와 아무 상관 없는 테스트들이 줄줄이 빨개진다 (ISSUE-003 에서 실제로 그랬다).
 *
 * 여기 두면 [BaseEntityProbeTest] 가 `@EntityScan` 으로 명시할 때만 보인다.
 *
 * SPEC-06 §1 을 전부 지킨 모양이라 규약의 참조 구현이기도 하다.
 */
@Entity
@Table(name = BaseEntityProbe.TABLE, schema = BaseEntityProbe.SCHEMA)
class BaseEntityProbe(
    @Column(name = "label", nullable = false)
    var label: String = "",
) : BaseEntity() {
    companion object {
        const val SCHEMA = "entity_probe"
        const val TABLE = "probe"
    }
}
