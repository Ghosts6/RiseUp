package com.riseup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class HealthAndSystemTest extends TestSupport {
    @Test
    void healthReportsOkWithMockIntegrations() throws Exception {
        JsonNode health = getJson("/api/health");
        assertThat(health.get("status").asText()).isEqualTo("ok");
        assertThat(health.get("integrations").get("twilio").asText()).isEqualTo("mock");
        assertThat(health.get("llm").get("escalation").asText()).contains("mock");
        assertThat(health.get("services").get("scheduler").get("status").asText()).isEqualTo("test");
    }

    @Test
    void meReturnsDemoUser() throws Exception {
        JsonNode me = getJson("/api/me");
        assertThat(me.get("name").asText()).isEqualTo("Demo User");
        assertThat(me.get("timezone").asText()).isEqualTo("America/Toronto");
    }

    @Test
    void notificationsMarkRead() throws Exception {
        JsonNode alarm = createAlarm();
        mvc.perform(post("/api/alarms/" + alarm.get("id").asInt() + "/trigger")).andExpect(status().isOk());
        JsonNode notes = getJson("/api/notifications");
        assertThat(notes.isArray()).isTrue();
        assertThat(notes.size()).isGreaterThan(0);
        JsonNode marked =
                json(mvc.perform(post("/api/notifications/read")).andExpect(status().isOk()).andReturn());
        assertThat(marked.get("marked").asInt()).isGreaterThan(0);
    }
}
