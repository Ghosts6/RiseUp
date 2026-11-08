package com.riseup.health;

import com.riseup.ai.LLMClient;
import com.riseup.ai.LLMProvider;
import com.riseup.config.RiseUpProperties;
import com.riseup.scheduling.JobScheduler;
import com.riseup.scheduling.ManualJobScheduler;
import com.riseup.scheduling.SpringJobScheduler;
import com.riseup.tools.ToolManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    private final RiseUpProperties settings;
    private final ToolManager tools;
    private final LLMClient llm;
    private final JobScheduler scheduler;
    private final String datasourceUrl;

    public HealthService(
            RiseUpProperties settings,
            ToolManager tools,
            LLMClient llm,
            JobScheduler scheduler,
            @Value("${spring.datasource.url}") String datasourceUrl) {
        this.settings = settings;
        this.tools = tools;
        this.llm = llm;
        this.scheduler = scheduler;
        this.datasourceUrl = datasourceUrl;
    }

    private static Map<String, Object> row(String status, String label, String detail, Map<String, Object> extra) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("label", label);
        m.put("detail", detail);
        if (extra != null) m.putAll(extra);
        return m;
    }

    public Map<String, Map<String, Object>> toolStatus() {
        Map<String, String> mode = tools.mode();
        List<String> twilioMissing = new ArrayList<>();
        if (RiseUpProperties.configured(settings.getTwilioAccountSid()) == null) twilioMissing.add("TWILIO_ACCOUNT_SID");
        if (RiseUpProperties.configured(settings.getTwilioAuthToken()) == null) twilioMissing.add("TWILIO_AUTH_TOKEN");
        if (RiseUpProperties.configured(settings.getTwilioPhoneNumber()) == null) twilioMissing.add("TWILIO_PHONE_NUMBER");
        boolean alexaOk = RiseUpProperties.configured(settings.getAlexaApiKey()) != null;
        boolean firebaseOk = RiseUpProperties.configured(settings.getFirebaseApiKey()) != null;
        boolean smtpOk = RiseUpProperties.configured(settings.getSmtpHost()) != null;

        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        out.put("twilio", fromMode("twilio", mode, twilioMissing.isEmpty(), twilioMissing));
        out.put("alexa", fromMode("alexa", mode, alexaOk, List.of()));
        out.put("firebase", fromMode("firebase", mode, firebaseOk, List.of()));
        out.put("email", fromMode("email", mode, smtpOk, List.of()));
        return out;
    }

    private Map<String, Object> fromMode(String name, Map<String, String> mode, boolean configured, List<String> missing) {
        boolean live = "live".equals(mode.get(name));
        if (live) {
            return row("live", "Live", "Configured and active", Map.of("mode", "live", "configured", true));
        }
        if (!missing.isEmpty()) {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("mode", "mock");
            extra.put("configured", false);
            extra.put("missing", missing);
            return row("not_configured", "Not configured", "Missing " + String.join(", ", missing) + " using mock", extra);
        }
        if (!configured) {
            return row("not_configured", "Not configured", "No API key, using mock", Map.of("mode", "mock", "configured", false));
        }
        return row("mock", "Mock", "Using offline mock", Map.of("mode", "mock", "configured", false));
    }

    public Map<String, Map<String, Object>> llmStatus() {
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : llm.getRouting().entrySet()) {
            String task = e.getKey();
            String requested = e.getValue();
            LLMProvider provider = llm.providerFor(task);
            String active = provider.getName();
            if ("mock".equals(active) && !"mock".equals(requested)) {
                rows.put(
                        task,
                        row(
                                "fallback_mock",
                                "Mock (fallback)",
                                "Requested " + requested + " but its key is missing",
                                Map.of(
                                        "provider",
                                        active,
                                        "model",
                                        provider.getModel(),
                                        "requested",
                                        requested,
                                        "configured",
                                        false)));
            } else if ("mock".equals(active)) {
                rows.put(
                        task,
                        row(
                                "mock",
                                "Mock",
                                "Offline mock provider",
                                Map.of(
                                        "provider",
                                        active,
                                        "model",
                                        provider.getModel(),
                                        "requested",
                                        requested,
                                        "configured",
                                        false)));
            } else {
                rows.put(
                        task,
                        row(
                                "live",
                                "Live",
                                "Using " + provider.label(),
                                Map.of(
                                        "provider",
                                        active,
                                        "model",
                                        provider.getModel(),
                                        "requested",
                                        requested,
                                        "configured",
                                        true)));
            }
        }
        return rows;
    }

    public Map<String, Object> payload() {
        Map<String, Map<String, Object>> toolsDetail = toolStatus();
        Map<String, Map<String, Object>> llmDetail = llmStatus();
        Map<String, String> integrations = new LinkedHashMap<>();
        toolsDetail.forEach((k, v) -> integrations.put(k, String.valueOf(v.get("mode"))));
        Map<String, String> llmLabels = new LinkedHashMap<>();
        llmDetail.forEach(
                (k, v) ->
                        llmLabels.put(
                                k,
                                v.get("provider") + ":" + v.get("model")));

        Map<String, Object> services = new LinkedHashMap<>();
        services.put(
                "api",
                row("running", "Running", settings.getEnvironment() + " · v" + settings.getVersion(), null));
        if (datasourceUrl != null && datasourceUrl.contains("postgres")) {
            services.put("database", row("live", "PostgreSQL", "Connected", null));
        } else if (datasourceUrl != null && datasourceUrl.contains("h2")) {
            services.put("database", row("local", "H2", "Local database", null));
        } else {
            services.put("database", row("live", "Database", "Connected", null));
        }
        services.put("cache", row("local", "In-memory", "Process-local cache", Map.of("backend", "memory")));
        if (scheduler instanceof ManualJobScheduler) {
            services.put("scheduler", row("test", "Manual", "Test/manual scheduler", null));
        } else if (scheduler instanceof SpringJobScheduler) {
            services.put(
                    "scheduler",
                    settings.isEnableScheduler()
                            ? row("running", "Running", "Spring scheduler active", null)
                            : row("disabled", "Disabled", "ENABLE_SCHEDULER=false", null));
        } else {
            services.put("scheduler", row("unknown", "Unknown", scheduler.getClass().getSimpleName(), null));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("version", settings.getVersion());
        out.put("environment", settings.getEnvironment());
        out.put("integrations", integrations);
        out.put("llm", llmLabels);
        out.put("services", services);
        out.put("integrations_detail", toolsDetail);
        out.put("llm_detail", llmDetail);
        return out;
    }
}
