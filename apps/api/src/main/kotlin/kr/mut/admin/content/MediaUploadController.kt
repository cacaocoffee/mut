package kr.mut.admin.content

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import io.swagger.v3.oas.annotations.Operation
import jakarta.servlet.http.HttpServletRequest
import kr.mut.common.security.authz.Action
import kr.mut.common.web.ApiPaths
import kr.mut.common.web.error.BadRequestException
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * 이미지 업로드 (ADR-0011 · ADR-0007 3단계).
 *
 * 어드민 편집기가 사진 파일을 올리면 GCS 에 넣고 공개 URL 을 돌려준다.
 * 저장소를 R2 대신 GCS 로 둔 이유는 ADR-0007 개정(2026-08-27)에 있다 — 같은 GCP
 * 프로젝트라 계정을 하나 더 만들지 않고, Cloud Run 서비스 계정이 자격증명(ADC)으로 붙는다.
 *
 * 어드민 컨트롤러라 `kr.mut.admin.content` 에 둔다(AdminArticleController 옆). content
 * 모듈에 두면 admin 의 [AdminActor] 를 참조해 모듈 경계(RED3·RED6)를 넘는다.
 *
 * 이 컨트롤러는 인증과 업로드만 한다 — 미디어 테이블(SPEC-06)은 아직 없어 URL 만 돌려준다.
 */
@RestController
@RequestMapping("${ApiPaths.ADMIN}/media")
@ConditionalOnProperty(prefix = "mut.media", name = ["bucket"])
class MediaUploadController(
    private val actor: AdminActor,
    private val storage: Storage,
    @Value("\${mut.media.bucket:}") private val bucket: String,
) {

    /** 허용 확장자 — 이미지만. 실행 파일·문서가 올라오는 것을 막는다. */
    private val allowed = mapOf(
        "image/webp" to "webp",
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/avif" to "avif",
    )

    @PostMapping(consumes = ["multipart/form-data"])
    @Operation(summary = "이미지 업로드", description = "editor 이상. GCS 에 올리고 공개 URL 을 준다.")
    fun upload(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(value = "slug", required = false) slug: String?,
        http: HttpServletRequest,
    ): Map<String, String> {
        actor.require(http, Action.WRITE_CONTENT)
        if (bucket.isBlank()) throw BadRequestException("미디어 버킷이 설정되지 않았습니다")

        val ext = allowed[file.contentType]
            ?: throw BadRequestException("이미지 파일만 올릴 수 있습니다 (webp·jpg·png·avif)")
        if (file.isEmpty) throw BadRequestException("빈 파일입니다")

        // 아티클 slug 아래에 담는다. 파일명은 안 믿는다 — 임의 문자열로 이름을 짓는다.
        val folder = (slug ?: "misc").replace(Regex("[^a-z0-9-]"), "").ifBlank { "misc" }
        val name = "articles/$folder/${randomKey()}.$ext"

        val blob = BlobInfo.newBuilder(BlobId.of(bucket, name))
            .setContentType(file.contentType)
            // 이미지는 안 바뀐다 — 이름이 바뀌므로 한 해 캐시한다.
            .setCacheControl("public, max-age=31536000, immutable")
            .build()
        storage.create(blob, file.bytes)

        return mapOf("url" to "https://storage.googleapis.com/$bucket/$name")
    }

    /** 파일명 충돌을 피하는 임의 키. 시각을 안 쓰는 이유는 테스트가 시계를 못 고정해서다. */
    private fun randomKey(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..16).map { chars.random() }.joinToString("")
    }
}
