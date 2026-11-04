package com.riseup.insights;

import com.riseup.api.ApiMapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {
    private final RecommendationRepository recommendations;
    private final UserProfileRepository profiles;
    private final UserPreferencesRepository prefsRepo;
    private final Clock clock;

    public RecommendationService(
            RecommendationRepository recommendations,
            UserProfileRepository profiles,
            UserPreferencesRepository prefsRepo,
            Clock clock) {
        this.recommendations = recommendations;
        this.profiles = profiles;
        this.prefsRepo = prefsRepo;
        this.clock = clock;
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
        // Rule-based recommendations
        if (prefs.isCheckinEnabled() && prefs.getCheckinDelayMinutes() > 8) {
            items.add(saveRec(userId, "checkin_delay", "Shorten check-in delay",
                    "Your check-in delay is relatively long; try 7 minutes.",
                    Map.of("checkin_delay_minutes", 7)));
        }
        if (prefs.getEnabledChannels() != null && !prefs.getEnabledChannels().contains("sms")
                && prefs.getEnabledChannels().contains("call")) {
            items.add(saveRec(userId, "enable_sms", "Enable SMS fallback",
                    "Adding SMS gives the agent another path if calls fail.",
                    Map.of("add_channel", "sms")));
        }
        if (items.isEmpty()) {
            items.add(saveRec(userId, "keep_going", "You're on track",
                    "No strong preference changes suggested from current data.",
                    Map.of()));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("source", "rules");
        out.put("message", "Generated " + items.size() + " recommendation(s)");
        return out;
    }

    private RecommendationEntity saveRec(int userId, String type, String title, String rationale, Map<String, Object> change) {
        RecommendationEntity r = new RecommendationEntity();
        r.setUserId(userId);
        r.setType(type);
        r.setTitle(title);
        r.setRationale(rationale);
        r.setChange(change);
        r.setSource("rules");
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
