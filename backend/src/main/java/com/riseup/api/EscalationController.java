package com.riseup.api;

import com.riseup.agent.AgentController;
import com.riseup.agent.EscalationEngine;
import com.riseup.api.dto.Dtos;
import com.riseup.domain.EscalationLogEntity;
import com.riseup.domain.UserEntity;
import com.riseup.domain.UserPreferencesEntity;
import com.riseup.error.NotFoundException;
import com.riseup.history.HistoryService;
import com.riseup.repository.EscalationLogRepository;
import com.riseup.repository.UserPreferencesRepository;
import com.riseup.repository.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class EscalationController {
    private static final Set<String> AWAKE = Set.of("awake", "yes", "y", "1", "up", "ok");

    private final EscalationLogRepository escalationLogs;
    private final EscalationEngine escalationEngine;
    private final HistoryService history;
    private final AuthService auth;
    private final UserRepository users;
    private final UserPreferencesRepository prefsRepo;
    private final AgentController agent;

    public EscalationController(
            EscalationLogRepository escalationLogs,
            EscalationEngine escalationEngine,
            HistoryService history,
            AuthService auth,
            UserRepository users,
            UserPreferencesRepository prefsRepo,
            AgentController agent) {
        this.escalationLogs = escalationLogs;
        this.escalationEngine = escalationEngine;
        this.history = history;
        this.auth = auth;
        this.users = users;
        this.prefsRepo = prefsRepo;
        this.agent = agent;
    }

    @GetMapping("/escalations")
    public List<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token,
            @RequestParam(defaultValue = "20") int limit) {
        int userId = auth.resolveUserId(authorization, token);
        return escalationLogs.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(limit)
                .map(ApiMapper::escalation)
                .toList();
    }

    @GetMapping("/escalations/{logId}")
    public Map<String, Object> explain(
            @PathVariable int logId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        int userId = auth.resolveUserId(authorization, token);
        return history.explain(userId, "escalation", logId);
    }

    @PostMapping("/escalations/acknowledge")
    public Map<String, Object> acknowledge(
            @RequestBody(required = false) Dtos.AcknowledgeIn body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        int userId = auth.resolveUserId(authorization, token);
        EscalationLogEntity entry =
                escalationEngine.acknowledgeLatest(userId, body == null || body.via == null ? "dashboard" : body.via);
        if (entry == null) throw new NotFoundException("No active escalation to acknowledge");
        return Map.of("acknowledged", true, "escalation", ApiMapper.escalation(entry));
    }

    @PostMapping(value = "/webhooks/twilio/sms", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String twilioSms(@RequestParam("From") String from, @RequestParam(value = "Body", defaultValue = "") String body) {
        String text = body.strip().toLowerCase(Locale.ROOT);
        UserEntity user = users.findByPhone(from).orElse(null);
        if (user == null) return "Unknown number.";
        if (text.contains("do not call") || text.contains("stop call")) {
            UserPreferencesEntity prefs = prefsRepo.findById(user.getId()).orElseThrow();
            prefs.setEnabledChannels(prefs.getEnabledChannels().stream().filter(c -> !"call".equals(c)).toList());
            prefsRepo.save(prefs);
            return "OK, RiseUp will not call you for escalations.";
        }
        if (AWAKE.contains(text)) {
            EscalationLogEntity entry = escalationEngine.acknowledgeLatest(user.getId(), "sms_reply");
            return entry != null ? "Great, you're confirmed awake!" : "Nothing to confirm right now.";
        }
        return "Reply AWAKE to confirm you're up.";
    }

    @PostMapping("/webhooks/alexa")
    @SuppressWarnings("unchecked")
    public Map<String, Object> alexa(@RequestBody Map<String, Object> body) {
        Map<String, Object> request = body.get("request") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        String requestType = request.get("type") == null ? null : String.valueOf(request.get("type"));
        if ("LaunchRequest".equals(requestType)) {
            return alexaSpeech("Welcome to RiseUp. Say remind me, then what and when.", false);
        }
        if ("SessionEndedRequest".equals(requestType)) {
            return alexaSpeech("Goodbye.", true);
        }
        String utterance = alexaUtterance(body);
        if (utterance == null || utterance.isBlank()) {
            String speech = "I didn't catch a reminder. Try: remind me to call mom tomorrow at 9 a.m.";
            if (requestType != null) return alexaSpeech(speech, false);
            return Map.of("status", "needs_clarification", "message", speech, "draft", null);
        }
        UserEntity user = users.findFirstByOrderByIdAsc().orElseThrow(() -> new NotFoundException("No user configured for Alexa reminders"));
        var result = agent.run("parse_reminder", Map.of("user_id", user.getId(), "text", utterance));
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) result.getOutput();
        String status = String.valueOf(parsed.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> draft = parsed.get("draft") instanceof Map<?, ?> d ? (Map<String, Object>) d : Map.of();
        String speech;
        if ("ok".equals(status)) {
            speech = "Got it: " + draft.get("title") + " at " + draft.get("due") + ". Open RiseUp Tasks to confirm and save.";
        } else if ("past_time".equals(status)) {
            speech = String.valueOf(parsed.getOrDefault("question", "That time has passed. Should I use tomorrow instead?"));
        } else if ("needs_clarification".equals(status)) {
            speech = String.valueOf(parsed.getOrDefault("question", "When should I remind you?"));
        } else {
            speech = "I couldn't parse that. Try again with a time, or add it in the RiseUp app.";
        }
        if (requestType != null) {
            Map<String, Object> resp = new LinkedHashMap<>(alexaSpeech(speech, "ok".equals(status)));
            resp.put("sessionAttributes", Map.of("riseup_parse", parsed));
            return resp;
        }
        Map<String, Object> out = new LinkedHashMap<>(parsed);
        out.put("speech", speech);
        out.put("source", "alexa");
        return out;
    }

    private static Map<String, Object> alexaSpeech(String speech, boolean end) {
        return Map.of(
                "version",
                "1.0",
                "response",
                Map.of(
                        "outputSpeech", Map.of("type", "PlainText", "text", speech),
                        "shouldEndSession", end));
    }

    @SuppressWarnings("unchecked")
    private static String alexaUtterance(Map<String, Object> body) {
        for (String key : List.of("text", "utterance", "speech")) {
            Object v = body.get(key);
            if (v instanceof String s && !s.isBlank()) return s.strip();
        }
        Object requestObj = body.get("request");
        if (!(requestObj instanceof Map<?, ?> request)) return null;
        if ("LaunchRequest".equals(request.get("type"))) return null;
        Object intentObj = request.get("intent");
        if (!(intentObj instanceof Map<?, ?> intent)) return null;
        Object slotsObj = intent.get("slots");
        if (!(slotsObj instanceof Map<?, ?> slots)) return null;
        for (String slotName : List.of("utterance", "reminder", "text", "query")) {
            Object slot = slots.get(slotName);
            if (slot instanceof Map<?, ?> sm) {
                Object value = sm.get("value");
                if (value instanceof String s && !s.isBlank()) return s.strip();
            }
        }
        return null;
    }
}
