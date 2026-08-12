package io.github.yanziki.enterpriseai.retrieval.search;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{organizationSlug}/workspaces/{workspaceSlug}/retrieval")
public class RetrievalController {

    private final RetrievalSearchService searchService;

    public RetrievalController(RetrievalSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/capabilities")
    RetrievalCapabilitiesResponse capabilities(
            @PathVariable String organizationSlug,
            @PathVariable String workspaceSlug,
            JwtAuthenticationToken authentication) {
        return searchService.capabilities(authentication, organizationSlug, workspaceSlug);
    }

    @PostMapping("/search")
    RetrievalSearchResponse search(
            @PathVariable String organizationSlug,
            @PathVariable String workspaceSlug,
            @RequestBody(required = false) RetrievalSearchRequest request,
            JwtAuthenticationToken authentication) {
        return searchService.search(authentication, organizationSlug, workspaceSlug, request);
    }
}
