package io.github.yanziki.enterpriseai.knowledge.api;

import io.github.yanziki.enterpriseai.knowledge.DocumentStatus;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/organizations/{organizationSlug}/workspaces/{workspaceSlug}/documents")
public class DocumentController {

    private final DocumentApplicationService documentService;

    public DocumentController(DocumentApplicationService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<UploadAcceptedResponse> createDocument(
            @PathVariable String organizationSlug,
            @PathVariable String workspaceSlug,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String title,
            JwtAuthenticationToken authentication) {
        return ResponseEntity.accepted()
                .body(
                        documentService.createDocument(
                                authentication, organizationSlug, workspaceSlug, file, title));
    }

    @GetMapping
    DocumentPageResponse list(
            @PathVariable String organizationSlug,
            @PathVariable String workspaceSlug,
            @RequestParam(defaultValue = "ACTIVE") DocumentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            JwtAuthenticationToken authentication) {
        return documentService.list(
                authentication, organizationSlug, workspaceSlug, status, page, size);
    }

    @GetMapping("/{documentId}")
    DocumentDetailResponse detail(
            @PathVariable String organizationSlug,
            @PathVariable String workspaceSlug,
            @PathVariable UUID documentId,
            JwtAuthenticationToken authentication) {
        return documentService.detail(authentication, organizationSlug, workspaceSlug, documentId);
    }

    @GetMapping("/{documentId}/versions")
    List<DocumentVersionResponse> versions(
            @PathVariable String organizationSlug,
            @PathVariable String workspaceSlug,
            @PathVariable UUID documentId,
            JwtAuthenticationToken authentication) {
        return documentService.versions(
                authentication, organizationSlug, workspaceSlug, documentId);
    }

    @PostMapping(value = "/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<UploadAcceptedResponse> createVersion(
            @PathVariable String organizationSlug,
            @PathVariable String workspaceSlug,
            @PathVariable UUID documentId,
            @RequestPart("file") MultipartFile file,
            JwtAuthenticationToken authentication) {
        return ResponseEntity.accepted()
                .body(
                        documentService.createVersion(
                                authentication, organizationSlug, workspaceSlug, documentId, file));
    }

    @GetMapping("/{documentId}/versions/{versionId}")
    DocumentVersionResponse version(
            @PathVariable String organizationSlug,
            @PathVariable String workspaceSlug,
            @PathVariable UUID documentId,
            @PathVariable UUID versionId,
            JwtAuthenticationToken authentication) {
        return documentService.version(
                authentication, organizationSlug, workspaceSlug, documentId, versionId);
    }

    @GetMapping("/{documentId}/versions/{versionId}/download")
    ResponseEntity<StreamingResponseBody> download(
            @PathVariable String organizationSlug,
            @PathVariable String workspaceSlug,
            @PathVariable UUID documentId,
            @PathVariable UUID versionId,
            JwtAuthenticationToken authentication) {
        AuthorizedDownload download =
                documentService.download(
                        authentication, organizationSlug, workspaceSlug, documentId, versionId);
        StreamingResponseBody body =
                output -> {
                    try (var stored = download.storedObject()) {
                        stored.content().transferTo(output);
                    }
                };
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(download.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .contentLength(download.storedObject().contentLength())
                .contentType(safeMediaType(download.contentType()))
                .body(body);
    }

    @PostMapping("/{documentId}/archive")
    DocumentDetailResponse archive(
            @PathVariable String organizationSlug,
            @PathVariable String workspaceSlug,
            @PathVariable UUID documentId,
            JwtAuthenticationToken authentication) {
        return documentService.archive(authentication, organizationSlug, workspaceSlug, documentId);
    }

    @PostMapping("/{documentId}/versions/{versionId}/retry")
    DocumentVersionResponse retry(
            @PathVariable String organizationSlug,
            @PathVariable String workspaceSlug,
            @PathVariable UUID documentId,
            @PathVariable UUID versionId,
            JwtAuthenticationToken authentication) {
        return documentService.retry(
                authentication, organizationSlug, workspaceSlug, documentId, versionId);
    }

    private MediaType safeMediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
