package io.github.yanziki.enterpriseai.shared.system;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemStatusController {

    private final String version;

    public SystemStatusController(@Value("${application.version}") String version) {
        this.version = version;
    }

    @GetMapping("/status")
    SystemStatusResponse status() {
        return new SystemStatusResponse("UP", "enterprise-ai-api", version);
    }

    record SystemStatusResponse(String status, String service, String version) {}
}
