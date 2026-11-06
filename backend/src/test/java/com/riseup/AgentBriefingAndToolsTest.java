package com.riseup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AgentBriefingAndToolsTest extends TestSupport {
    @Test
    void briefingAndRemindIntentProposeAction() throws Exception {
        JsonNode briefing = json(mvc.perform(get("/api/agent/briefing")).andExpect(status().isOk()).andReturn());
        assertThat(briefing.path("summary").asText()).isNotBlank();
        assertThat(briefing.path("alarms").isArray()).isTrue();
        assertThat(briefing.path("reminders").isArray()).isTrue();

        JsonNode chat =
                json(
                        mvc.perform(
                                        post("/api/agent/chat")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        mapper.writeValueAsString(
                                                                Map.of(
                                                                        "message",
                                                                        "remind me to drink water tomorrow at 9am",
                                                                        "history",
                                                                        java.util.List.of()))))
                                .andExpect(status().isOk())
                                .andReturn());
        assertThat(chat.path("reply").asText()).isNotBlank();
        if (chat.has("proposed_action") && !chat.path("proposed_action").isMissingNode()) {
            assertThat(chat.path("proposed_action").path("tool").asText()).isEqualTo("create_reminder");
            JsonNode args = chat.path("proposed_action").path("args");
            JsonNode executed =
                    json(
                            mvc.perform(
                                            post("/api/agent/tools/execute")
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .content(
                                                            mapper.writeValueAsString(
                                                                    Map.of(
                                                                            "tool",
                                                                            "create_reminder",
                                                                            "args",
                                                                            mapper.convertValue(
                                                                                    args, Map.class)))))
                                    .andExpect(status().isOk())
                                    .andReturn());
            assertThat(executed.path("ok").asBoolean()).isTrue();
            assertThat(executed.path("reminder").path("id").asInt()).isPositive();
        }
    }

    @Test
    void todayIntentReturnsBriefingPayload() throws Exception {
        JsonNode chat =
                json(
                        mvc.perform(
                                        post("/api/agent/chat")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"message\":\"What's on today?\",\"history\":[]}"))
                                .andExpect(status().isOk())
                                .andReturn());
        assertThat(chat.path("briefing").isObject()).isTrue();
        assertThat(chat.path("model").asText()).isEqualTo("briefing");
    }
}
