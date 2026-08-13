package io.github.yanziki.enterpriseai.answer;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{organizationSlug}/workspaces/{workspaceSlug}/answers")
public class AnswerController {

    private final GroundedAnswerService answerService;

    public AnswerController(GroundedAnswerService answerService) {
        this.answerService = answerService;
    }

    @PostMapping
    AnswerResponse answer(
            @PathVariable String organizationSlug,
            @PathVariable String workspaceSlug,
            @RequestBody(required = false) AnswerRequest request,
            JwtAuthenticationToken authentication) {
        return answerService.answer(authentication, organizationSlug, workspaceSlug, request);
    }
}
