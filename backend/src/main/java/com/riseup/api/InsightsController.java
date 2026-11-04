package com.riseup.api;

import com.riseup.agent.AgentController;
import com.riseup.domain.RecommendationEntity;
import com.riseup.domain.UserProfileEntity;
import com.riseup.error.ValidationFailedException;
import com.riseup.insights.AnalyticsService;
import com.riseup.insights.LearningService;
import com.riseup.insights.RecommendationService;
import com.riseup.repository.UserProfileRepository;
import com.riseup.time.TimeUtil;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class InsightsController {
    private final UserProfileRepository profiles;
    private final LearningService learning;
    private final AnalyticsService analytics;
    private final RecommendationService recommendations;
    private final AgentController agent;
    private final AuthService auth;

    public InsightsController(
            UserProfileRepository profiles,
            LearningService learning,
            AnalyticsService analytics,
            RecommendationService recommendations,
            AgentController agent,
            AuthService auth) {
        this.profiles = profiles;
        this.learning = learning;
        this.analytics = analytics;
        this.recommendations = recommendations;
        this.agent = agent;
        this.auth = auth;
    }

    @GetMapping("/profile")
    public Map<String, Object> profile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        int userId = auth.resolveUserId(authorization, token);
        UserProfileEntity p =
                profiles.findById(userId).orElseGet(() -> {
                    UserProfileEntity n = new UserProfileEntity();
                    n.setUserId(userId);
                    return profiles.save(n);
                });
        return ApiMapper.profile(p);
    }

    @PostMapping("/profile/recompute")
    public Map<String, Object> recompute(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        return ApiMapper.profile(learning.analyzeBehavior(auth.resolveUserId(authorization, token)));
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token,
            @RequestParam(defaultValue = "30d") String range) {
        try {
            int days = TimeUtil.parseRange(range);
            return analytics.getStats(auth.resolveUserId(authorization, token), days);
        } catch (ValidationFailedException e) {
            throw e;
        }
    }

    @GetMapping("/recommendations")
    public List<Map<String, Object>> listRecs(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        return recommendations.listPending(auth.resolveUserId(authorization, token)).stream()
                .map(ApiMapper::recommendation)
                .toList();
    }

    @PostMapping("/recommendations/generate")
    public Map<String, Object> generate(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        var result = agent.run("recommend", Map.of("user_id", auth.resolveUserId(authorization, token)));
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.getOutput();
        @SuppressWarnings("unchecked")
        List<RecommendationEntity> items = (List<RecommendationEntity>) output.get("items");
        return Map.of(
                "items", items.stream().map(ApiMapper::recommendation).toList(),
                "source", output.get("source"),
                "message", output.get("message"));
    }

    @PostMapping("/recommendations/{recId}/accept")
    public Map<String, Object> accept(
            @PathVariable int recId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        return ApiMapper.recommendation(recommendations.accept(auth.resolveUserId(authorization, token), recId));
    }

    @PostMapping("/recommendations/{recId}/dismiss")
    public Map<String, Object> dismiss(
            @PathVariable int recId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        return ApiMapper.recommendation(recommendations.dismiss(auth.resolveUserId(authorization, token), recId));
    }
}
