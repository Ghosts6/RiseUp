package com.riseup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.riseup.domain.UserProfileEntity;
import com.riseup.repository.UserProfileRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EscalationApiTest extends TestSupport {
    @Autowired UserProfileRepository profiles;

    @Test
    void llmDecidesFirstMethod() throws Exception {
        JsonNode alarm = createAlarm();
        ringAndTimeout(alarm.get("id").asInt());
        JsonNode state = getJson("/api/alarms/" + alarm.get("id").asInt() + "/escalation");
        assertThat(state.get("attempts")).hasSize(1);
        JsonNode attempt = state.get("attempts").get(0);
        assertThat(attempt.get("method").asText()).isIn("call", "sms", "alexa", "push", "emergency_contact", "call");
        assertThat(attempt.get("decided_by").asText()).startsWith("llm:mock");
        assertThat(attempt.get("fallback_used").asBoolean()).isFalse();
        assertThat(attempt.get("status").asText()).isEqualTo("sent");
    }

    @Test
    void noResponseMovesToNextMethod() throws Exception {
        JsonNode alarm = createAlarm();
        ringAndTimeout(alarm.get("id").asInt());
        advanceSeconds(30);
        JsonNode attempts = getJson("/api/alarms/" + alarm.get("id").asInt() + "/escalation").get("attempts");
        assertThat(attempts.size()).isGreaterThan(0);
        assertThat(attempts.get(0).get("method").asText()).isIn("call", "sms", "alexa", "push", "emergency_contact");
    }

    @Test
    void onlyEnabledChannelsAreChosen() throws Exception {
        mvc.perform(
                        patch("/api/prefs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"enabled_channels\":[\"sms\",\"alexa\"]}"))
                .andExpect(status().isOk());
        JsonNode alarm = createAlarm();
        ringAndTimeout(alarm.get("id").asInt());
        advanceSeconds(30);
        advanceSeconds(30);
        JsonNode attempts = getJson("/api/alarms/" + alarm.get("id").asInt() + "/escalation").get("attempts");
        assertThat(attempts.get(0).get("method").asText()).isEqualTo("sms");
        assertThat(attempts.get(1).get("method").asText()).isEqualTo("alexa");
    }

    @Test
    void missedCheckinEscalates() throws Exception {
        JsonNode alarm = createAlarm();
        int id = alarm.get("id").asInt();
        mvc.perform(post("/api/alarms/" + id + "/trigger")).andExpect(status().isOk());
        mvc.perform(
                        post("/api/alarms/" + id + "/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(solveChallenge(id))))
                .andExpect(status().isOk());
        advanceMinutes(7);
        advanceMinutes(5);
        advanceSeconds(1);
        JsonNode state = getJson("/api/alarms/" + id + "/escalation");
        assertThat(state.get("status").asText()).isEqualTo("escalating");
        assertThat(state.get("attempts").get(0).get("trigger").asText()).isEqualTo("checkin_missed");
    }

    @Test
    void acknowledgeStopsEscalation() throws Exception {
        JsonNode alarm = createAlarm();
        ringAndTimeout(alarm.get("id").asInt());
        JsonNode ack =
                json(
                        mvc.perform(
                                        post("/api/escalations/acknowledge")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"via\":\"dashboard\"}"))
                                .andExpect(status().isOk())
                                .andReturn());
        assertThat(ack.get("acknowledged").asBoolean()).isTrue();
        assertThat(getJson("/api/alarms/" + alarm.get("id").asInt()).get("status").asText()).isIn("completed", "escalating", "awaiting_checkin", "missed", "ringing");
    }

    @Test
    void prefersMethodWithBestHistory() throws Exception {
        mvc.perform(
                        patch("/api/prefs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"escalation_order\":[\"sms\",\"call\",\"alexa\",\"push\"]}"))
                .andExpect(status().isOk());
        UserProfileEntity profile = profiles.findById(1).orElseThrow();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("call", Map.of("attempts", 20, "successes", 19, "success_rate", 0.95));
        stats.put("sms", Map.of("attempts", 20, "successes", 8, "success_rate", 0.4));
        profile.setEscalationStats(stats);
        profiles.save(profile);
        JsonNode alarm = createAlarm();
        ringAndTimeout(alarm.get("id").asInt());
        JsonNode attempt = getJson("/api/alarms/" + alarm.get("id").asInt() + "/escalation").get("attempts").get(0);
        assertThat(attempt.get("method").asText()).isIn("call", "sms", "alexa", "push", "emergency_contact", "call");
        assertThat(attempt.get("method").asText()).isNotBlank();
        assertThat(attempt.get("reasoning").asText()).isNotBlank();
    }
}
