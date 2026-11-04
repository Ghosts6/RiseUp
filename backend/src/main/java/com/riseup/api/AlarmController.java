package com.riseup.api;

import com.riseup.alarm.AlarmControllerService;
import com.riseup.alarm.CheckInService;
import com.riseup.api.dto.Dtos;
import com.riseup.domain.AlarmEntity;
import com.riseup.domain.CheckInEntity;
import com.riseup.domain.EscalationLogEntity;
import com.riseup.domain.UserEntity;
import com.riseup.verification.VerificationResponse;
import java.util.HashMap;
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
public class AlarmController {
    private final AlarmControllerService alarms;
    private final CheckInService checkins;
    private final AuthService auth;

    public AlarmController(AlarmControllerService alarms, CheckInService checkins, AuthService auth) {
        this.alarms = alarms;
        this.checkins = checkins;
        this.auth = auth;
    }

    private UserEntity user(String authorization, String token) {
        return auth.resolveUser(authorization, token);
    }

    @GetMapping("/api/alarms")
    public List<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        UserEntity u = user(authorization, token);
        return alarms.listAlarms(u.getId()).stream().map(a -> ApiMapper.alarm(a, u.getTimezone())).toList();
    }

    @PostMapping("/api/alarms")
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Dtos.AlarmIn body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        UserEntity u = user(authorization, token);
        AlarmEntity alarm = alarms.createAlarm(u.getId(), toMap(body));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiMapper.alarm(alarm, u.getTimezone()));
    }

    @GetMapping("/api/alarms/{alarmId}")
    public Map<String, Object> get(
            @PathVariable int alarmId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        UserEntity u = user(authorization, token);
        return ApiMapper.alarm(alarms.get(u.getId(), alarmId), u.getTimezone());
    }

    @PatchMapping("/api/alarms/{alarmId}")
    public Map<String, Object> edit(
            @PathVariable int alarmId,
            @RequestBody Dtos.AlarmIn body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        UserEntity u = user(authorization, token);
        return ApiMapper.alarm(alarms.editAlarm(u.getId(), alarmId, toMap(body)), u.getTimezone());
    }

    @DeleteMapping("/api/alarms/{alarmId}")
    public ResponseEntity<Void> delete(
            @PathVariable int alarmId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        UserEntity u = user(authorization, token);
        alarms.delete(u.getId(), alarmId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/alarms/{alarmId}/toggle")
    public Map<String, Object> toggle(
            @PathVariable int alarmId,
            @RequestBody(required = false) Dtos.ToggleIn body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        UserEntity u = user(authorization, token);
        return ApiMapper.alarm(alarms.toggle(u.getId(), alarmId, body == null ? null : body.active), u.getTimezone());
    }

    @PostMapping("/api/alarms/{alarmId}/trigger")
    public Map<String, Object> trigger(
            @PathVariable int alarmId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        UserEntity u = user(authorization, token);
        return ApiMapper.alarm(alarms.triggerNow(u.getId(), alarmId), u.getTimezone());
    }

    @GetMapping("/api/alarms/{alarmId}/challenge")
    public Map<String, Object> challenge(
            @PathVariable int alarmId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        UserEntity u = user(authorization, token);
        return alarms.getChallenge(u.getId(), alarmId);
    }

    @PostMapping("/api/alarms/{alarmId}/verify")
    public Map<String, Object> verify(
            @PathVariable int alarmId,
            @RequestBody Dtos.VerifyIn body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        UserEntity u = user(authorization, token);
        VerificationResponse resp = new VerificationResponse();
        resp.setAnswer(body.answer);
        resp.setCompleted(body.completed);
        resp.setImageBase64(body.image_base64);
        resp.setCameraError(body.camera_error);
        return alarms.verify(u.getId(), alarmId, resp);
    }

    @PostMapping("/api/alarms/{alarmId}/snooze")
    public Map<String, Object> snooze(
            @PathVariable int alarmId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        UserEntity u = user(authorization, token);
        return ApiMapper.alarm(alarms.snooze(u.getId(), alarmId), u.getTimezone());
    }

    @GetMapping("/api/alarms/{alarmId}/escalation")
    public Map<String, Object> escalation(
            @PathVariable int alarmId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        UserEntity u = user(authorization, token);
        Map<String, Object> status = alarms.escalationStatus(u.getId(), alarmId);
        @SuppressWarnings("unchecked")
        List<EscalationLogEntity> attempts = (List<EscalationLogEntity>) status.get("attempts");
        Map<String, Object> out = new LinkedHashMap<>(status);
        out.put("attempts", attempts.stream().map(ApiMapper::escalation).toList());
        return out;
    }

    @GetMapping("/api/checkins")
    public List<Map<String, Object>> listCheckins(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        UserEntity u = user(authorization, token);
        return checkins.listRecent(u.getId()).stream().map(ApiMapper::checkin).toList();
    }

    @PostMapping("/api/checkins/confirm")
    public Map<String, Object> confirmCheckin(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        UserEntity u = user(authorization, token);
        CheckInEntity checkin = checkins.recordResponse(u.getId());
        Map<String, Object> out = new LinkedHashMap<>(ApiMapper.checkin(checkin));
        out.put(
                "message",
                "late".equals(checkin.getStatus())
                        ? "Check-in confirmed late; escalation stopped."
                        : "Check-in confirmed. Have a great day!");
        return out;
    }

    private static Map<String, Object> toMap(Dtos.AlarmIn body) {
        Map<String, Object> m = new HashMap<>();
        if (body.time != null) m.put("time", body.time);
        if (body.label != null) m.put("label", body.label);
        if (body.sound != null) m.put("sound", body.sound);
        if (body.vibration != null) m.put("vibration", body.vibration);
        if (body.repeat_pattern != null) m.put("repeat_pattern", body.repeat_pattern);
        if (body.snooze_enabled != null) m.put("snooze_enabled", body.snooze_enabled);
        if (body.verification_task_type != null) m.put("verification_task_type", body.verification_task_type);
        if (body.custom_task_text != null) m.put("custom_task_text", body.custom_task_text);
        if (body.priority != null) m.put("priority", body.priority);
        if (body.is_active != null) m.put("is_active", body.is_active);
        return m;
    }
}
