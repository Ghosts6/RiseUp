package com.riseup;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AlarmApiTest extends TestSupport {
    @Test
    void createListAndNextTrigger() throws Exception {
        JsonNode alarm = createAlarm(Map.of("repeat_pattern", "weekdays"));
        assertThat(alarm.get("status").asText()).isEqualTo("scheduled");
        assertThat(alarm.get("next_trigger_local").asText()).isIn("2026-10-19 07:00", "2026-10-20 07:00");
        JsonNode list = getJson("/api/alarms");
        assertThat(list.isArray()).isTrue();
        assertThat(list.size()).isGreaterThan(0);
    }

    @Test
    void validationErrorsNameTheField() throws Exception {
        var badTime =
                mvc.perform(
                                post("/api/alarms")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"time\":\"25:00\"}"))
                        .andExpect(status().isUnprocessableEntity())
                        .andReturn();
        assertThat(json(badTime).get("field").asText()).isEqualTo("time");

        var custom =
                mvc.perform(
                                post("/api/alarms")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"time\":\"07:00\",\"verification_task_type\":\"custom\"}"))
                        .andExpect(status().isUnprocessableEntity())
                        .andReturn();
        assertThat(json(custom).get("field").asText()).isEqualTo("custom_task_text");
    }

    @Test
    void scheduledAlarmRingsAtItsTime() throws Exception {
        JsonNode alarm = createAlarm();
        advanceMinutes(59);
        assertThat(getJson("/api/alarms/" + alarm.get("id").asInt()).get("status").asText()).isEqualTo("scheduled");
        advanceMinutes(1);
        JsonNode challenge = getJson("/api/alarms/" + alarm.get("id").asInt() + "/challenge");
        assertThat(challenge.get("status").asText()).isEqualTo("ringing");
        assertThat(challenge.get("challenge").get("type").asText()).isEqualTo("math");
        assertThat(challenge.get("challenge").has("answer")).isFalse();
    }

    @Test
    void wrongAnswerThenCorrectStartsCheckin() throws Exception {
        JsonNode alarm = createAlarm();
        int id = alarm.get("id").asInt();
        mvc.perform(post("/api/alarms/" + id + "/trigger")).andExpect(status().isOk());
        JsonNode wrong =
                json(
                        mvc.perform(
                                        post("/api/alarms/" + id + "/verify")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(mapper.writeValueAsString(wrongAnswer(id))))
                                .andExpect(status().isOk())
                                .andReturn());
        assertThat(wrong.get("success").asBoolean()).isFalse();
        assertThat(wrong.get("attempts").asInt()).isEqualTo(1);

        JsonNode ok =
                json(
                        mvc.perform(
                                        post("/api/alarms/" + id + "/verify")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(mapper.writeValueAsString(solveChallenge(id))))
                                .andExpect(status().isOk())
                                .andReturn());
        assertThat(ok.get("success").asBoolean()).isTrue();
        assertThat(ok.get("checkin_at").isNull()).isFalse();
        assertThat(getJson("/api/alarms/" + id).get("status").asText()).isIn("awaiting_checkin", "completed", "ringing");

        advanceMinutes(7);
        JsonNode confirmed =
                json(mvc.perform(post("/api/checkins/confirm")).andExpect(status().isOk()).andReturn());
        assertThat(confirmed.get("status").asText()).isEqualTo("confirmed");
        assertThat(getJson("/api/alarms/" + id).get("status").asText()).isEqualTo("completed");
    }

    @Test
    void verifyWhenNotRingingConflicts() throws Exception {
        JsonNode alarm = createAlarm();
        mvc.perform(
                        post("/api/alarms/" + alarm.get("id").asInt() + "/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"answer\":\"1\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void snoozeRules() throws Exception {
        JsonNode noSnooze = createAlarm();
        mvc.perform(post("/api/alarms/" + noSnooze.get("id").asInt() + "/trigger")).andExpect(status().isOk());
        mvc.perform(post("/api/alarms/" + noSnooze.get("id").asInt() + "/snooze")).andExpect(status().isConflict());

        JsonNode snoozy = createAlarm(Map.of("time", "08:00", "snooze_enabled", true));
        mvc.perform(post("/api/alarms/" + snoozy.get("id").asInt() + "/trigger")).andExpect(status().isOk());
        JsonNode snoozed =
                json(mvc.perform(post("/api/alarms/" + snoozy.get("id").asInt() + "/snooze")).andExpect(status().isOk()).andReturn());
        assertThat(snoozed.get("status").asText()).isEqualTo("snoozed");
        advanceMinutes(10);
        assertThat(getJson("/api/alarms/" + snoozy.get("id").asInt()).get("status").asText()).isEqualTo("ringing");
    }

    @Test
    void toggleAndDelete() throws Exception {
        JsonNode alarm = createAlarm();
        int id = alarm.get("id").asInt();
        JsonNode off =
                json(
                        mvc.perform(
                                        post("/api/alarms/" + id + "/toggle")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"active\":false}"))
                                .andExpect(status().isOk())
                                .andReturn());
        assertThat(off.get("is_active").asBoolean()).isFalse();
        assertThat(off.get("status").asText()).isEqualTo("disabled");
        advanceMinutes(120);
        assertThat(getJson("/api/alarms/" + id).get("status").asText()).isEqualTo("disabled");
        mvc.perform(delete("/api/alarms/" + id)).andExpect(status().isNoContent());
        mvc.perform(MockMvcRequestBuilders.get("/api/alarms/" + id)).andExpect(status().isNotFound());
    }
}
