package com.riseup.agent;

import com.riseup.ai.LLMClient;
import com.riseup.ai.LLMProviderError;
import com.riseup.ai.LLMResponse;
import com.riseup.ai.PromptBuilder;
import com.riseup.ai.ResponseParser;
import com.riseup.alarm.AlarmSchedulerService;
import com.riseup.alarm.StatusEmitter;
import com.riseup.clock.Clock;
import com.riseup.config.RiseUpProperties;
import com.riseup.domain.AlarmEntity;
import com.riseup.domain.EmergencyContactEntity;
import com.riseup.domain.EscalationLogEntity;
import com.riseup.domain.UserEntity;
import com.riseup.domain.UserPreferencesEntity;
import com.riseup.domain.UserProfileEntity;
import com.riseup.repository.AlarmRepository;
import com.riseup.repository.EmergencyContactRepository;
import com.riseup.repository.EscalationLogRepository;
import com.riseup.repository.UserPreferencesRepository;
import com.riseup.repository.UserProfileRepository;
import com.riseup.repository.UserRepository;
import com.riseup.scheduling.JobScheduler;
import com.riseup.time.TimeUtil;
import com.riseup.tools.ToolManager;
import com.riseup.tools.ToolResult;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EscalationEngine {
    public static final List<String> FALLBACK_LADDER =
            List.of("call", "sms", "alexa", "push", "emergency_contact");
    public static final Set<String> ESCALATION_CHANNELS =
            Set.of("call", "sms", "alexa", "push", "emergency_contact");
    public static final Set<String> INTRUSIVE = Set.of("call", "sms", "emergency_contact");

    public record EscalationDecision(
            String method,
            String reasoning,
            Double confidence,
            String fallback,
            String decidedBy,
            boolean fallbackUsed,
            Map<String, Object> inputs) {}

    public static class EscalationContext {
        public AlarmEntity alarm;
        public UserEntity user;
        public UserPreferencesEntity prefs;
        public UserProfileEntity profile;
        public EmergencyContactEntity contact;
        public String trigger;
        public String episode;
        public LocalDateTime now;
        public List<EscalationLogEntity> attempts = new ArrayList<>();
        public List<EscalationLogEntity> history = new ArrayList<>();
        public ToolResult lastResult;

        public List<String> tried() {
            return attempts.stream().map(EscalationLogEntity::getMethod).filter(m -> m != null).toList();
        }
    }

    private final AlarmRepository alarms;
    private final UserRepository users;
    private final UserPreferencesRepository prefsRepo;
    private final UserProfileRepository profiles;
    private final EmergencyContactRepository contacts;
    private final EscalationLogRepository escalationLogs;
    private final ToolManager tools;
    private final LLMClient llm;
    private final Clock clock;
    private final RiseUpProperties settings;
    private final JobScheduler scheduler;
    private final AlarmSchedulerService alarmScheduler;
    private final StatusEmitter emitter;
    private final AgentController agent;
    private final PromptBuilder prompts = new PromptBuilder();
    private final ResponseParser parser = new ResponseParser();

    public EscalationEngine(
            AlarmRepository alarms,
            UserRepository users,
            UserPreferencesRepository prefsRepo,
            UserProfileRepository profiles,
            EmergencyContactRepository contacts,
            EscalationLogRepository escalationLogs,
            ToolManager tools,
            LLMClient llm,
            Clock clock,
            RiseUpProperties settings,
            JobScheduler scheduler,
            @Lazy AlarmSchedulerService alarmScheduler,
            StatusEmitter emitter,
            @Lazy AgentController agent) {
        this.alarms = alarms;
        this.users = users;
        this.prefsRepo = prefsRepo;
        this.profiles = profiles;
        this.contacts = contacts;
        this.escalationLogs = escalationLogs;
        this.tools = tools;
        this.llm = llm;
        this.clock = clock;
        this.settings = settings;
        this.scheduler = scheduler;
        this.alarmScheduler = alarmScheduler;
        this.emitter = emitter;
        this.agent = agent;
    }

    public static String followupJobId(int logId) {
        return "escalation-followup:" + logId;
    }

    public EscalationContext buildContext(int alarmId, String episode, String trigger) {
        AlarmEntity alarm = alarms.findById(alarmId).orElse(null);
        if (alarm == null) return null;
        UserEntity user = users.findById(alarm.getUserId()).orElseThrow();
        EscalationContext ctx = new EscalationContext();
        ctx.alarm = alarm;
        ctx.user = user;
        ctx.prefs = prefsRepo.findById(user.getId()).orElseGet(() -> {
            UserPreferencesEntity p = new UserPreferencesEntity();
            p.setUserId(user.getId());
            return prefsRepo.save(p);
        });
        ctx.profile = profiles.findById(user.getId()).orElseGet(() -> {
            UserProfileEntity p = new UserProfileEntity();
            p.setUserId(user.getId());
            p.setUpdatedAt(clock.now());
            return profiles.save(p);
        });
        ctx.contact =
                contacts.findFirstByUserIdAndConsentStatusOrderByIdAsc(user.getId(), "granted").orElse(null);
        ctx.trigger = trigger;
        ctx.episode = episode;
        ctx.now = clock.now();
        ctx.attempts = new ArrayList<>(escalationLogs.findByEpisodeOrderByAttemptAsc(episode));
        ctx.history = escalationLogs.findByUserIdOrderByCreatedAtDesc(user.getId());
        if (ctx.history.size() > 15) ctx.history = ctx.history.subList(0, 15);
        return ctx;
    }

    public List<String> availableMethods(EscalationContext ctx) {
        Set<String> enabled = Set.copyOf(ctx.prefs.getEnabledChannels() == null ? List.of() : ctx.prefs.getEnabledChannels());
        boolean quiet =
                TimeUtil.inQuietHours(
                        ctx.now, ctx.user.getTimezone(), ctx.prefs.getQuietHoursStart(), ctx.prefs.getQuietHoursEnd());
        List<String> order = new ArrayList<>();
        for (String m : ctx.prefs.getEscalationOrder() == null ? FALLBACK_LADDER : ctx.prefs.getEscalationOrder()) {
            if (ESCALATION_CHANNELS.contains(m) && !order.contains(m)) order.add(m);
        }
        for (String m : FALLBACK_LADDER) if (!order.contains(m)) order.add(m);
        List<String> result = new ArrayList<>();
        for (String method : order) {
            if (!enabled.contains(method) || ctx.tried().contains(method)) continue;
            if (quiet && INTRUSIVE.contains(method)) continue;
            if (!tools.channelReady(method, ctx.user, ctx.contact)) continue;
            result.add(method);
        }
        return result;
    }

    public Map<String, Object> decisionInputs(EscalationContext ctx, List<String> available) {
        LocalDateTime local = TimeUtil.toLocal(ctx.now, ctx.user.getTimezone());
        String lastSuccess = null;
        for (EscalationLogEntity h : ctx.history) {
            if ("confirmed".equals(h.getStatus()) && h.getMethod() != null) {
                lastSuccess = h.getMethod();
                break;
            }
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("user", ctx.user.getName());
        inputs.put("trigger", ctx.trigger);
        inputs.put("alarm", Map.of("label", ctx.alarm.getLabel(), "time", ctx.alarm.getTime()));
        inputs.put("current_time", String.format("%02d:%02d", local.getHour(), local.getMinute()));
        inputs.put("day_of_week", local.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        inputs.put("timezone", ctx.user.getTimezone());
        inputs.put("available_methods", available);
        inputs.put("escalation_order", ctx.prefs.getEscalationOrder());
        inputs.put("escalation_stats", ctx.profile.getEscalationStats());
        inputs.put("last_successful_method", lastSuccess);
        inputs.put(
                "tried_this_episode",
                ctx.attempts.stream()
                        .map(a -> Map.of("method", a.getMethod() == null ? "" : a.getMethod(), "status", a.getStatus(), "error", a.getError() == null ? "" : a.getError()))
                        .toList());
        return inputs;
    }

    public EscalationDecision decideEscalation(EscalationContext ctx, List<String> available) {
        Map<String, Object> inputs = decisionInputs(ctx, available);
        String prompt = prompts.buildEscalationPrompt(inputs);
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            try {
                LLMResponse response = llm.sendRequest(prompt, "escalation", prompts.system);
                try {
                    ResponseParser.DecisionDto dto = parser.parseDecision(response.text(), available);
                    return new EscalationDecision(
                            dto.method(),
                            dto.reasoning(),
                            dto.confidence(),
                            dto.fallback(),
                            response.decidedBy() + ":" + response.model(),
                            false,
                            inputs);
                } catch (ResponseParser.ParseError e) {
                    errors.add(e.getMessage());
                }
            } catch (LLMProviderError e) {
                errors.add("LLM unavailable: " + e.getMessage());
                break;
            }
        }
        return fallbackLadder(ctx, available, String.join("; ", errors), inputs);
    }

    public EscalationDecision fallbackLadder(
            EscalationContext ctx, List<String> available, String reason, Map<String, Object> inputs) {
        String lastSuccess = null;
        for (EscalationLogEntity h : ctx.history) {
            if ("confirmed".equals(h.getStatus())
                    && h.getMethod() != null
                    && available.contains(h.getMethod())) {
                lastSuccess = h.getMethod();
                break;
            }
        }
        List<String> ladder = FALLBACK_LADDER.stream().filter(available::contains).toList();
        String method;
        String why;
        if (lastSuccess != null && !"emergency_contact".equals(lastSuccess)) {
            method = lastSuccess;
            why = "used " + method + " because it worked last time";
        } else {
            method = ladder.get(0);
            why = "followed the default ladder (" + String.join(" > ", ladder) + ")";
        }
        List<String> remaining = ladder.stream().filter(m -> !m.equals(method)).toList();
        return new EscalationDecision(
                method,
                "Rule-based fallback, " + why + ". Reason: " + (reason.isBlank() ? "LLM unavailable" : reason),
                null,
                remaining.isEmpty() ? null : remaining.get(0),
                "fallback",
                true,
                inputs != null ? inputs : decisionInputs(ctx, available));
    }

    @Transactional
    public Map.Entry<EscalationLogEntity, ToolResult> execute(EscalationContext ctx, EscalationDecision decision) {
        ToolResult result = tools.executeEscalation(decision.method(), ctx.user, ctx.prefs, ctx.contact);
        LocalDateTime now = clock.now();
        EscalationLogEntity entry = new EscalationLogEntity();
        entry.setUserId(ctx.user.getId());
        entry.setAlarmId(ctx.alarm.getId());
        entry.setEpisode(ctx.episode);
        entry.setAttempt(ctx.attempts.size() + 1);
        entry.setTriggerEvent(ctx.trigger);
        entry.setMethod(decision.method());
        entry.setDecidedBy(decision.decidedBy());
        entry.setReasoning(decision.reasoning());
        entry.setConfidence(decision.confidence());
        entry.setFallbackMethod(decision.fallback());
        entry.setFallbackUsed(decision.fallbackUsed());
        entry.setInputs(decision.inputs());
        entry.setStatus(result.isSuccess() ? "sent" : "failed");
        entry.setError(result.isSuccess() ? null : result.getDetail());
        entry.setSentAt(result.isSuccess() ? now : null);
        entry.setCreatedAt(now);
        escalationLogs.save(entry);
        if (!result.isSuccess()) {
            ctx.profile.recordEscalationOutcome(decision.method(), false, null);
            profiles.save(ctx.profile);
        }
        ctx.attempts.add(entry);
        ctx.lastResult = result;
        emitter.emit(
                "escalation",
                entry.getId(),
                ctx.user.getId(),
                entry.getStatus(),
                Map.of(
                        "method", entry.getMethod() == null ? "" : entry.getMethod(),
                        "reasoning", entry.getReasoning(),
                        "error", entry.getError() == null ? "" : entry.getError(),
                        "alarm_id", ctx.alarm.getId()));
        return Map.entry(entry, result);
    }

    public void scheduleFollowup(EscalationLogEntity entry) {
        scheduler.schedule(
                followupJobId(entry.getId()),
                clock.now().plusSeconds(settings.getEscalationResponseSeconds()),
                kwargs -> followup(((Number) kwargs.get("log_id")).intValue()),
                Map.of("log_id", entry.getId()));
    }

    @Transactional
    public void followup(int logId) {
        EscalationLogEntity entry = escalationLogs.findById(logId).orElse(null);
        if (entry == null || !"sent".equals(entry.getStatus())) return;
        entry.setStatus("no_response");
        escalationLogs.save(entry);
        UserProfileEntity profile = profiles.findById(entry.getUserId()).orElseThrow();
        profile.recordEscalationOutcome(entry.getMethod() == null ? "" : entry.getMethod(), false, null);
        profiles.save(profile);
        AlarmEntity alarm = entry.getAlarmId() == null ? null : alarms.findById(entry.getAlarmId()).orElse(null);
        if (alarm == null
                || !"escalating".equals(alarm.getStatus())
                || !entry.getEpisode().equals(alarm.getEscalationEpisode())) {
            return;
        }
        agent.run("escalate", Map.of("alarm_id", alarm.getId(), "episode", entry.getEpisode(), "trigger", entry.getTriggerEvent()));
    }

    @Transactional
    public EscalationLogEntity acknowledge(AlarmEntity alarm, String via) {
        if (alarm.getEscalationEpisode() == null) return null;
        List<EscalationLogEntity> attempts =
                escalationLogs.findByEpisodeOrderByAttemptAsc(alarm.getEscalationEpisode());
        List<EscalationLogEntity> pending =
                attempts.stream().filter(a -> "sent".equals(a.getStatus()) || "no_response".equals(a.getStatus())).toList();
        if (pending.isEmpty()) return null;
        EscalationLogEntity entry = pending.get(pending.size() - 1);
        LocalDateTime now = clock.now();
        entry.setStatus("confirmed");
        entry.setRespondedAt(now);
        entry.setResponseTimeSeconds(
                entry.getSentAt() == null ? null : (double) Duration.between(entry.getSentAt(), now).getSeconds());
        Map<String, Object> inputs = new HashMap<>(entry.getInputs() == null ? Map.of() : entry.getInputs());
        inputs.put("acknowledged_via", via);
        entry.setInputs(inputs);
        escalationLogs.save(entry);
        scheduler.cancel(followupJobId(entry.getId()));
        UserProfileEntity profile = profiles.findById(alarm.getUserId()).orElseThrow();
        profile.recordEscalationOutcome(
                entry.getMethod() == null ? "" : entry.getMethod(), true, entry.getResponseTimeSeconds());
        profiles.save(profile);
        emitter.emit(
                "escalation",
                entry.getId(),
                alarm.getUserId(),
                "confirmed",
                Map.of("method", entry.getMethod() == null ? "" : entry.getMethod(), "via", via, "alarm_id", alarm.getId()));
        return entry;
    }

    @Transactional
    public EscalationLogEntity acknowledgeLatest(int userId, String via) {
        List<EscalationLogEntity> recent = escalationLogs.findByUserIdOrderByCreatedAtDesc(userId);
        int n = Math.min(10, recent.size());
        for (int i = 0; i < n; i++) {
            EscalationLogEntity entry = recent.get(i);
            if (("sent".equals(entry.getStatus()) || "no_response".equals(entry.getStatus()))
                    && entry.getAlarmId() != null) {
                AlarmEntity alarm = alarms.findById(entry.getAlarmId()).orElse(null);
                if (alarm != null
                        && "escalating".equals(alarm.getStatus())
                        && entry.getEpisode().equals(alarm.getEscalationEpisode())) {
                    EscalationLogEntity confirmed = acknowledge(alarm, via);
                    alarmScheduler.finish(alarm, "completed");
                    return confirmed;
                }
            }
        }
        return null;
    }

    @Transactional
    public void markUnanswered(EscalationContext ctx, String reason) {
        if (ctx.attempts.isEmpty()) {
            EscalationLogEntity entry = new EscalationLogEntity();
            entry.setUserId(ctx.user.getId());
            entry.setAlarmId(ctx.alarm.getId());
            entry.setEpisode(ctx.episode);
            entry.setAttempt(1);
            entry.setTriggerEvent(ctx.trigger);
            entry.setDecidedBy("system");
            entry.setReasoning(reason);
            entry.setStatus("skipped");
            entry.setFallbackUsed(false);
            entry.setInputs(Map.of("enabled_channels", ctx.prefs.getEnabledChannels()));
            entry.setCreatedAt(clock.now());
            escalationLogs.save(entry);
        }
        alarmScheduler.addLog(ctx.alarm, "missed", Map.of("reason", reason, "attempts", ctx.attempts.size()));
        alarmScheduler.finish(ctx.alarm, "missed");
    }
}
