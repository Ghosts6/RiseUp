package com.riseup.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseup.agent.AgentController;
import com.riseup.api.dto.Dtos;
import com.riseup.domain.ReminderEntity;
import com.riseup.domain.UserEntity;
import com.riseup.reminder.ReminderControllerService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {
    private final ReminderControllerService reminders;
    private final AgentController agent;
    private final AuthService auth;
    private final ObjectMapper mapper = new ObjectMapper();

    public ReminderController(ReminderControllerService reminders, AgentController agent, AuthService auth) {
        this.reminders = reminders;
        this.agent = agent;
        this.auth = auth;
    }

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String priority) {
        UserEntity u = auth.resolveUser(authorization, token);
        return reminders.list(u.getId(), status, category, priority).stream()
                .map(r -> ApiMapper.reminder(r, u.getTimezone()))
                .toList();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Dtos.ReminderIn body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token,
            @RequestParam(defaultValue = "form") String source) {
        UserEntity u = auth.resolveUser(authorization, token);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = mapper.convertValue(body, Map.class);
        data.entrySet().removeIf(e -> e.getValue() == null);
        ReminderEntity reminder = reminders.create(u.getId(), data, "nl".equals(source) ? "nl" : "form");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiMapper.reminder(reminder, u.getTimezone()));
    }

    @PostMapping("/parse")
    public Map<String, Object> parse(
            @RequestBody Dtos.ParseIn body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        UserEntity u = auth.resolveUser(authorization, token);
        var result = agent.run("parse_reminder", Map.of("user_id", u.getId(), "text", body.text));
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) result.getOutput();
        return out;
    }

    @GetMapping("/priority-preview")
    public Map<String, Object> priorityPreview(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        UserEntity u = auth.resolveUser(authorization, token);
        Map<String, Object> preview = reminders.priorityPreview(u.getId());
        @SuppressWarnings("unchecked")
        List<ReminderEntity> order = (List<ReminderEntity>) preview.get("order");
        Map<String, Object> out = new LinkedHashMap<>(preview);
        out.put("order", order.stream().map(r -> ApiMapper.reminder(r, u.getTimezone())).toList());
        return out;
    }

    @GetMapping("/{reminderId}")
    public Map<String, Object> get(
            @PathVariable int reminderId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        UserEntity u = auth.resolveUser(authorization, token);
        return ApiMapper.reminder(reminders.get(u.getId(), reminderId), u.getTimezone());
    }

    @PatchMapping("/{reminderId}")
    public Map<String, Object> update(
            @PathVariable int reminderId,
            @RequestBody Dtos.ReminderIn body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token,
            @RequestParam(defaultValue = "this") String scope) {
        UserEntity u = auth.resolveUser(authorization, token);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = mapper.convertValue(body, Map.class);
        data.entrySet().removeIf(e -> e.getValue() == null);
        return ApiMapper.reminder(reminders.update(u.getId(), reminderId, data, scope), u.getTimezone());
    }

    @DeleteMapping("/{reminderId}")
    public ResponseEntity<Void> delete(
            @PathVariable int reminderId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        UserEntity u = auth.resolveUser(authorization, token);
        reminders.delete(u.getId(), reminderId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{reminderId}/complete")
    public Map<String, Object> complete(
            @PathVariable int reminderId,
            @RequestBody(required = false) Dtos.CompleteIn body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        UserEntity u = auth.resolveUser(authorization, token);
        return ApiMapper.reminder(
                reminders.complete(u.getId(), reminderId, body != null && body.already_done), u.getTimezone());
    }

    @PostMapping("/{reminderId}/snooze")
    public Map<String, Object> snooze(
            @PathVariable int reminderId,
            @RequestBody(required = false) Dtos.SnoozeIn body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        UserEntity u = auth.resolveUser(authorization, token);
        return ApiMapper.reminder(
                reminders.snooze(u.getId(), reminderId, body == null ? null : body.minutes), u.getTimezone());
    }
}
