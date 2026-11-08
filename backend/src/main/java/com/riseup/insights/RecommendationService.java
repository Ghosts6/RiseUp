package com.riseup.insights;

import com.riseup.ai.LLMClient;
import com.riseup.ai.LLMResponse;
import com.riseup.ai.PromptBuilder;
import com.riseup.ai.ResponseParser;
import com.riseup.clock.Clock;
import com.riseup.domain.RecommendationEntity;
import com.riseup.domain.UserPreferencesEntity;
import com.riseup.domain.UserProfileEntity;
import com.riseup.error.NotFoundException;
import com.riseup.repository.RecommendationRepository;
import com.riseup.repository.UserPreferencesRepository;
import com.riseup.repository.UserProfileRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {
    private static final Set<String> SAFE_CHANNELS = Set.of("push", "sms", "call", "alexa", "email");

    private final RecommendationRepository recommendations;
    private final UserProfileRepository profiles;
    private final UserPreferencesRepository prefsRepo;
    private final Clock clock;
    private final LLMClient llm;
    private final PromptBuilder prompts = new PromptBuilder();
    private final ResponseParser parser = new ResponseParser();

    public RecommendationService(
            RecommendationRepository recommendations,
            UserProfileRepository profiles,
            UserPreferencesRepository prefsRepo,
            Clock clock,
            LLMClient llm) {
        this.recommendations = recommendations;
        this.profiles = profiles;
        this.prefsRepo = prefsRepo;
        this.clock = clock;
        this.llm = llm;
    }

    public List<RecommendationEntity> listPending(int userId) {
        return recommendations.findByUserIdAndStatusOrderByCreatedAtDesc(userId, "pending");
    }

    @Transactional
    public Map<String, Object> generate(int userId) {
        UserProfileEntity profile =
                profiles.findById(userId).orElseGet(() -> {
                    UserProfileEntity p = new UserProfileEntity();
                    p.setUserId(userId);
                    p.setUpdatedAt(clock.now());
                    return profiles.save(p);
                });
        UserPreferencesEntity prefs =
                prefsRepo.findById(userId).orElseGet(() -> {
                    UserPreferencesEntity p = new UserPreferencesEntity();
                    p.setUserId(userId);
                    return prefsRepo.save(p);
                });

        List<RecommendationEntity> items = new ArrayList<>();
        String source = "rules";
        boolean fallbackUsed = false;

        try {
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("profile", Map.of(
                    "data_status", profile.getDataStatus(),
                    "escalation_stats", profile.getEscalationStats(),
                    "task_completion_rates", profile.getTaskCompletionRates(),
                    "verification_stats", profile.getVerificationStats(),
                    "alarm_patterns", profile.getAlarmPatterns()));
            inputs.put(
                    "preferences",
                    Map.of(
                            "enabled_channels", prefs.getEnabledChannels(),
                            "checkin_enabled", prefs.isCheckinEnabled(),
                            "checkin_delay_minutes", prefs.getCheckinDelayMinutes(),
                            "escalation_order", prefs.getEscalationOrder()));
            LLMResponse resp =
                    llm.sendRequest(prompts.buildRecommendPrompt(inputs), "recommend", prompts.system);
            List<Map<String, Object>> parsed = parser.parseRecommendations(resp.text());
            for (Map<String, Object> row : parsed) {
                RecommendationEntity saved = saveValidated(userId, row, resp.decidedBy());
                if (saved != null) {
                    items.add(saved);
                }
            }
            if (!items.isEmpty()) {
                source = resp.decidedBy();
            }
        } catch (Exception e) {
            fallbackUsed = true;
        }

        if (items.isEmpty()) {
            items.addAll(ruleBased(userId, prefs));
            source = "rules";
            fallbackUsed = true;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("source", source);
        out.put("fallback_used", fallbackUsed);
        out.put("message", "Generated " + items.size() + " recommendation(s)");
        return out;
    }

    private List<RecommendationEntity> ruleBased(int userId, UserPreferencesEntity prefs) {
        List<RecommendationEntity> items = new ArrayList<>();
        if (prefs.isCheckinEnabled() && prefs.getCheckinDelayMinutes() > 8) {
            items.add(saveRec(userId, "checkin_delay", "Shorten check-in delay",
                    "Your check-in delay is relatively long; try 7 minutes.",
                    Map.of("checkin_delay_minutes", 7), "rules"));
        }
        if (prefs.getEnabledChannels() != null
                && !prefs.getEnabledChannels().contains("sms")
                && prefs.getEnabledChannels().contains("call")) {
            items.add(saveRec(userId, "enable_sms", "Enable SMS fallback",
                    "Adding SMS gives the agent another path if calls fail.",
                    Map.of("add_channel", "sms"), "rules"));
        }
        if (items.isEmpty()) {
            items.add(saveRec(userId, "keep_going", "You're on track",
                    "No strong preference changes suggested from current data.",
                    Map.of(), "rules"));
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private RecommendationEntity saveValidated(int userId, Map<String, Object> row, String source) {
        String type = String.valueOf(row.getOrDefault("type", "general"));
        String title = String.valueOf(row.getOrDefault("title", "Suggestion"));
        String rationale = String.valueOf(row.getOrDefault("rationale", ""));
        Object changeObj = row.get("change");
        Map<String, Object> change =
                changeObj instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : Map.of();
        if (change.containsKey("disable_all_channels") || change.containsKey("enabled_channels")) {
            Object channels = change.get("enabled_channels");
            if (channels instanceof List<?> list && list.isEmpty()) {
                return null;
            }
        }
        if (change.containsKey("add_channel")) {
            String ch = String.valueOf(change.get("add_channel")).toLowerCase();
            if (!SAFE_CHANNELS.contains(ch)) {
                return null;
            }
        }
        if (change.containsKey("checkin_delay_minutes")) {
            Object delay = change.get("checkin_delay_minutes");
            if (!(delay instanceof Number n) || n.intValue() < 3 || n.intValue() > 60) {
                return null;
            }
        }
        return saveRec(userId, type, title, rationale, change, source);
    }

    private RecommendationEntity saveRec(
            int userId, String type, String title, String rationale, Map<String, Object> change, String source) {
        RecommendationEntity r = new RecommendationEntity();
        r.setUserId(userId);
        r.setType(type);
        r.setTitle(title);
        r.setRationale(rationale);
        r.setChange(change);
        r.setSource(source == null ? "rules" : source);
        r.setStatus("pending");
        r.setCreatedAt(clock.now());
        return recommendations.save(r);
    }

    @Transactional
    public RecommendationEntity accept(int userId, int recId) {
        RecommendationEntity r =
                recommendations.findByIdAndUserId(recId, userId)
                        .orElseThrow(() -> new NotFoundException("Recommendation " + recId + " not found"));
        r.setStatus("accepted");
        UserPreferencesEntity prefs = prefsRepo.findById(userId).orElseThrow();
        Object delay = r.getChange().get("checkin_delay_minutes");
        if (delay instanceof Number n) prefs.setCheckinDelayMinutes(n.intValue());
        Object add = r.getChange().get("add_channel");
        if (add != null) {
            List<String> channels = new ArrayList<>(prefs.getEnabledChannels());
            if (!channels.contains(String.valueOf(add))) channels.add(String.valueOf(add));
            prefs.setEnabledChannels(channels);
        }
        prefsRepo.save(prefs);
        return recommendations.save(r);
    }

    @Transactional
    public RecommendationEntity dismiss(int userId, int recId) {
        RecommendationEntity r =
                recommendations.findByIdAndUserId(recId, userId)
                        .orElseThrow(() -> new NotFoundException("Recommendation " + recId + " not found"));
        r.setStatus("dismissed");
        return recommendations.save(r);
    }
}
