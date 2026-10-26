package com.meridian.ai.job;

import com.meridian.common.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/jobs")
public class AiJobController {

    private final AiJobService jobs;

    public AiJobController(AiJobService jobs) {
        this.jobs = jobs;
    }

    @GetMapping("/{id}")
    public AiJobView status(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return AiJobView.from(jobs.get(id, CurrentUser.id(jwt)));
    }

    @PostMapping("/{id}/cancel")
    public AiJobView cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return AiJobView.from(jobs.cancel(id, CurrentUser.id(jwt)));
    }
}
