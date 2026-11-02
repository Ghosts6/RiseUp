package com.riseup.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MockTools {
    private static final Logger log = LoggerFactory.getLogger(MockTools.class);
    private static final AtomicLong IDS = new AtomicLong(1);

    private MockTools() {}

    abstract static class MockTool implements Tool {
        protected final Set<String> failures;
        public final List<Map<String, Object>> sent = new ArrayList<>();

        MockTool(Set<String> failures) {
            this.failures = failures == null ? Set.of() : failures;
        }

        @Override
        public boolean isMock() {
            return true;
        }

        protected ToolResult deliver(String channel, Map<String, Object> payload) {
            Map<String, Object> record = new java.util.LinkedHashMap<>(payload);
            record.put("channel", channel);
            sent.add(record);
            if (failures.contains(channel)) {
                log.info("[mock {}] simulated failure: {}", channel, payload);
                return new ToolResult(false, channel, "simulated " + channel + " failure");
            }
            log.info("[mock {}] {}", channel, payload);
            return new ToolResult(true, channel, "mock " + channel + " delivered", "mock-" + IDS.getAndIncrement());
        }
    }

    public static class MockTwilioClient extends MockTool {
        public MockTwilioClient(Set<String> failures) {
            super(failures);
        }

        @Override
        public String getName() {
            return "twilio";
        }

        @Override
        public ToolResult execute(Map<String, Object> payload) {
            if ("call".equals(payload.get("action"))) {
                return deliver("call", Map.of("to", payload.get("to"), "message", payload.getOrDefault("message", "")));
            }
            return deliver("sms", Map.of("to", payload.get("to"), "message", payload.getOrDefault("message", "")));
        }
    }

    public static class MockAlexaClient extends MockTool {
        public MockAlexaClient(Set<String> failures) {
            super(failures);
        }

        @Override
        public String getName() {
            return "alexa";
        }

        @Override
        public ToolResult execute(Map<String, Object> payload) {
            return deliver("alexa", Map.of("message", payload.getOrDefault("message", "")));
        }
    }

    public static class MockFirebaseClient extends MockTool {
        public MockFirebaseClient(Set<String> failures) {
            super(failures);
        }

        @Override
        public String getName() {
            return "firebase";
        }

        @Override
        public ToolResult execute(Map<String, Object> payload) {
            return deliver(
                    "push",
                    Map.of(
                            "token", payload.get("token"),
                            "title", payload.getOrDefault("title", ""),
                            "body", payload.getOrDefault("body", "")));
        }
    }
}
