package com.riseup.api;

import com.riseup.domain.NotificationEntity;
import com.riseup.domain.UserEntity;
import com.riseup.health.HealthService;
import com.riseup.repository.NotificationRepository;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SystemController {
    private final HealthService health;
    private final AuthService auth;
    private final NotificationRepository notifications;

    public SystemController(HealthService health, AuthService auth, NotificationRepository notifications) {
        this.health = health;
        this.auth = auth;
        this.notifications = notifications;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return health.payload();
    }

    @GetMapping("/me")
    public Map<String, Object> me(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        UserEntity user = auth.resolveUser(authorization, token);
        return ApiMapper.user(user);
    }

    @GetMapping("/notifications")
    public List<Map<String, Object>> notifications(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token,
            @RequestParam(value = "unread", defaultValue = "false") boolean unread,
            @RequestParam(value = "limit", defaultValue = "30") int limit) {
        int userId = auth.resolveUserId(authorization, token);
        List<NotificationEntity> items =
                unread
                        ? notifications.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                        : notifications.findByUserIdOrderByCreatedAtDesc(userId);
        return items.stream().limit(limit).map(ApiMapper::notification).toList();
    }

    @PostMapping("/notifications/read")
    public Map<String, Object> markRead(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        int userId = auth.resolveUserId(authorization, token);
        List<NotificationEntity> items = notifications.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId);
        for (NotificationEntity n : items) n.setRead(true);
        notifications.saveAll(items);
        return Map.of("marked", items.size());
    }
}
