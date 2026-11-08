package com.riseup.agent;

import com.riseup.alarm.AgentKick;
import com.riseup.clock.Clock;
import com.riseup.domain.EscalationLogEntity;
import com.riseup.domain.UserEntity;
import com.riseup.insights.RecommendationService;
import com.riseup.reminder.NlParser;
import com.riseup.repository.UserRepository;
import com.riseup.tools.ToolResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentController implements AgentKick {
    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final EscalationEngine escalationEngine;
    private final NlParser nlParser;
    private final RecommendationService recommendations;
    private final UserRepository users;
    private final Clock clock;

    public AgentController(
            EscalationEngine escalationEngine,
            NlParser nlParser,
            RecommendationService recommendations,
            UserRepository users,
            Clock clock) {
        this.escalationEngine = escalationEngine;
        this.nlParser = nlParser;
        this.recommendations = recommendations;
        this.users = users;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void runEscalation(int alarmId, String episode, String trigger) {
        run("escalate", Map.of("alarm_id", alarmId, "episode", episode, "trigger", trigger));
    }

    @Transactional
    public AgentResult run(String goal, Map<String, Object> context) {
        return switch (goal) {
            case "escalate" -> escalate(context);
            case "parse_reminder" -> parseReminder(context);
            case "recommend" -> recommend(context);
            default -> throw new IllegalArgumentException("Unknown agent goal '" + goal + "'");
        };
    }

    private AgentResult escalate(Map<String, Object> context) {
        AgentResult result = new AgentResult("escalate", "started");
        int alarmId = ((Number) context.get("alarm_id")).intValue();
        String episode = String.valueOf(context.get("episode"));
        String trigger = String.valueOf(context.get("trigger"));
        EscalationEngine.EscalationContext ctx = escalationEngine.buildContext(alarmId, episode, trigger);
        if (ctx == null
                || !"escalating".equals(ctx.alarm.getStatus())
                || !episode.equals(ctx.alarm.getEscalationEpisode())) {
            result.setStatus("stale");
            return result;
        }
        for (int i = 0; i < EscalationEngine.ESCALATION_CHANNELS.size(); i++) {
            List<String> available = escalationEngine.availableMethods(ctx);
            result.getSteps().add(Map.of("observe", Map.of("available", available, "tried", ctx.tried())));
            if (available.isEmpty()) {
                String reason =
                        ctx.attempts.isEmpty()
                                ? "All escalation methods are disabled or unavailable"
                                : "Every available method was tried without a response";
                escalationEngine.markUnanswered(ctx, reason);
                result.setStatus("unanswered");
                return result;
            }
            EscalationEngine.EscalationDecision decision = escalationEngine.decideEscalation(ctx, available);
            Map<String, Object> decide = new LinkedHashMap<>();
            decide.put("method", decision.method());
            decide.put("decided_by", decision.decidedBy());
            decide.put("fallback_used", decision.fallbackUsed());
            result.getSteps().add(Map.of("decide", decide));
            Map.Entry<EscalationLogEntity, ToolResult> acted = escalationEngine.execute(ctx, decision);
            EscalationLogEntity entry = acted.getKey();
            ToolResult toolResult = acted.getValue();
            result.getSteps()
                    .add(
                            Map.of(
                                    "act",
                                    Map.of(
                                            "log_id",
                                            entry.getId(),
                                            "success",
                                            toolResult.isSuccess(),
                                            "detail",
                                            toolResult.getDetail() == null ? "" : toolResult.getDetail())));
            if (toolResult.isSuccess()) {
                escalationEngine.scheduleFollowup(entry);
                result.setStatus("awaiting_response");
                result.setOutput(entry);
                return result;
            }
            log.info("Escalation via {} failed ({}); re-deciding", decision.method(), toolResult.getDetail());
        }
        escalationEngine.markUnanswered(ctx, "Escalation step limit reached");
        result.setStatus("unanswered");
        return result;
    }

    private AgentResult parseReminder(Map<String, Object> context) {
        UserEntity user = users.findById(((Number) context.get("user_id")).intValue()).orElseThrow();
        Map<String, Object> parsed = nlParser.parse(String.valueOf(context.get("text")), user, clock.now());
        AgentResult result = new AgentResult("parse_reminder", String.valueOf(parsed.get("status")));
        result.setOutput(parsed);
        return result;
    }

    private AgentResult recommend(Map<String, Object> context) {
        Map<String, Object> output = recommendations.generate(((Number) context.get("user_id")).intValue());
        AgentResult result = new AgentResult("recommend", "done");
        result.setOutput(output);
        return result;
    }
}
