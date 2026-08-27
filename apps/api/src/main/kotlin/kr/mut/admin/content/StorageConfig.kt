package kr.mut.admin.content

import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * GCS 클라이언트 빈 (ADR-0011 이미지 업로드).
 *
 * 자격증명을 코드에 두지 않는다 — Cloud Run 위에서는 서비스 계정이 ADC(Application
 * Default Credentials)로 자동으로 붙는다. 로컬·테스트에는 버킷 설정이 없으므로
 * `mut.media.bucket` 이 있을 때만 빈을 만든다 — 없으면 GCS 를 아예 부르지 않는다.
 */
@Configuration
@ConditionalOnProperty(prefix = "mut.media", name = ["bucket"])
class StorageConfig {

    @Bean
    fun storage(): Storage = StorageOptions.getDefaultInstance().service
}
