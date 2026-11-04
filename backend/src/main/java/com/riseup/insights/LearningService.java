package com.riseup.insights;

import com.riseup.clock.Clock;
import com.riseup.domain.AlarmLogEntity;
import com.riseup.domain.UserProfileEntity;
import com.riseup.repository.AlarmLogRepository;
import com.riseup.repository.UserProfileRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningService {
    private final UserProfileRepository profiles;
    private final AlarmLogRepository alarmLogs;
    private final Clock clock;

    public LearningService(UserProfileRepository profiles, AlarmLogRepository alarmLogs, Clock clock) {
        this.profiles = profiles;
        this.alarmLogs = alarmLogs;
        this.clock = clock;
    }

    @Transactional
    public UserProfileEntity analyzeBehavior(int userId) {
        UserProfileEntity profile =
                profiles.findById(userId).orElseGet(() -> {
                    UserProfileEntity p = new UserProfileEntity();
                    p.setUserId(userId);
                    return p;
                });
        List<AlarmLogEntity> logs = alarmLogs.findByUserIdOrderByCreatedAtDesc(userId);
        Map<String, Object> verification = new HashMap<>();
        long verified = logs.stream().filter(l -> "verified".equals(l.getEvent())).count();
        long failed = logs.stream().filter(l -> "failed_attempt".equals(l.getEvent())).count();
        verification.put("verified", verified);
        verification.put("failed_attempts", failed);
        profile.setVerificationStats(verification);
        profile.setDataStatus(logs.size() < 5 ? "insufficient" : "ready");
        profile.setUpdatedAt(clock.now());
        return profiles.save(profile);
    }
}
